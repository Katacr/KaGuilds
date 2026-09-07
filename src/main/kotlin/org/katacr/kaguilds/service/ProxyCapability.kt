package org.katacr.kaguilds.service

import org.katacr.kaguilds.KaGuilds
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 代理能力探测与降级。
 *
 * 设计原则：proxy 不是"配置开关即可靠来源"，而是"运行时验证的能力"。
 * 前置拦截：
 *  - proxy: true 但数据库非 MySQL → 直接降级（SQLite 不支持多服并发写入，会损坏数据）。
 * 运行时探测：
 *  - 开服后进入探测窗口，被动等待来自 KaProxy 的 OnlinePlayersList / ServerIdentify
 *    作为"代理存活"信号；窗口结束仍未收到 → 降级并写回 config.yml: proxy: false。
 *  - 同时通过 ServerHello 探针让代理下发本服真实 serverId，避免多服误用默认 server-id。
 * 所有业务应读取 [isProxyEnabled] 而非直接读 config.proxy。
 */
class ProxyCapability(private val plugin: KaGuilds) {

    /** 配置中期望的 proxy 值。 */
    private val desiredProxy: Boolean = plugin.config.getBoolean("proxy", false)

    /** 运行时实际生效的 proxy 值（探测成功后才为 true）。 */
    @Volatile
    private var effectiveProxy: Boolean = false

    /** 是否已收到代理存活信号。 */
    private val verified = AtomicBoolean(false)

    /** 探测是否已结束（无论成功失败）。 */
    @Volatile
    private var probeFinished = false

    /** 探测窗口时长（毫秒）。需足够长以容纳代理侧周期广播（默认 5s 一次）的若干次推送。 */
    private val probeWindowMs = 25_000L

    /** 降级原因（用于日志区分）。 */
    @Volatile
    private var degradeReason: String? = null

    /**
     * 启动探测。应在 onEnable 中数据库初始化之后、业务逻辑之前调用。
     */
    fun start() {
        if (!desiredProxy) {
            effectiveProxy = false
            probeFinished = true
            return
        }

        // 前置拦截：跨服必须使用 MySQL
        val dbType = plugin.config.getString("database.type", "SQLite") ?: "SQLite"
        if (!dbType.equals("MySQL", ignoreCase = true)) {
            degrade("proxy 模式要求使用 MySQL 数据库，当前为 $dbType，已降级为单服模式。" +
                "请修改 database.type 为 MySQL 后重启；config.yml 的 proxy 已写回 false。")
            return
        }

        // 期望启用：先标记未验证，启动定时任务在窗口结束后判定
        effectiveProxy = false
        probeFinished = false

        // 周期性发送 ServerHello 探针（每 3 秒），确保在探测窗口内总能触达代理。
        // 同时也由 GuildListener 在玩家上线时补发（解决开服瞬间无在线玩家无法发包的问题）。
        sendServerHello()
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!verified.get() && !probeFinished) {
                sendServerHello()
            }
        }, 60L, 60L) // 60 tick = 3 秒

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!verified.get()) {
                degrade("在探测窗口内未收到 KaProxy 信号，跨服功能不可用。" +
                    "已降级为单服模式，config.yml 的 proxy 已写回 false。" +
                    "请确认 KaProxy 已安装并启用 Guilds 模块（legacy-channel-enabled: true）。")
            }
            probeFinished = true
        }, (probeWindowMs / 50).toLong()) // Bukkit tick = 50ms
    }

    /**
     * 玩家上线时补发探针（开服瞬间可能无在线玩家，无法主动发包联系代理）。
     */
    fun onPlayerJoinProbe() {
        if (desiredProxy && !verified.get() && !probeFinished) {
            sendServerHello()
        }
    }

    /**
     * 由 PluginMessageListener 在收到来自代理的 OnlinePlayersList / ServerIdentify 时调用，
     * 标记代理通讯正常。
     */
    fun markProxyReachable() {
        if (desiredProxy && verified.compareAndSet(false, true)) {
            effectiveProxy = true
            plugin.logger.info("[KaGuilds] 检测到 KaProxy 通讯正常，已启用跨服模式。")
        }
    }

    /**
     * 当前代理模式是否真正可用。
     */
    fun isProxyEnabled(): Boolean = effectiveProxy && desiredProxy

    /**
     * 配置是否期望 proxy（用于文档/日志区分）。
     */
    fun desiredProxy(): Boolean = desiredProxy

    /**
     * 降级原因（null 表示未降级或无需降级）。
     */
    fun degradeReason(): String? = degradeReason

    private fun sendServerHello() {
        try {
            val out = java.io.ByteArrayOutputStream()
            java.io.DataOutputStream(out).use { it.writeUTF("ServerHello") }
            plugin.server.onlinePlayers.firstOrNull()
                ?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
                ?: plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    plugin.server.onlinePlayers.firstOrNull()
                        ?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
                }, 20L)
        } catch (e: Exception) {
            plugin.logger.warning("发送 ServerHello 探针失败: ${e.message}")
        }
    }

    private fun degrade(reason: String) {
        effectiveProxy = false
        probeFinished = true
        degradeReason = reason
        plugin.logger.warning("[KaGuilds] $reason")
        writeProxyFalseToConfig()
    }

    private fun writeProxyFalseToConfig() {
        try {
            val configFile = File(plugin.dataFolder, "config.yml")
            if (!configFile.exists()) return
            val lines = configFile.readLines()
            val out = mutableListOf<String>()
            var replaced = false
            for (line in lines) {
                if (!replaced && line.trim().startsWith("proxy:")) {
                    out.add("proxy: false")
                    out.add("# 由 KaGuilds 自动降级写入：${java.time.LocalDateTime.now()} ${degradeReason ?: "探测失败"}")
                    out.add("# 修复原因并确认部署后，请将此值改回 true 并重启。")
                    replaced = true
                } else {
                    out.add(line)
                }
            }
            if (replaced) {
                configFile.writeText(out.joinToString("\n"))
            }
        } catch (e: Exception) {
            plugin.logger.warning("写回 proxy: false 到 config.yml 失败: ${e.message}")
        }
    }
}
