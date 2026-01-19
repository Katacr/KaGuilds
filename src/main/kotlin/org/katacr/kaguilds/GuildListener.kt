package org.katacr.kaguilds

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
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
}