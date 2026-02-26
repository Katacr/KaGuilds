package org.katacr.kaguilds.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.MessageUtil

class NotifyListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        // 延迟 3 秒提示，躲开进服时的其他消息刷屏
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (event.player.isOnline) {
                plugin.guildService.checkAndNotifyRequests(event.player)
                checkPvPReady(event.player)
            }
        }, 60L)
    }

    /**
     * 检查玩家是否为参战公会成员，若是则发送准备提示
     */
    private fun checkPvPReady(player: org.bukkit.entity.Player) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return

        val match = plugin.pvpManager.currentMatch ?: return

        // 只有在准备阶段的参战公会成员才提示
        if (!match.isStarted && (match.redGuildId == guildId || match.blueGuildId == guildId)) {
            val lang = plugin.langManager
            val msg = MessageUtil.createText(lang.get("arena-pvp-ready-hint"))

            val readyBtn = MessageUtil.createClickableText(
                text = lang.get("arena-pvp-ready-btn"),
                hoverText = lang.get("arena-pvp-ready-btn-hover"),
                command = "/kg pvp ready"
            )

            msg.addExtra(readyBtn)
            player.spigot().sendMessage(msg)
        }
    }
}