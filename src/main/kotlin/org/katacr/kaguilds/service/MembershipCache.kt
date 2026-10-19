package org.katacr.kaguilds.service

import org.katacr.kaguilds.DatabaseManager
import org.katacr.kaguilds.KaGuilds
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 公会成员身份缓存。作为全插件判断"玩家属于哪个公会"的唯一入口，
 * 替代原先分散且非线程安全的 [KaGuilds.playerGuildCache] 直接读取。
 *
 * 设计目标：让单服与跨服走同一判断路径，永不因"缓存未加载"而误判。
 * - 线程安全：内部使用 ConcurrentHashMap。
 * - TTL：缓存条目带过期时间，过期后触发后台刷新，避免脏数据长期存活。
 * - 查库兜底：本地无缓存或过期时返回数据库最新值（调用方多在异步线程，可直接查）。
 */
class MembershipCache(private val plugin: KaGuilds) {

    private val db: DatabaseManager get() = plugin.dbManager

    /** UUID -> (公会ID, 过期时间戳)。-1 表示明确无公会（也缓存，避免反复查库）。 */
    private val cache = ConcurrentHashMap<UUID, CachedEntry>()

    /** 无公会的缓存有效期（较短，便于退会后快速感知）。 */
    private val ttlNoGuildMs = 10_000L

    /** 有公会的缓存有效期。 */
    private val ttlHasGuildMs = 30_000L

    private data class CachedEntry(val guildId: Int?, val expireAt: Long)

    /** 查询结果：区分"确无公会(null)"与"查询失败"。 */
    private sealed class QueryResult {
        data class Ok(val guildId: Int?) : QueryResult()
        object Failed : QueryResult()
    }

    /**
     * 获取玩家所属公会 ID。
     * @param player 用于主线程调用时回退到"返回旧值 + 后台刷新"，避免阻塞主线程。
     * @param allowStaleOnMainThread 当在主线程且缓存过期时，是否允许返回旧值并后台刷新（true 不卡主线程）。
     *                                默认 true；若调用方在主线程且必须拿到最新值，应改为 false 并接受短暂查询。
     */
    fun getGuildId(player: org.bukkit.entity.Player, allowStaleOnMainThread: Boolean = true): Int? {
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        val existing = cache[uuid]

        if (existing != null && existing.expireAt > now) {
            return existing.guildId
        }

        // 缓存缺失或过期：判断调用线程
        val isMainThread = plugin.server.isPrimaryThread
        if (isMainThread && allowStaleOnMainThread) {
            // 主线程：返回旧值（可能为 null）并异步刷新，避免卡顿
            if (existing != null) {
                refreshAsync(uuid)
                return existing.guildId
            }
            // 完全没有缓存：主线程同步查一次（多数命令/菜单场景可接受一次短查询）
            return when (val fresh = queryDb(uuid)) {
                is QueryResult.Ok -> {
                    put(uuid, fresh.guildId)
                    fresh.guildId
                }
                // 查询失败：不缓存（避免把失败当"无公会"），返回 null
                QueryResult.Failed -> null
            }
        }

        // 异步线程或主线程要求最新：同步查库
        return when (val fresh = queryDb(uuid)) {
            is QueryResult.Ok -> {
                put(uuid, fresh.guildId)
                fresh.guildId
            }
            QueryResult.Failed -> null
        }
    }

    /**
     * UUID 版本，供非 Player 上下文（如离线 UUID）使用。
     */
    fun getGuildId(uuid: UUID, allowStaleOnMainThread: Boolean = true): Int? {
        val now = System.currentTimeMillis()
        val existing = cache[uuid]
        if (existing != null && existing.expireAt > now) {
            return existing.guildId
        }
        val isMainThread = plugin.server.isPrimaryThread
        if (isMainThread && allowStaleOnMainThread) {
            if (existing != null) {
                refreshAsync(uuid)
                return existing.guildId
            }
            return when (val fresh = queryDb(uuid)) {
                is QueryResult.Ok -> {
                    put(uuid, fresh.guildId)
                    fresh.guildId
                }
                QueryResult.Failed -> null
            }
        }
        return when (val fresh = queryDb(uuid)) {
            is QueryResult.Ok -> {
                put(uuid, fresh.guildId)
                fresh.guildId
            }
            QueryResult.Failed -> null
        }
    }

    /**
     * 写入/更新玩家公会身份（本地成员变更时立即调用）。
     * @param guildId 公会 ID；null 或 -1 表示无公会。
     */
    fun set(uuid: UUID, guildId: Int?) {
        val id = if (guildId != null && guildId >= 0) guildId else null
        put(uuid, id)
    }

    /**
     * 使缓存失效（成员变更、退会、被踢、转让等场景调用）。
     * 同时广播让其它服也失效（跨服同步）。
     */
    fun invalidate(uuid: UUID) {
        cache.remove(uuid)
        if (plugin.isProxyEnabled()) {
            plugin.guildService.syncPlayerCache(uuid, -1)
        }
    }

    /**
     * 批量使某公会所有缓存失效（公会解散/删除场景）。
     */
    fun invalidateGuild(guildId: Int) {
        cache.entries.removeIf { it.value.guildId == guildId }
    }

    /**
     * 仅更新本地缓存，不广播（供 PluginMessageListener 处理来自其它服的同步包，
     * 避免回声：本服收到代理转发的包后不应再广播给其它服）。
     */
    fun updateLocal(uuid: UUID, guildId: Int?) {
        set(uuid, guildId)
    }

    /**
     * 仅移除本地缓存，不广播（语义同上）。
     */
    fun removeLocal(uuid: UUID) {
        cache.remove(uuid)
    }

    /**
     * 仅本地批量清除某公会的成员缓存，不广播（接收端用）。
     */
    fun invalidateGuildLocal(guildId: Int) {
        cache.entries.removeIf { it.value.guildId == guildId }
    }

    /**
     * 玩家上线时加载缓存（由 GuildListener 调用），并触发任务激活。
     */
    fun loadOnJoin(player: org.bukkit.entity.Player): Int? {
        return when (val fresh = queryDb(player.uniqueId)) {
            is QueryResult.Ok -> {
                put(player.uniqueId, fresh.guildId)
                fresh.guildId
            }
            QueryResult.Failed -> null
        }
    }

    private fun put(uuid: UUID, guildId: Int?) {
        val ttl = if (guildId != null) ttlHasGuildMs else ttlNoGuildMs
        cache[uuid] = CachedEntry(guildId, System.currentTimeMillis() + ttl)
    }

    private fun queryDb(uuid: UUID): QueryResult {
        return try {
            QueryResult.Ok(db.memberRepository.getGuildIdByPlayer(uuid))
        } catch (e: Exception) {
            plugin.logger.warning("查询玩家 $uuid 公会身份失败: ${e.message}")
            QueryResult.Failed
        }
    }

    private fun refreshAsync(uuid: UUID) {
        plugin.runAsync {
            when (val fresh = queryDb(uuid)) {
                is QueryResult.Ok -> put(uuid, fresh.guildId)
                // 查询失败：保留旧缓存，不覆盖为"无公会"
                QueryResult.Failed -> {}
            }
        }
    }
}
