package org.katacr.kaguilds.listener

import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.entity.PlayerDeathEvent

class PvPListener(private val plugin: org.katacr.kaguilds.KaGuilds) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val match = plugin.pvpManager.currentMatch ?: return

        // 如果玩家不在当前比赛名单中，跳过
        if (!match.players.contains(player.uniqueId)) return

        // 检查伤害是否致命
        if (player.health - event.finalDamage <= 0) {
            // 拦截死亡
            event.isCancelled = true

            // 1. 设置为旁观者模式
            player.gameMode = GameMode.SPECTATOR
            player.health = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0

            // 2. 特效与消息
            player.sendMessage("§c§l[!] 你已战死！正在进入旁观模式...")
            player.playSound(player.location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f)

            // 广播击杀信息（可选）
            val guildId = plugin.playerGuildCache[player.uniqueId]
            val teamColor = if (guildId == match.redGuildId) "§c红队" else "§b蓝队"
            match.broadcast("§7[PVP] $teamColor §f的 §e${player.name} §f已退出战场！")

            // 3. 检查是否有公会全军覆没
            plugin.pvpManager.checkWinCondition()
        }
    }
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val match = plugin.pvpManager.currentMatch ?: return

        // 如果掉线的玩家在比赛中
        if (match.players.contains(player.uniqueId)) {
            // 如果比赛已经开始（玩家在冒险模式）
            if (player.gameMode == GameMode.ADVENTURE) {
                match.broadcast("§7[PVP] §e${player.name} §c离开了服务器，视为战败。")
                // 将其设为旁观状态（逻辑上），这样 checkWinCondition 会统计到他已出局
                // 注意：这里不需要改 GameMode，因为他已经下线了，只需触发胜负检查

                // 延迟一小会儿检查，确保服务器已经处理完该玩家的离线状态
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    plugin.pvpManager.checkWinCondition()
                }, 1L)
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        // 核心安全逻辑：无论公会战是否结束，只要这个玩家有“快照”没恢复，就强制恢复
        // 这是为了防止玩家通过“开战 -> 拿套装 -> 掉线 -> 等比赛结束 -> 上线”来刷取套装物品
        plugin.pvpManager.handlePlayerJoin(player)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val match = plugin.pvpManager.currentMatch ?: return

        if (match.players.contains(player.uniqueId) && match.isStarted) {
            // 防止掉落物品
            event.keepInventory = true
            event.drops.clear()

            // 死亡即变旁观
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                player.spigot().respawn() // 自动重生
                player.gameMode = GameMode.SPECTATOR
                plugin.pvpManager.checkWinCondition()
            }, 1L)
        }
    }

}