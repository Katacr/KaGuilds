package org.katacr.kaguilds.arena

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import java.util.UUID

class PvPManager(private val plugin: KaGuilds) {

    // 记录邀请：接收公会ID -> 发起公会ID
    val pendingInvites = mutableMapOf<Int, Int>()
    // 记录邀请的时间戳，用于超时处理
    val inviteTimestamp = mutableMapOf<Int, Long>()

    // 当前战斗状态（后续步骤使用）
    var currentMatch: ActiveMatch? = null

    // 记录玩家的背包快照，用于战斗结束时恢复
    private val inventorySnapshots = mutableMapOf<UUID, Array<org.bukkit.inventory.ItemStack?>>()

    /**
     * 发起挑战
     */
    fun sendChallenge(senderGuildId: Int, targetGuildId: Int): Boolean {
        if (currentMatch != null) return false // 竞技场已被占用

        pendingInvites[targetGuildId] = senderGuildId
        inviteTimestamp[targetGuildId] = System.currentTimeMillis()
        return true
    }

    /**
     * 通知目标公会
     */
    fun notifyTargetGuild(targetGuildId: Int, senderGuildName: String) {
        val msg = net.md_5.bungee.api.chat.TextComponent("§6§l[公会战] §f公会 §b$senderGuildName §f向你们发起了挑战！ ")

        val acceptBtn = net.md_5.bungee.api.chat.TextComponent("§a§l[点击接受]")
        acceptBtn.clickEvent = net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/kg pvp accept"
        )
        val hoverText = net.md_5.bungee.api.chat.hover.content.Text("§7点击后将立即进入准备阶段")
        acceptBtn.hoverEvent = net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            hoverText
        )
        msg.addExtra(acceptBtn)
        plugin.server.onlinePlayers.forEach { onlinePlayer ->
            val playerGuildId = plugin.playerGuildCache[onlinePlayer.uniqueId]
            if (playerGuildId == targetGuildId) {
                if (plugin.dbManager.isStaff(onlinePlayer.uniqueId, targetGuildId)) {
                    onlinePlayer.spigot().sendMessage(msg)
                }
            }
        }
    }

    /**
     * 保存玩家背包快照并清空
     */
    fun takeSnapshot(player: Player) {
        // 深度备份当前背包
        inventorySnapshots[player.uniqueId] = player.inventory.contents.clone()
        player.inventory.clear()
        player.sendMessage("§7[PVP] 你的原始背包已安全存档。")
    }

    /**
     * 恢复玩家背包快照
     */
    fun restoreSnapshot(player: Player) {
        val savedItems = inventorySnapshots.remove(player.uniqueId)
        if (savedItems != null) {
            player.inventory.contents = savedItems
            player.sendMessage("§a[PVP] 你的原始背包已恢复。")
        }
    }

    /**
     * 接受挑战并开启准备倒计时
     */
    fun acceptChallenge(targetGuildId: Int): Int? {
        val senderGuildId = pendingInvites.remove(targetGuildId)
        val timestamp = inviteTimestamp.remove(targetGuildId) ?: 0L

        // 检查邀请是否过期 (60秒)
        if (senderGuildId == null || System.currentTimeMillis() - timestamp > 60000) {
            return null
        }

        // 初始化对战数据
        currentMatch = ActiveMatch(
            redGuildId = senderGuildId,
            blueGuildId = targetGuildId,
            startTime = System.currentTimeMillis()
        )

        startReadyTask()

        return senderGuildId
    }

    private fun startReadyTask() {
        val readyTime = plugin.config.getInt("guild.arena.ready-time", 120)

        object : org.bukkit.scheduler.BukkitRunnable() {
            var timeLeft = readyTime

            override fun run() {
                val match = currentMatch ?: run { this.cancel(); return }

                // 如果战斗已经因为某种原因开始了或取消了，停止倒计时
                if (match.isStarted) { this.cancel(); return }

                if (timeLeft <= 0) {
                    // --- 扳机按下：正式调用开始战斗 ---
                    startBattle()
                    this.cancel()
                    return
                }

                // 倒计时提示 (每30秒提示一次，最后10秒每秒提示)
                if (timeLeft % 30 == 0 || timeLeft <= 10) {
                    plugin.server.broadcastMessage("§e§l[PVP] §f公会战准备倒计时: §c$timeLeft §f秒")
                    plugin.server.broadcastMessage("§e§l[PVP] §f请参赛成员尽快输入 §a/kg pvp ready")
                }

                timeLeft--
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    /**
     * 正式开始战斗
     */
    fun startBattle() {
        val match = currentMatch ?: return

        // 检查是否有最少人数要求
        val minPlayers = plugin.config.getInt("guild.arena.min-players", 2)
        if (match.players.size < minPlayers) {
            plugin.server.broadcastMessage("§e§l[PVP] §c由于准备人数不足 ${minPlayers}人，比赛自动取消。")
            // 记得退费逻辑可以写在这里
            stopBattle(null)
            return
        }

        match.isStarted = true

        // 只遍历那些点了 Ready 的人
        match.players.mapNotNull { plugin.server.getPlayer(it) }.forEach { player ->
            takeSnapshot(player) // 保存原本背包

            // 发放套装
            if (plugin.config.getBoolean("guild.arena.kit", true)) {
                player.inventory.contents = plugin.arenaManager.kitContents?.clone() ?: player.inventory.contents
            }

            player.gameMode = org.bukkit.GameMode.ADVENTURE
            player.sendMessage("§6§l>> §e§l战斗正式爆发！")
        }

        startMatchTimer()
    }

    /**
     * 实现游戏时长监控
     */
    private fun startMatchTimer() {
        val pvpTime = plugin.config.getInt("guild.arena.pvp-time", 600)

        object : org.bukkit.scheduler.BukkitRunnable() {
            var timeLeft = pvpTime
            override fun run() {
                val match = currentMatch ?: run { this.cancel(); return }

                if (timeLeft <= 0) {
                    determineWinnerBySurvival() // 时间到，强行结算
                    this.cancel()
                    return
                }

                // 最后 10 秒倒计时提醒
                if (timeLeft <= 10) {
                    match.players.forEach { uuid ->
                        plugin.server.getPlayer(uuid)?.sendMessage("§c距离战斗结束还剩 §l$timeLeft §c秒！")
                    }
                }
                timeLeft--
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }


    fun handlePlayerJoin(player: Player) {
        // 检查这个玩家是否有待恢复的快照
        if (inventorySnapshots.containsKey(player.uniqueId)) {
            // 如果他不在战斗区域（说明是掉线后重新连接）
            if (currentMatch == null || !currentMatch!!.players.contains(player.uniqueId)) {
                restoreSnapshot(player)
                player.gameMode = org.bukkit.GameMode.SURVIVAL // 恢复生存模式
            }
        }
    }

    /**
     * 战局结算：通过存活人数判定胜负
     */
    private fun determineWinnerBySurvival() {
        val match = currentMatch ?: return

        // 过滤出当前还在竞技场内且存活的玩家
        val redAlive = match.players.count { uuid ->
            val p = plugin.server.getPlayer(uuid)
            p != null && p.gameMode != org.bukkit.GameMode.SPECTATOR && plugin.playerGuildCache[uuid] == match.redGuildId
        }
        val blueAlive = match.players.count { uuid ->
            val p = plugin.server.getPlayer(uuid)
            p != null && p.gameMode != org.bukkit.GameMode.SPECTATOR && plugin.playerGuildCache[uuid] == match.blueGuildId
        }

        val winnerId = when {
            redAlive > blueAlive -> match.redGuildId
            blueAlive > redAlive -> match.blueGuildId
            else -> null // 平局
        }

        stopBattle(winnerId)
    }

    /**
     * 停止战斗并清理
     * @param winnerId 获胜公会ID，null 则为平局
     */
    fun stopBattle(winnerId: Int?) {
        val match = currentMatch ?: return

        // 1. 处理数据库统计与奖励
        processMatchResults(match, winnerId)

        // 2. 恢复所有参与者的状态
        match.players.forEach { uuid ->
            val player = plugin.server.getPlayer(uuid) ?: return@forEach

            // 恢复背包
            restoreSnapshot(player)

            // 恢复模式与状态
            player.gameMode = GameMode.SURVIVAL

            // 传送到公会家园或主城 (这里假设传送到公会传送点)
            val guildData = plugin.dbManager.getGuildData(plugin.playerGuildCache[uuid] ?: -1)
            if (guildData?.teleportLocation != null) {
                val loc = org.katacr.kaguilds.util.SerializationUtil.deserializeLocation(guildData.teleportLocation)
                if (loc != null) player.teleport(loc)
            } else {
                player.teleport(player.world.spawnLocation)
            }
        }

        // 3. 全局广播
        if (winnerId != null) {
            val winnerName = plugin.dbManager.getGuildData(winnerId)?.name ?: "未知"
            plugin.server.broadcastMessage("§e§l[PVP] §f战斗结束！公会 §b$winnerName §f获得了最终胜利！")
        } else {
            plugin.server.broadcastMessage("§e§l[PVP] §f战斗结束！双方势均力敌，以平局收场。")
        }

        // 4. 清除当前战斗实例
        currentMatch = null
    }

    private fun processMatchResults(match: ActiveMatch, winnerId: Int?) {
        val redId = match.redGuildId
        val blueId = match.blueGuildId

        // 1. 更新 guild_data 统计
        when (winnerId) {
            redId -> {
                updateGuildStats(redId, "wins")
                updateGuildStats(blueId, "losses")
                executeRewardCommands(redId)
            }
            blueId -> {
                updateGuildStats(blueId, "wins")
                updateGuildStats(redId, "losses")
                executeRewardCommands(blueId)
            }
            else -> {
                updateGuildStats(redId, "draws")
                updateGuildStats(blueId, "draws")
            }
        }
        // 增加总场次
        updateGuildStats(redId, "total")
        updateGuildStats(blueId, "total")

        // 2. 插入详细战报历史
        val sql = "INSERT INTO guild_pvp_history (red_guild_id, blue_guild_id, winner_guild_id, start_time, end_time) VALUES (?, ?, ?, ?, ?)"
        plugin.dbManager.dataSource?.connection?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, redId)
                ps.setInt(2, blueId)
                if (winnerId != null) ps.setInt(3, winnerId) else ps.setNull(3, java.sql.Types.INTEGER)
                ps.setLong(4, match.startTime)
                ps.setLong(5, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }

    /**
     * 辅助方法：执行奖励命令
     */
    private fun executeRewardCommands(guildId: Int) {
        val commands = plugin.config.getStringList("guild.arena.reward-command")
        commands.forEach { cmd ->
            val finalCmd = cmd.replace("{id}", guildId.toString())
            plugin.server.dispatchCommand(plugin.server.consoleSender, finalCmd)
        }
    }

    /**
     * 辅助方法：更新公会战统计字段
     */
    private fun updateGuildStats(guildId: Int, type: String) {
        val column = when(type) {
            "wins" -> "pvp_wins"
            "losses" -> "pvp_losses"
            "draws" -> "pvp_draws"
            else -> "pvp_total"
        }
        val sql = "UPDATE guild_data SET $column = $column + 1 WHERE id = ?"
        plugin.dbManager.dataSource?.connection?.use { conn ->
            conn.prepareStatement(sql).use {
                it.setInt(1, guildId)
                it.executeUpdate()
            }
        }
    }

    /**
     * 检查胜负条件
     */
    fun checkWinCondition() {
        val match = currentMatch ?: return
        if (!match.isStarted) return // 还没开始，不判胜负

        // 获取红队当前存活人数 (在线且处于战斗模式)
        val redAlive = match.players.count { uuid ->
            val p = plugin.server.getPlayer(uuid)
            p != null && p.isOnline && p.gameMode == org.bukkit.GameMode.ADVENTURE && plugin.playerGuildCache[uuid] == match.redGuildId
        }

        // 获取蓝队当前存活人数
        val blueAlive = match.players.count { uuid ->
            val p = plugin.server.getPlayer(uuid)
            p != null && p.isOnline && p.gameMode == org.bukkit.GameMode.ADVENTURE && plugin.playerGuildCache[uuid] == match.blueGuildId
        }

        // 只要有一方死光，就结算
        if (redAlive == 0 || blueAlive == 0) {
            val winnerId = when {
                redAlive > 0 -> match.redGuildId
                blueAlive > 0 -> match.blueGuildId
                else -> null // 同归于尽
            }
            stopBattle(winnerId)
        }
    }

}

// 定义战斗中的状态数据类
data class ActiveMatch(
    val redGuildId: Int,
    val blueGuildId: Int,
    val startTime: Long,
    val players: MutableSet<UUID> = mutableSetOf(),
    var isStarted: Boolean = false
) {
    /**
     * 向所有参与本场战斗的玩家发送消息
     */
    fun broadcast(message: String) {
        // 获取插件实例以访问服务器方法
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("KaGuilds") as? KaGuilds

        players.forEach { uuid ->
            val player = plugin?.server?.getPlayer(uuid)
            // 只有玩家在线时才发送，防止报错
            player?.sendMessage(message)
        }
    }
}