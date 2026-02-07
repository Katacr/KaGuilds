package org.katacr.kaguilds.listener

import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent

class PvPListener(private val plugin: org.katacr.kaguilds.KaGuilds) : Listener {

    /**
     * 核心伤害处理：拦截致死伤害并转为旁观
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDamage(event: EntityDamageEvent) {
        val lang = plugin.langManager
        val player = event.entity as? Player ?: return
        val match = plugin.pvpManager.currentMatch ?: return

        // 仅在战斗正式开始后处理逻辑
        if (!match.isStarted || !match.players.contains(player.uniqueId)) return

        // 只有还在战斗（冒险模式）的玩家需要拦截死亡
        if (player.gameMode != GameMode.ADVENTURE) return

        if (player.health - event.finalDamage <= 0.5) {
            event.isCancelled = true

            // 1. 设置旁观状态
            player.gameMode = GameMode.SPECTATOR
            player.health = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            player.fireTicks = 0
            player.activePotionEffects.clear()

            // 2. 消息与特效
            player.sendMessage(lang.get("arena-pvp-death-msg"))
            player.playSound(player.location, Sound.ENTITY_PLAYER_DEATH, 1f, 1f)

            val guildId = plugin.playerGuildCache[player.uniqueId]
            val teamColor = if (guildId == match.redGuildId)
                lang.get("arena-pvp-red-team-name")
            else
                lang.get("arena-pvp-blue-team-name")
            match.smartBroadcast(lang.get("arena-pvp-death-broadcast", "team" to teamColor, "player" to player.name))

            // 3. 检查胜负
            plugin.pvpManager.checkWinCondition()
        }
    }

    /**
     * 掉线处理
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val lang = plugin.langManager
        val player = event.player
        val match = plugin.pvpManager.currentMatch ?: return

        if (!match.players.contains(player.uniqueId)) return

        if (!match.isStarted) {
            // 准备阶段掉线，直接踢出名单
            match.players.remove(player.uniqueId)
            match.smartBroadcast(lang.get("arena-pvp-ready-quit-broadcast", "player" to player.name))
        } else if (player.gameMode == GameMode.ADVENTURE) {
            // 战斗阶段掉线，视为战败
            match.smartBroadcast(lang.get("arena-pvp-gaming-quit-broadcast", "player" to player.name))
            // 延迟一帧检查，确保状态已更新
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                plugin.pvpManager.checkWinCondition()
            }, 1L)
        }
    }

    /**
     * 进服处理：强制恢复快照
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // 无论比赛是否在进行，都要检查并恢复掉线玩家的快照
        plugin.pvpManager.handlePlayerJoin(event.player)
    }

    /**
     * 指令拦截：防止战斗中传送逃跑
     */
    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val lang = plugin.langManager
        val player = event.player
        val match = plugin.pvpManager.currentMatch ?: return

        if (match.isStarted && match.players.contains(player.uniqueId)) {
            val cmd = event.message.lowercase()
            // 允许管理员指令和公会内部必要指令，拦截 /home, /tpa, /spawn 等
            if (!cmd.startsWith("/kg") && !player.hasPermission("kaguilds.admin")) {
                player.sendMessage(lang.get("arena-pvp-cmd-deny"))
                event.isCancelled = true
            }
        }
    }

    /**
     * 边界保护：防止玩家利用 Bug 跑出封闭竞技场
     */
    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val lang = plugin.langManager
        val match = plugin.pvpManager.currentMatch ?: return
        if (!match.isStarted || !match.players.contains(event.player.uniqueId)) return

        val player = event.player
        if (player.gameMode != GameMode.ADVENTURE) return

        val arena = plugin.arenaManager.arena
        val pos1 = arena.pos1
        val pos2 = arena.pos2

        if (pos1 != null && pos2 != null) {
            val loc = event.to
            // 简单的立方体区域判定
            val minX = minOf(pos1.x, pos2.x)
            val maxX = maxOf(pos1.x, pos2.x)
            val minY = minOf(pos1.y, pos2.y)
            val maxY = maxOf(pos1.y, pos2.y)
            val minZ = minOf(pos1.z, pos2.z)
            val maxZ = maxOf(pos1.z, pos2.z)

            if (loc.x !in minX..maxX || loc.z < minZ || loc.z > maxZ || loc.y < minY || loc.y > maxY) {
                player.sendMessage(lang.get("arena-pvp-out-of-bounds"))
                // 传回对应的出生点
                val spawn = if (plugin.playerGuildCache[player.uniqueId] == match.redGuildId) arena.redSpawn else arena.blueSpawn
                spawn?.let { player.teleport(it) }
            }
        }
    }
}