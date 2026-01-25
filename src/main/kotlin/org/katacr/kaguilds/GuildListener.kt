package org.katacr.kaguilds

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent

class GuildListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        // 玩家加入时，异步从数据库读取其公会ID并存入缓存
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
            if (guildId != null) {
                plugin.playerGuildCache[player.uniqueId] = guildId
            }
        })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        // 玩家退出时，清理内存，防止内存泄漏
        plugin.playerGuildCache.remove(event.player.uniqueId)
    }



    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        // 只有当坐标（Block）发生变化时才取消，原地转头不取消
        val from = event.from
        val to = event.to
        if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
            val player = event.player
            if (plugin.guildService.isTeleporting(player.uniqueId)) {
                plugin.guildService.cancelTeleport(player.uniqueId)
                player.sendMessage(plugin.langManager.get("tp-cancelled-move"))
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