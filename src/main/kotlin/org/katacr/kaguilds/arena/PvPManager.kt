package org.katacr.kaguilds.arena

import org.bukkit.GameMode
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
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

    // 记录当前的BossBar
    private var pvpBar: BossBar? = null

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
        val lang = plugin.langManager
        val msg = net.md_5.bungee.api.chat.TextComponent(lang.get("arena-pvp-invite-msg", "name" to senderGuildName))

        val acceptBtn = net.md_5.bungee.api.chat.TextComponent(lang.get("arena-pvp-accept-btn"))
        acceptBtn.clickEvent = net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/kg pvp accept"
        )
        val hoverText = net.md_5.bungee.api.chat.hover.content.Text(lang.get("arena-pvp-accept-btn-hover"))
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
        val lang = plugin.langManager
        inventorySnapshots[player.uniqueId] = player.inventory.contents.clone()
        player.inventory.clear()
        player.sendMessage(lang.get("arena-pvp-save-inventory"))
    }

    /**
     * 恢复玩家背包快照
     */
    fun restoreSnapshot(player: Player) {
        val lang = plugin.langManager
        val savedItems = inventorySnapshots.remove(player.uniqueId)
        if (savedItems != null) {
            player.inventory.contents = savedItems
            player.sendMessage(lang.get("arena-pvp-restore-inventory"))
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

    /**
     * 开始准备任务
     * 逻辑：此阶段仅进行倒计时、BossBar显示和参赛名单记录，不做任何玩家状态修改。
     */
    private fun startReadyTask() {
        val lang = plugin.langManager
        val readyTime = plugin.config.getInt("guild.arena.ready-time", 120)
        val match = currentMatch ?: return
        match.totalTime = readyTime

        object : org.bukkit.scheduler.BukkitRunnable() {
            var timeLeft = readyTime

            override fun run() {

                // 检查比赛是否由于某一方意外操作（如管理员删除公会）导致提前消失
                val m = currentMatch ?: run {
                    removeBossBar()
                    cancel()
                    return
                }

                // 如果比赛已经通过某种方式开始了，停止此任务
                if (m.isStarted) { cancel(); return }

                if (timeLeft <= 0) {
                    startBattle()
                    cancel()
                    return
                }
                val anyRedOnline = plugin.server.onlinePlayers.any { plugin.playerGuildCache[it.uniqueId] == m.redGuildId }
                val anyBlueOnline = plugin.server.onlinePlayers.any { plugin.playerGuildCache[it.uniqueId] == m.blueGuildId }

                if (!anyRedOnline && !anyBlueOnline) {
                    // 双方公会都没有任何人在服务器了，直接销毁，不计分
                    currentMatch = null
                    removeBossBar()
                    cancel()
                    return
                }

                // 更新 BossBar 进度与 mm:ss 格式标题
                pvpBar?.progress = (timeLeft.toDouble() / m.totalTime.toDouble()).coerceIn(0.0, 1.0)
                updateBossBar(timeLeft)

                // 仅对参赛双方广播倒计时，减少全服骚扰
                if (timeLeft % 30 == 0 || (timeLeft in 1..10)) {
                    m.smartBroadcast(lang.get("arena-pvp-ready-countdown", "time" to timeLeft.toString()))
                }

                timeLeft--
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    /**
     * 正式开始战斗
     */
    fun startBattle() {
        val lang = plugin.langManager
        val match = currentMatch ?: return

        // 1. 筛选当前真实在线的准备玩家并清理名单
        val participants = match.players.mapNotNull { plugin.server.getPlayer(it) }.filter { it.isOnline }
        match.players.retainAll(participants.map { it.uniqueId }.toSet())

        // 2. 检查人数：确保两边都至少有一人且满足最小总人数
        val minPlayers = plugin.config.getInt("guild.arena.min-players", 2)
        val redGroup = participants.filter { plugin.playerGuildCache[it.uniqueId] == match.redGuildId }
        val blueGroup = participants.filter { plugin.playerGuildCache[it.uniqueId] == match.blueGuildId }

        if (participants.size < minPlayers || redGroup.isEmpty() || blueGroup.isEmpty()) {
            match.smartBroadcast(lang.get("arena-pvp-not-enough-players"))
            stopBattle(null)
            return
        }

        // 3. 获取竞技场坐标
        val redSpawn = plugin.arenaManager.arena.redSpawn
        val blueSpawn = plugin.arenaManager.arena.blueSpawn

        if (redSpawn == null || blueSpawn == null) {
            match.smartBroadcast(lang.get("arena-pvp-no-spawn"))
            stopBattle(null)
            return
        }

        match.isStarted = true

        // 4. 全服广播开战消息
        val redName = plugin.dbManager.getGuildData(match.redGuildId)?.name ?: "RED-TEAM"
        val blueName = plugin.dbManager.getGuildData(match.blueGuildId)?.name ?: "BLUE-TEAM"
        plugin.server.broadcastMessage(lang.get("arena-pvp-start", "red" to redName, "blue" to blueName))

        // 5. 物资准备
        val redKit = plugin.arenaManager.redKitContents
        val blueKit = plugin.arenaManager.blueKitContents

        // 6. 处理参战玩家状态
        participants.forEach { player ->
            val playerGuildId = plugin.playerGuildCache[player.uniqueId]
            val isRedTeam = (playerGuildId == match.redGuildId)

            // A. 状态重置
            player.health = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            player.foodLevel = 20
            player.fireTicks = 0
            player.activePotionEffects.clear()

            // B. 保存原本背包并清空
            takeSnapshot(player)
            player.inventory.clear()

            // C. 传送并设置模式
            val spawn = if (isRedTeam) plugin.arenaManager.arena.redSpawn else plugin.arenaManager.arena.blueSpawn
            spawn?.let { player.teleport(it) }
            player.gameMode = GameMode.ADVENTURE

            // D. 根据阵营发放对应套装
            val targetKit = if (isRedTeam) redKit else blueKit

            if (targetKit != null && plugin.config.getBoolean("guild.arena.kit", true)) {
                // 填充主背包 (0-35)
                for (i in 0 until 36) {
                    if (i < targetKit.size) player.inventory.setItem(i, targetKit[i]?.clone())
                }
                // 填充盔甲 (36-39)
                if (targetKit.size >= 40) {
                    val armor = targetKit.sliceArray(36..39).map { it?.clone() }.toTypedArray()
                    player.inventory.armorContents = armor
                }
                // 填充副手 (40)
                if (targetKit.size >= 41) {
                    player.inventory.setItemInOffHand(targetKit[40]?.clone())
                }
            }

            player.updateInventory()
            player.sendMessage(lang.get("arena-pvp-start-msg"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1f, 1f)
        }

        // 7. 启动正式战斗计时器
        startMatchTimer()
    }
    /**
     * 实现游戏时长监控
     */
    private fun startMatchTimer() {
        val battleTime = plugin.config.getInt("guild.arena.battle-time", 300)
        val match = currentMatch ?: return
        match.totalTime = battleTime

        object : org.bukkit.scheduler.BukkitRunnable() {
            var timeLeft = battleTime

            override fun run() {
                val m = currentMatch ?: run { removeBossBar(); cancel(); return }

                if (timeLeft <= 0) {
                    // 时间到，判定当前存活人数
                    checkWinCondition() // 如果两边都有人，checkWinCondition 会触发平局逻辑
                    cancel()
                    return
                }

                // 更新 BossBar
                pvpBar?.progress = (timeLeft.toDouble() / m.totalTime.toDouble()).coerceIn(0.0, 1.0)
                updateBossBar(timeLeft)

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
                player.gameMode = GameMode.SURVIVAL // 恢复生存模式
            }
        }
    }

    /**
     * 停止战斗并清理
     * @param winnerId 获胜公会ID，null 则为平局
     */
    fun stopBattle(winnerId: Int?) {
        val lang = plugin.langManager
        val match = currentMatch ?: return
        val started = match.isStarted // 记录战斗是否真正开始过

        // 1. 立即停止 UI 和 逻辑任务
        removeBossBar()

        if (started) {
            // --- 场景 A: 战斗已正式开始过，进行结算 ---

            // 全服广播结果
            broadcastFinalResult(match, winnerId)

            // 遍历参战名单恢复状态
            match.players.forEach { uuid ->
                val player = plugin.server.getPlayer(uuid) ?: return@forEach

                // 状态重置
                player.health = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                player.foodLevel = 20
                player.fireTicks = 0
                player.activePotionEffects.clear()

                // 恢复背包、模式并传回家
                restoreSnapshot(player)
                player.gameMode = GameMode.SURVIVAL
                teleportHome(player)

                // 胜负仪式感
                val playerGuildId = plugin.playerGuildCache[player.uniqueId]
                if (winnerId != null && playerGuildId == winnerId) {
                    val title = lang.get("arena-pvp-victory-title")
                    val subtitle = lang.get("arena-pvp-victory-subtitle")
                    player.sendTitle(title, subtitle, 10, 70, 20)
                    player.playSound(player.location, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
                } else if (winnerId != null) {
                    val title = lang.get("arena-pvp-defeat-title")
                    val subtitle = lang.get("arena-pvp-defeat-subtitle")
                    player.sendTitle(title, subtitle, 10, 70, 20)
                }
            }

            // 异步更新数据库战绩与历史
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                processMatchResults(match, winnerId)
            })

        } else {
            // 准备阶段取消，不计入统计，仅执行退款
            match.smartBroadcast(lang.get("arena-pvp-not-enough-players"))

            // 退还公会挑战金
            val fee = plugin.config.getDouble("balance.pvp", 300.0)
            if (fee > 0) {
                // 这里我们退还给发起公会（通常是 redGuildId 发起的挑战）
                val success = plugin.dbManager.updateGuildBalance(match.redGuildId, fee)
                if (success) {
                    match.smartBroadcast(lang.get("arena-pvp-refund-success", "fee" to fee.toString()))
                }
            }
        }

        // 5. 彻底释放引用，结束生命周期
        currentMatch = null
    }

    /**
     * 公示最终结果
     */
    private fun broadcastFinalResult(match: ActiveMatch, winnerId: Int?) {
        val lang = plugin.langManager
        val redName = plugin.dbManager.getGuildData(match.redGuildId)?.name ?: "RED_TEAM"
        val blueName = plugin.dbManager.getGuildData(match.blueGuildId)?.name ?: "BLUE_TEAM"

        if (winnerId == null) {
            plugin.server.broadcastMessage(lang.get("arena-pvp-draw-message", "red" to redName, "blue" to blueName))  // "§e$redName §7vs §b$blueName §f双方握手言和，平局收场！"
        } else {
            val winnerName = if (winnerId == match.redGuildId) redName else blueName
            plugin.server.broadcastMessage(lang.get("arena-pvp-victory-message", "red" to redName, "blue" to blueName, "winner" to winnerName)) // "§e$redName §7vs §b$blueName §f战斗结束，§a§l$winnerName §f勇夺桂冠！"
        }
    }

    /**
     * 传送玩家回家或公会基地
     */
    private fun teleportHome(player: Player) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: -1
        val guildData = plugin.dbManager.getGuildData(guildId)

        // 如果有公会家园传家园，没有传主城出生点
        val loc = guildData?.teleportLocation?.let {
            org.katacr.kaguilds.util.SerializationUtil.deserializeLocation(it)
        } ?: player.world.spawnLocation

        player.teleport(loc)
    }

    /**
     * 战局结算：更新数据库统计与历史记录
     */
    private fun processMatchResults(match: ActiveMatch, winnerId: Int?) {
        val lang = plugin.langManager
        val redId = match.redGuildId
        val blueId = match.blueGuildId

        // 使用异步任务，避免数据库操作卡顿主线程
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                // 1. 更新 guild_data 统计
                when (winnerId) {
                    redId -> {
                        updateGuildStats(redId, "wins")
                        updateGuildStats(blueId, "losses")
                        // 奖励指令通常涉及玩家操作，建议回主线程执行或确保指令支持异步
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                            executeRewardCommands(redId)
                        })
                    }
                    blueId -> {
                        updateGuildStats(blueId, "wins")
                        updateGuildStats(redId, "losses")
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                            executeRewardCommands(blueId)
                        })
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
            } catch (e: Exception) {
                plugin.logger.severe(lang.get("arena-pvp-update-history-error", "error" to e.message.toString()))  //"更新公会战战报时发生错误: ${e.message}"
                e.printStackTrace()
            }
        })
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
     * 核心胜负判定逻辑
     * @param forceCutoff 是否强制结算（true 用于倒计时结束，false 用于实时伤亡检查）
     */
    fun checkWinCondition(forceCutoff: Boolean = false) {
        val match = currentMatch ?: return
        if (!match.isStarted) return // 还没开始，不判胜负

        // 1. 统计红队当前存活人数 (在线且处于 ADVENTURE 模式)
        val redAlive = match.players.count { uuid ->
            val p = plugin.server.getPlayer(uuid)
            p != null && p.isOnline && p.gameMode == GameMode.ADVENTURE && plugin.playerGuildCache[uuid] == match.redGuildId
        }

        // 2. 统计蓝队当前存活人数
        val blueAlive = match.players.count { uuid ->
            val p = plugin.server.getPlayer(uuid)
            p != null && p.isOnline && p.gameMode == GameMode.ADVENTURE && plugin.playerGuildCache[uuid] == match.blueGuildId
        }

        // A. 实时结算：只要有一方人死光了，立刻结束
        if (redAlive == 0 || blueAlive == 0) {
            val winnerId = when {
                redAlive > 0 -> match.redGuildId
                blueAlive > 0 -> match.blueGuildId
                else -> null // 两边都没人了（可能同归于尽或全部掉线）
            }
            stopBattle(winnerId)
            return
        }

        // B. 超时结算：如果时间到了且两边都有人活着
        if (forceCutoff) {
            val winnerId = when {
                redAlive > blueAlive -> match.redGuildId
                blueAlive > redAlive -> match.blueGuildId
                else -> null // 人数完全相等，判定平局
            }
            stopBattle(winnerId)
            return
        }
        // C. 继续战斗：两边都有人且时间还没到，则什么都不做
    }

    /**
     * 更新 BossBar 显示
     */
    fun updateBossBar(seconds: Int) {
        val lang = plugin.langManager
        val match = currentMatch ?: return
        val redName = plugin.dbManager.getGuildData(match.redGuildId)?.name ?: "红队"
        val blueName = plugin.dbManager.getGuildData(match.blueGuildId)?.name ?: "蓝队"
        val timeStr = formatTime(seconds)

        val title = if (!match.isStarted) {
            // 准备阶段显示
            lang.get("arena-pvp-bossbar-title-ready", "red" to redName, "blue" to blueName, "time" to timeStr)
            // "§c§l[ $redName ] §fVS §b§l[ $blueName ]   §6§l$timeStr §7(输入/kg pvp ready加入)"
        } else {
            // 战斗阶段显示：■■□ [ 红队 ] 3:20 [ 蓝队 ] □■■
            val redIcons = getTeamStatusIcons(match, match.redGuildId, true)
            val blueIcons = getTeamStatusIcons(match, match.blueGuildId, false)
            lang.get("arena-pvp-bossbar-title-gaming", "red" to redName, "blue" to blueName, "time" to timeStr, "redicons" to redIcons, "blueicons" to blueIcons)
            // "$redIcons §c§l[ $redName ] §f§l$timeStr §b§l[ $blueName ] $blueIcons"
        }

        if (pvpBar == null) {
            pvpBar = plugin.server.createBossBar(title, BarColor.YELLOW, BarStyle.SOLID)
        } else {
            pvpBar?.setTitle(title)
            pvpBar?.color = if (match.isStarted) BarColor.RED else BarColor.YELLOW
        }

        // 维护玩家可见性（仅参赛公会可见）
        plugin.server.onlinePlayers.forEach { p ->
            val gid = plugin.playerGuildCache[p.uniqueId]
            if (gid == match.redGuildId || gid == match.blueGuildId) {
                pvpBar?.addPlayer(p)
            } else {
                pvpBar?.removePlayer(p)
            }
        }
    }

    /**
     * 生成 □□■■■ 状态图标
     * @param isMirrored 是否镜像排列（用于右侧蓝队）
     */
    private fun getTeamStatusIcons(match: ActiveMatch, guildId: Int, isMirrored: Boolean): String {
        // 获取该公会参与战斗的所有 UUID
        val lang = plugin.langManager
        val teamMembers = match.players.filter { plugin.playerGuildCache[it] == guildId }

        val icons = teamMembers.map { uuid ->
            val p = plugin.server.getPlayer(uuid)
            // 存活条件：在线且模式为 ADVENTURE
            if (p != null && p.isOnline && p.gameMode == GameMode.ADVENTURE) lang.get("arena-pvp-bossbar-icon-alive") else lang.get("arena-pvp-bossbar-icon-dead")
        }

        return if (isMirrored) icons.joinToString("") else icons.reversed().joinToString("")
    }

    /**
     * 清除 BossBar
     */
    fun removeBossBar() {
        pvpBar?.removeAll()
        pvpBar = null
    }
    /**
     * 将秒数转换为 mm:ss 格式
     */
    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }

}

// 定义战斗中的状态数据类
data class ActiveMatch(
    val redGuildId: Int,
    val blueGuildId: Int,
    val startTime: Long,
    val players: MutableSet<UUID> = mutableSetOf(),
    var isStarted: Boolean = false,
    var totalTime: Int = 120
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


    fun smartBroadcast(message: String) {
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("KaGuilds") as? KaGuilds ?: return

        plugin.server.onlinePlayers.forEach { onlinePlayer ->
            val playerGuildId = plugin.playerGuildCache[onlinePlayer.uniqueId]
            // 只有属于参赛两方的玩家才会收到消息
            if (playerGuildId == redGuildId || playerGuildId == blueGuildId) {
                onlinePlayer.sendMessage(message)
            }
        }
    }
}