package org.katacr.kaguilds.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.katacr.kaguilds.KaGuilds

class NotifyListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        // 延迟 3 秒提示，躲开进服时的其他消息刷屏
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (event.player.isOnline) {
                plugin.guildService.checkAndNotifyRequests(event.player)
            }
        }, 60L)
    }
}