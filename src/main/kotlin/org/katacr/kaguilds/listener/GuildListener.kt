package org.katacr.kaguilds.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.katacr.kaguilds.KaGuilds

class GuildListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        // 玩家加入时，异步从数据库读取其公会ID并存入缓存
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 确保玩家在数据库中存在记录（用于支持跨服邀请等功能）
            plugin.dbManager.memberRepository.ensurePlayerExists(player.uniqueId, player.name)

            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)
            if (guildId != null) {
                plugin.playerGuildCache[player.uniqueId] = guildId
                plugin.taskManager.activatePlayerTasks(player.uniqueId, guildId)

                // 检查并计算公会的银行利息
                checkAndCalculateGuildInterest(guildId)
            }
        })

        // 代理探测补发：开服瞬间可能无在线玩家，无法主动发包联系代理；
        // 一旦有玩家上线即可补发 ServerHello 探针，触发代理回执 serverId 与在线列表。
        plugin.proxyCapability.onPlayerJoinProbe()

        // 补发空服期间暂存的跨服消息
        plugin.networkBus.flushPending()
    }

    /**
     * 检查并计算公会的银行利息
     * @param guildId 公会ID
     */
    private fun checkAndCalculateGuildInterest(guildId: Int) {
        plugin.interestService.settle(guildId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player

        // 玩家退出时，清理内存，防止内存泄漏
        plugin.playerGuildCache.remove(player.uniqueId)
        plugin.guildChatPlayers.remove(player.uniqueId)

    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        // 只有当坐标（Block）发生变化时才取消，原地转头不取消
        val from = event.from
        val to = event.to
        if (to != null) {
            if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
                val player = event.player
                if (plugin.guildService.isTeleporting(player.uniqueId)) {
                    plugin.guildService.cancelTeleport(player.uniqueId)
                    player.sendMessage(plugin.langManager.get("tp-cancelled-move"))
                }
            }
        }
    }

    @EventHandler
    fun onPlayerDamage(event: EntityDamageEvent) {
        if (event.entity is Player) {
            val player = event.entity as Player
            if (plugin.guildService.isTeleporting(player.uniqueId)) {
                plugin.guildService.cancelTeleport(player.uniqueId)
                player.sendMessage(plugin.langManager.get("tp-cancelled-damage"))
            }
        }
    }
}
