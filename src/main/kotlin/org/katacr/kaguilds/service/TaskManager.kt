package org.katacr.kaguilds.service

import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.MessageUtil
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 任务管理器
 * 负责管理公会任务系统的配置、进度跟踪和奖励执行
 */
class TaskManager(val plugin: KaGuilds) {
    private val tasksByType = mutableMapOf<String, MutableList<TaskDefinition>>()
    val guildDoneCache = mutableMapOf<Int, MutableSet<String>>()
    val dailyDoneCache = mutableMapOf<UUID, MutableSet<String>>()

    // BossBar 缓存: PlayerUUID -> TaskKey -> BossBar (个人任务)
    private val bossBarCache = mutableMapOf<UUID, MutableMap<String, org.bukkit.boss.BossBar>>()
    // 全局任务 BossBar 缓存: GuildId -> TaskKey -> BossBar (同一公会的所有玩家共享)
    private val globalBossBarCache = mutableMapOf<Int, MutableMap<String, org.bukkit.boss.BossBar>>()

    // BossBar 定时器缓存: PlayerUUID -> TaskKey -> TaskId (个人任务)
    private val bossBarTimerCache = mutableMapOf<UUID, MutableMap<String, Int>>()
    // 全局任务 BossBar 定时器缓存: GuildId -> TaskKey -> TaskId
    private val globalBossBarTimerCache = mutableMapOf<Int, MutableMap<String, Int>>()

    /**
     * 任务定义数据类
     */
    data class TaskDefinition(
        val key: String,           // 任务标识符
        val name: String,          // 任务名称
        val type: String,          // 任务类型: global, daily
        val eventType: String,     // 事件类型: login, kill_mobs, break_block, donate, chat等
        val target: String,        // 任务目标(实体/方块类型等), * 表示任意
        val amount: Int,           // 目标数量
        val lore: List<String>,    // 任务描述
        val actions: List<String>  // 完成后执行的动作
    )

    val taskDefinitions = mutableMapOf<String, TaskDefinition>()

    // 午夜缓存检查定时器
    private val midnightTimer = Timer("KaGuilds-Midnight-Checker", true)
    private var midnightCheckRunning = false

    // 任务重置时间定时器
    private val taskResetTimer = Timer("KaGuilds-TaskReset-Checker", true)
    private var taskResetRunning = false

    /**
     * 初始化任务管理器
     */
    fun initialize() {
        loadTaskDefinitions()
        startMidnightCacheCheck()
        startTaskResetCheck()
    }

    /**
     * 启动午夜缓存检查任务
     * 使用系统时间精确在 0:00 触发
     */
    private fun startMidnightCacheCheck() {
        if (midnightCheckRunning) {
            return
        }

        val now = java.time.LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val delay = java.time.Duration.between(now, nextMidnight).toMillis()

        midnightTimer.schedule(object : TimerTask() {
            override fun run() {
                // 切换到 Bukkit 主线程执行 Bukkit API
                plugin.server.scheduler.runTask(plugin, Runnable {
                    checkAllOnlinePlayersCache()
                })

                // 递归调度下一个午夜
                scheduleNextMidnight()
            }
        }, delay)

        midnightCheckRunning = true
    }

    /**
     * 调度下一个午夜的检查任务
     */
    private fun scheduleNextMidnight() {
        // 每 24 小时精确触发一次 (86400000 毫秒)
        val delay = 24 * 60 * 60 * 1000L

        midnightTimer.schedule(object : TimerTask() {
            override fun run() {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    checkAllOnlinePlayersCache()
                })

                scheduleNextMidnight()
            }
        }, delay)
    }

    /**
     * 检查所有在线玩家的缓存
     */
    private fun checkAllOnlinePlayersCache() {
        val onlinePlayers = plugin.server.onlinePlayers
        if (onlinePlayers.isEmpty()) {
            return
        }

        // 先收集所有在线玩家的公会ID（去重）
        val activeGuildIds = onlinePlayers
            .mapNotNull { plugin.playerGuildCache[it.uniqueId] }
            .distinct()

        // 计算活跃公会的银行利息
        if (activeGuildIds.isNotEmpty()) {
            calculateInterestForActiveGuilds(activeGuildIds)
        }

        onlinePlayers.forEach { player ->
            val guildId = plugin.playerGuildCache[player.uniqueId]
            if (guildId != null && dailyDoneCache.containsKey(player.uniqueId)) {
                // 从数据库重新加载今日已完成的任务
                val completed = plugin.dbManager.getCompletedTaskKeys(guildId, player.uniqueId)
                val oldCache = dailyDoneCache[player.uniqueId]

                // 比较缓存是否需要更新
                if (oldCache != completed) {
                    dailyDoneCache[player.uniqueId] = completed.toMutableSet()
                }

                // 检查是否有 login 类型的任务，如果有则直接触发
                val loginTasks = tasksByType["login"]
                if (loginTasks != null && loginTasks.isNotEmpty()) {
                    // 检查该玩家的 login 任务是否已完成
                    val hasUnfinishedLoginTask = loginTasks.any { task ->
                        !completed.contains(task.key)
                    }

                    if (hasUnfinishedLoginTask) {
                        handleEvent(player, "login")
                    }
                }
            }
        }
    }

    /**
     * 停止午夜缓存检查任务
     */
    fun stopMidnightCheck() {
        if (midnightCheckRunning) {
            midnightTimer.cancel()
            midnightCheckRunning = false
            plugin.logger.info("[Task] 午夜缓存检查已停止")
        }
    }

    /**
     * 停止任务重置检查任务
     */
    fun stopTaskResetCheck() {
        if (taskResetRunning) {
            taskResetTimer.cancel()
            taskResetRunning = false
            plugin.logger.info("[Task] 任务重置检查已停止")
        }
    }

    /**
     * 启动任务重置检查任务
     * 根据配置的 reset_time 在指定时间触发重置
     */
    private fun startTaskResetCheck() {
        if (taskResetRunning) {
            return
        }

        val resetTimeConfig = plugin.config.getString("task.reset_time", "00:00:00") ?: "00:00:00"
        val parts = resetTimeConfig.split(":")
        if (parts.size != 3) {
            plugin.logger.warning("[Task] 无效的任务重置时间配置: $resetTimeConfig，使用默认值 00:00:00")
            return
        }

        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0
        val second = parts[2].toIntOrNull() ?: 0

        val now = java.time.LocalDateTime.now()
        val nextReset = now.toLocalDate().atTime(hour, minute, second)

        val delay = if (nextReset.isAfter(now)) {
            java.time.Duration.between(now, nextReset).toMillis()
        } else {
            java.time.Duration.between(now, nextReset.plusDays(1)).toMillis()
        }

        taskResetTimer.schedule(object : TimerTask() {
            override fun run() {
                // 切换到 Bukkit 主线程执行 Bukkit API
                plugin.server.scheduler.runTask(plugin, Runnable {
                    resetAllDailyTasks()
                })

                // 递归调度下一个重置时间
                scheduleNextTaskReset(hour, minute, second)
            }
        }, delay)

        taskResetRunning = true
        plugin.logger.info("[Task] 任务重置检查已启动，将在 $resetTimeConfig 重置每日任务")
    }

    /**
     * 调度下一个任务重置检查任务
     */
    private fun scheduleNextTaskReset(hour: Int, minute: Int, second: Int) {
        // 每 24 小时精确触发一次 (86400000 毫秒)
        val delay = 24 * 60 * 60 * 1000L

        taskResetTimer.schedule(object : TimerTask() {
            override fun run() {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    resetAllDailyTasks()
                })

                scheduleNextTaskReset(hour, minute, second)
            }
        }, delay)
    }

    /**
     * 重置所有每日任务
     */
    private fun resetAllDailyTasks() {
        val onlinePlayers = plugin.server.onlinePlayers
        if (onlinePlayers.isEmpty()) {
            return
        }

        plugin.logger.info("[Task] 开始重置每日任务...")

        // 收集所有在线玩家的公会ID（去重）
        val activeGuildIds = onlinePlayers
            .mapNotNull { plugin.playerGuildCache[it.uniqueId] }
            .distinct()

        // 清除所有在线玩家的每日任务缓存
        onlinePlayers.forEach { player ->
            val guildId = plugin.playerGuildCache[player.uniqueId]
            if (guildId != null) {
                plugin.dbManager.checkAndResetDailyTasks(guildId, player.uniqueId)
                dailyDoneCache.remove(player.uniqueId)
            }
        }

        // 清除所有公会的全局任务缓存
        activeGuildIds.forEach { guildId ->
            plugin.dbManager.checkAndResetGlobalTasks(guildId)
            guildDoneCache.remove(guildId)
        }

        plugin.logger.info("[Task] 每日任务重置完成")
    }

    /**
     * 计算活跃公会的银行利息
     * @param activeGuildIds 活跃公会ID列表
     */
    private fun calculateInterestForActiveGuilds(activeGuildIds: List<Int>) {
        val today = getTodayDateLong()

        val updatedGuilds = mutableListOf<Int>()

        activeGuildIds.forEach { guildId ->
            val guildData = plugin.dbManager.getGuildData(guildId) ?: return@forEach

            val lastInterestDate = guildData.lastInterestDate

            // 计算距离上次计息的天数
            val daysSinceLastInterest = calculateDaysBetween(lastInterestDate, today)

            if (daysSinceLastInterest > 0) {
                // 获取该等级的利息率
                val interestRate = getInterestRate(guildData.level)
                if (interestRate == null || interestRate <= 0) {

                    return@forEach
                }

                // 复利计算：每天的利息都会累加到本金中
                val rateMultiplier = 1 + (interestRate / 100.0)
                val finalBalance = guildData.balance * rateMultiplier.pow(daysSinceLastInterest.toDouble())
                val totalInterest = ((finalBalance - guildData.balance) * 100).roundToInt() / 100.0

                if (totalInterest > 0) {
                    // 更新公会余额
                    val success = plugin.dbManager.updateGuildBalance(guildId, totalInterest)

                    if (success) {
                        // 记录利息日志
                        plugin.dbManager.logBankTransaction(
                            guildId,
                            "系统",
                            "INTEREST",
                            totalInterest
                        )

                        updatedGuilds.add(guildId)
                    }
                }
            }
        }

        // 批量更新计息日期
        if (updatedGuilds.isNotEmpty()) {
            plugin.dbManager.batchUpdateLastInterestDate(updatedGuilds, today)
        }
    }
    /**
     * @param lastTimestamp 上次时间戳
     * @param currentTimestamp 当前时间戳
     * @return 天数
     */
    private fun calculateDaysBetween(lastTimestamp: Long, currentTimestamp: Long): Int {
        if (lastTimestamp == 0L) return 1 // 首次计息，按1天计算

        val lastDate = java.time.LocalDate.ofEpochDay(lastTimestamp / 86400000)
        val currentDate = java.time.LocalDate.ofEpochDay(currentTimestamp / 86400000)

        return (currentDate.toEpochDay() - lastDate.toEpochDay()).toInt()
    }

    /**
     * 获取今日0点的时间戳
     * @return 时间戳
     */
    private fun getTodayDateLong(): Long {
        val now = java.time.LocalDateTime.now()
        val todayMidnight = now.toLocalDate().atStartOfDay()
        return java.time.ZonedDateTime.of(todayMidnight, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * 获取指定等级的银行利息率
     * @param level 公会等级
     * @return 利息率（百分比），如 0.5 表示 0.5%
     */
    private fun getInterestRate(level: Int): Double? {
        val levelSection = plugin.levelsConfig.getConfigurationSection("levels.$level") ?: return null

        return levelSection.getDouble("bank-interest", 0.0)
    }

    /**
     * 从配置文件加载任务定义
     */
    private fun loadTaskDefinitions() {
        taskDefinitions.clear()
        tasksByType.clear()

        val tasksSection = plugin.tasksConfig.getConfigurationSection("tasks") ?: run {

            return
        }

        var loadedCount = 0
        for (taskKey in tasksSection.getKeys(false)) {
            val taskSection = tasksSection.getConfigurationSection(taskKey) ?: continue
            val eventSection = taskSection.getConfigurationSection("event") ?: continue

            val task = TaskDefinition(
                key = taskKey,
                name = taskSection.getString("name", taskKey) ?: taskKey,
                type = taskSection.getString("type", "global") ?: "global",
                eventType = eventSection.getString("type", "") ?: "",
                target = eventSection.getString("target", "*") ?: "*",
                amount = eventSection.getInt("amount", 1),
                lore = taskSection.getStringList("lore"),
                actions = taskSection.getStringList("actions")
            )

            taskDefinitions[taskKey] = task
            // 将任务按事件类型存入分组 Map
            tasksByType.computeIfAbsent(task.eventType) { mutableListOf() }.add(task)
            loadedCount++
        }
    }

    /**
     * 处理任务事件
     * @param player 触发事件的玩家
     * @param eventType 事件类型
     * @param target 目标值(如实体类型、方块类型等)
     * @param increment 增量值，默认为 1
     */
    fun handleEvent(player: Player, eventType: String, target: String = "*", increment: Int = 1) {
        val tasks = tasksByType[eventType] ?: return
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return



        for (task in tasks) {
            // 基础匹配
            if (task.target != "*" && !task.target.equals(target, ignoreCase = true)) {
                continue
            }

            // 内存检查 (如果内存没有，isTaskDone 会自动去数据库预热)
            val isDone = isTaskDone(guildId, player, task)
            if (isDone) {
                continue
            }



            // 3. 数据库原子更新
            val result = plugin.dbManager.incrementTaskProgress(
                guildId, task.key,
                if (task.type == "daily") player.uniqueId else null,
                increment, task.amount
            )

            if (result != null) {
                // 显示任务进度 BossBar
                if (task.type == "daily" && !result.completed) {
                    // 个人任务 BossBar
                    if (plugin.config.getBoolean("task.daily_boss_bar", true)) {
                        showTaskProgressBossBar(player, task, result.progress, result.target)
                    }
                } else if (task.type == "global" && !result.completed) {
                    // 全局任务 BossBar
                    if (plugin.config.getBoolean("task.global_boss_bar", true)) {
                        showGlobalTaskProgressBossBar(guildId, task, result.progress, result.target)
                    }
                }
            } else {
                plugin.logger.warning("[Task] 任务 ${task.key} 进度更新失败，返回 null")
                return
            }

            if (result.completed) {
                // 移除该任务的 BossBar
                if (task.type == "daily") {
                    // 取消并清理个人任务 BossBar 定时器
                    val oldTaskId = bossBarTimerCache[player.uniqueId]?.get(task.key)
                    if (oldTaskId != null && oldTaskId != -1) {
                        Bukkit.getScheduler().cancelTask(oldTaskId)
                        bossBarTimerCache[player.uniqueId]?.remove(task.key)
                    }
                    // 移除个人任务 BossBar
                    bossBarCache[player.uniqueId]?.get(task.key)?.removeAll()
                    bossBarCache[player.uniqueId]?.remove(task.key)
                } else if (task.type == "global") {
                    // 取消并清理全局任务 BossBar 定时器
                    val oldTaskId = globalBossBarTimerCache[guildId]?.get(task.key)
                    if (oldTaskId != null && oldTaskId != -1) {
                        Bukkit.getScheduler().cancelTask(oldTaskId)
                        globalBossBarTimerCache[guildId]?.remove(task.key)
                    }
                    // 移除全局任务 BossBar
                    globalBossBarCache[guildId]?.get(task.key)?.removeAll()
                    globalBossBarCache[guildId]?.remove(task.key)

                    // 跨服通知移除 BossBar
                    if (plugin.config.getBoolean("proxy", false)) {
                        sendGlobalTaskCompletedToProxy(guildId, task.key, task.name)
                    }
                }

                // 更新内存，确保下次直接从内存返回 true
                if (task.type == "daily") {
                    dailyDoneCache.getOrPut(player.uniqueId) { mutableSetOf() }.add(task.key)
                } else {
                    guildDoneCache.getOrPut(guildId) { mutableSetOf() }.add(task.key)
                }

                // 执行奖励
                executeTaskActions(player, guildId, task)
                notifyTaskCompletion(player, task, guildId)
            }
        }
    }

    /**
     * 显示任务进度 BossBar (个人任务)
     * @param player 玩家
     * @param task 任务定义
     * @param progress 当前进度
     * @param target 目标值
     */
    private fun showTaskProgressBossBar(player: Player, task: TaskDefinition, progress: Int, target: Int) {
        val playerUuid = player.uniqueId
        val taskType = if (task.type == "daily") "每日任务" else "全局任务"

        // 检查是否已有该任务的 BossBar
        val existingBar = bossBarCache.getOrPut(playerUuid) { mutableMapOf() }[task.key]

        if (existingBar != null) {
            // 更新现有 BossBar 的进度
            val barText = plugin.langManager.get("task-progress-bar")
                .replace("%progress%", progress.toString())
                .replace("%target%", target.toString())

            existingBar.setTitle("${plugin.langManager.get("task-progress-title")
                .replace("%task_type%", taskType)
                .replace("%task_name%", task.name)} $barText")

            val progressValue = progress.toDouble() / target.toDouble().coerceAtLeast(1.0)
            existingBar.progress = progressValue.coerceIn(0.0, 1.0)

            // 取消之前的定时器
            val oldTaskId = bossBarTimerCache[playerUuid]?.get(task.key)
            if (oldTaskId != null && oldTaskId != -1) {
                Bukkit.getScheduler().cancelTask(oldTaskId)
            }

            // 创建新的定时器：5秒后移除 BossBar
            val newTaskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currentBar = bossBarCache[playerUuid]?.get(task.key)
                if (currentBar == existingBar) {
                    currentBar.removeAll()
                    bossBarCache[playerUuid]?.remove(task.key)
                    bossBarTimerCache[playerUuid]?.remove(task.key)
                }
            }, 5 * 20L).taskId

            // 保存新的定时器ID
            bossBarTimerCache.getOrPut(playerUuid) { mutableMapOf() }[task.key] = newTaskId
        } else {
            // 创建新的 BossBar
            val title = plugin.langManager.get("task-progress-title")
                .replace("%task_type%", taskType)
                .replace("%task_name%", task.name)

            val barText = plugin.langManager.get("task-progress-bar")
                .replace("%progress%", progress.toString())
                .replace("%target%", target.toString())

            val progressValue = progress.toDouble() / target.toDouble().coerceAtLeast(1.0)

            val bossBar = Bukkit.createBossBar(
                "$title $barText",
                BarColor.GREEN,
                BarStyle.SEGMENTED_10
            )

            bossBar.progress = progressValue.coerceIn(0.0, 1.0)
            bossBar.addPlayer(player)

            // 缓存 BossBar
            bossBarCache[playerUuid]!![task.key] = bossBar

            // 创建定时器：5秒后移除 BossBar
            val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val cachedBar = bossBarCache[playerUuid]?.get(task.key)
                if (cachedBar == bossBar) {
                    bossBar.removeAll()
                    bossBarCache[playerUuid]?.remove(task.key)
                    bossBarTimerCache[playerUuid]?.remove(task.key)
                }
            }, 5 * 20L).taskId

            // 保存定时器ID
            bossBarTimerCache.getOrPut(playerUuid) { mutableMapOf() }[task.key] = taskId
        }
    }

    /**
     * 显示全局任务进度 BossBar
     * @param guildId 公会ID
     * @param task 任务定义
     * @param progress 当前进度
     * @param target 目标值
     */
    private fun showGlobalTaskProgressBossBar(guildId: Int, task: TaskDefinition, progress: Int, target: Int) {
        // 检查是否已有该任务的 BossBar
        val existingBar = globalBossBarCache.getOrPut(guildId) { mutableMapOf() }[task.key]

        val taskType = "全局任务"

        // 获取公会的所有在线玩家
        val guildPlayers = plugin.server.onlinePlayers.filter { player ->
            plugin.playerGuildCache[player.uniqueId] == guildId
        }

        if (existingBar != null) {
            // 更新现有 BossBar 的进度
            val barText = plugin.langManager.get("task-progress-bar")
                .replace("%progress%", progress.toString())
                .replace("%target%", target.toString())

            existingBar.setTitle("${plugin.langManager.get("task-progress-title")
                .replace("%task_type%", taskType)
                .replace("%task_name%", task.name)} $barText")

            val progressValue = progress.toDouble() / target.toDouble().coerceAtLeast(1.0)
            existingBar.progress = progressValue.coerceIn(0.0, 1.0)

            // 如果启用代理模式，发送跨服消息更新进展
            if (plugin.config.getBoolean("proxy", false)) {
                sendGlobalTaskBossBarToProxy(guildId, task.key, task.name, progress, target)
            }

            // 取消之前的定时器
            val oldTaskId = globalBossBarTimerCache[guildId]?.get(task.key)
            if (oldTaskId != null && oldTaskId != -1) {
                Bukkit.getScheduler().cancelTask(oldTaskId)
            }

            // 创建新的定时器：5秒后移除 BossBar
            val newTaskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currentBar = globalBossBarCache[guildId]?.get(task.key)
                if (currentBar == existingBar) {
                    currentBar.removeAll()
                    globalBossBarCache[guildId]?.remove(task.key)
                    globalBossBarTimerCache[guildId]?.remove(task.key)
                }
            }, 5 * 20L).taskId

            // 保存新的定时器ID
            globalBossBarTimerCache.getOrPut(guildId) { mutableMapOf() }[task.key] = newTaskId
        } else {
            // 创建新的 BossBar
            val title = plugin.langManager.get("task-progress-title")
                .replace("%task_type%", taskType)
                .replace("%task_name%", task.name)

            val barText = plugin.langManager.get("task-progress-bar")
                .replace("%progress%", progress.toString())
                .replace("%target%", target.toString())

            val progressValue = progress.toDouble() / target.toDouble().coerceAtLeast(1.0)

            val bossBar = Bukkit.createBossBar(
                "$title $barText",
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
            )

            bossBar.progress = progressValue.coerceIn(0.0, 1.0)

            // 添加所有公会在线玩家到 BossBar
            guildPlayers.forEach { bossBar.addPlayer(it) }

            // 缓存 BossBar
            globalBossBarCache[guildId]!![task.key] = bossBar

            // 创建定时器：5秒后移除 BossBar
            val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val cachedBar = globalBossBarCache[guildId]?.get(task.key)
                if (cachedBar == bossBar) {
                    bossBar.removeAll()
                    globalBossBarCache[guildId]?.remove(task.key)
                    globalBossBarTimerCache[guildId]?.remove(task.key)
                }
            }, 5 * 20L).taskId

            // 保存定时器ID
            globalBossBarTimerCache.getOrPut(guildId) { mutableMapOf() }[task.key] = taskId

            // 如果启用代理模式，发送跨服消息
            if (plugin.config.getBoolean("proxy", false)) {
                sendGlobalTaskBossBarToProxy(guildId, task.key, task.name, progress, target)
            }
        }
    }

    /**
     * 发送全局任务 BossBar 更新到代理端（跨服）
     * @param guildId 公会ID
     * @param taskKey 任务键
     * @param taskName 任务名称
     * @param progress 当前进度
     * @param target 目标值
     */
    private fun sendGlobalTaskBossBarToProxy(guildId: Int, taskKey: String, taskName: String, progress: Int, target: Int) {
        val out = plugin.guildService.createDataOutputForTask()
        out.outputStream.writeUTF("GlobalTaskProgress")
        out.outputStream.writeInt(guildId)
        out.outputStream.writeUTF(taskKey)
        out.outputStream.writeUTF(taskName)
        out.outputStream.writeInt(progress)
        out.outputStream.writeInt(target)

        // 借用第一个在线玩家发包
        plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
    }

    /**
     * 执行任务完成后的动作
     */
    internal fun executeTaskActions(player: Player, guildId: Int, task: TaskDefinition) {
        val guildData = plugin.dbManager.getGuildData(guildId) ?: return

        for (action in task.actions) {
            try {
                // 解析动作指令
                val parts = action.split(":")
                if (parts.size < 2) continue

                val actionType = parts[0].lowercase()
                val command = parts.drop(1).joinToString(":")

                // 替换变量
                var processedCommand = command
                    .replace("{player}", player.name)
                    .replace("{player_name}", player.name)
                    .replace("{player_uuid}", player.uniqueId.toString())
                    .replace("{guild_id}", guildId.toString())
                    .replace("{guild_name}", guildData.name)

                // 处理 PlaceholderAPI 变量
                if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
                    processedCommand = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, processedCommand)
                }

                // 转换为 MenuListener 支持的动作类型
                val menuListenerAction = when (actionType) {
                    "console" -> "console:$processedCommand"
                    "command" -> "command:$processedCommand"
                    "tell" -> "tell:$processedCommand"
                    else -> return
                }

                // 复用 MenuListener 的 executeActionLine 方法
                plugin.menuListener.executeActionLine(player, menuListenerAction)
            } catch (e: Exception) {
                plugin.logger.warning(plugin.langManager.get("task-action-error") + ": $action, ${e.message}")
            }
        }
    }

    /**
     * 通知玩家任务完成
     */
    internal fun notifyTaskCompletion(player: Player, task: TaskDefinition, guildId: Int) {
        // 全局任务：通知公会全体成员
        if (task.type == "global") {
            notifyGuildTaskCompletion(guildId, task)
        } else {
            // 每日任务：只通知当前玩家
            notifyPlayerTaskCompletion(player, task)
        }
    }

    /**
     * 通知玩家任务完成（每日任务）
     */
    private fun notifyPlayerTaskCompletion(player: Player, task: TaskDefinition) {
        try {
            val message = plugin.langManager.get("task-completed")
                .replace("%name%", task.name)
            val msg = MessageUtil.createText(message)
            player.spigot().sendMessage(msg)
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        } catch (e: Exception) {
            plugin.logger.warning(plugin.langManager.get("task-notify-error") + ": ${e.message}")
        }
    }

    /**
     * 通知公会全体成员任务完成（全局任务）
     */
    private fun notifyGuildTaskCompletion(guildId: Int, task: TaskDefinition) {
        val lang = plugin.langManager
        val isProxy = plugin.config.getBoolean("proxy", false)

        if (isProxy) {
            // 跨服模式：发包给代理端转发
            val out = plugin.guildService.createDataOutputForTask()
            out.outputStream.writeUTF("TaskCompleted")
            out.outputStream.writeInt(guildId)
            out.outputStream.writeUTF(task.name)

            // 借用第一个在线玩家发包
            plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
        } else {
            // 单服模式：直接在当前服务器内查找并发送
            plugin.server.onlinePlayers.forEach { p ->
                if (plugin.playerGuildCache[p.uniqueId] == guildId) {
                    val message = lang.get("task-global-completed", "name" to task.name)
                    val msg = MessageUtil.createText(message)
                    p.spigot().sendMessage(msg)
                    p.playSound(p.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
            }
        }
    }

    /**
     * 重新加载任务配置
     */
    fun reload() {
        loadTaskDefinitions()
    }

    /**
     * 根据任务键获取任务定义
     */
    fun getTaskDefinition(taskKey: String): TaskDefinition? {
        return taskDefinitions[taskKey]
    }

    /**
     * 判断并加载完成状态
     */
    private fun isTaskDone(guildId: Int, player: Player, task: TaskDefinition): Boolean {
        val isDaily = task.type == "daily"

        if (isDaily) {
            // 如果内存里没有该玩家的记录，去数据库查一次（预热）
            if (!dailyDoneCache.containsKey(player.uniqueId)) {
                val completed = plugin.dbManager.getCompletedTaskKeys(guildId, player.uniqueId)
                dailyDoneCache[player.uniqueId] = completed.toMutableSet()
            }
            return dailyDoneCache[player.uniqueId]?.contains(task.key) ?: false
        } else {
            // 如果内存里没有该公会的记录，去数据库查一次（预热）
            if (!guildDoneCache.containsKey(guildId)) {
                val completed = plugin.dbManager.getCompletedTaskKeys(guildId, null)
                guildDoneCache[guildId] = completed.toMutableSet()
            }
            return guildDoneCache[guildId]?.contains(task.key) ?: false
        }
    }

    /**
     * 清除玩家过期的每日任务缓存（新的一天登录时调用）
     */
    fun clearExpiredDailyCache(player: Player) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return

        // 从数据库重新加载今日已完成的任务
        val completed = plugin.dbManager.getCompletedTaskKeys(guildId, player.uniqueId)
        // 更新缓存（会自动清除昨天的任务）
        dailyDoneCache[player.uniqueId] = completed.toMutableSet()
    }

    /**
     * 清除玩家的每日任务缓存（玩家退出游戏时调用）
     */
    fun clearDailyCache(playerUuid: UUID) {
        dailyDoneCache.remove(playerUuid)

        // 清理该玩家的 BossBar 定时器
        val playerTimers = bossBarTimerCache.remove(playerUuid)
        playerTimers?.values?.forEach { taskId ->
            if (taskId != -1) {
                Bukkit.getScheduler().cancelTask(taskId)
            }
        }

        // 清理该玩家的 BossBar 缓存
        val playerBars = bossBarCache.remove(playerUuid)
        playerBars?.values?.forEach { it.removeAll() }
    }

    /**
     * 更新跨服全局任务 BossBar (接收自其他服务器)
     * @param guildId 公会ID
     * @param taskKey 任务键
     * @param taskName 任务名称
     * @param progress 当前进度
     * @param target 目标值
     */
    fun updateCrossServerGlobalBossBar(guildId: Int, taskKey: String, taskName: String, progress: Int, target: Int) {
        val taskType = "全局任务"

        // 检查是否已有该任务的 BossBar
        val existingBar = globalBossBarCache.getOrPut(guildId) { mutableMapOf() }[taskKey]

        if (existingBar != null) {
            // 更新现有 BossBar 的进度
            val barText = plugin.langManager.get("task-progress-bar")
                .replace("%progress%", progress.toString())
                .replace("%target%", target.toString())

            existingBar.setTitle("§b${taskType}: §f${taskName} $barText")

            val progressValue = progress.toDouble() / target.toDouble().coerceAtLeast(1.0)
            existingBar.progress = progressValue.coerceIn(0.0, 1.0)

            // 取消之前的定时器
            val oldTaskId = globalBossBarTimerCache[guildId]?.get(taskKey)
            if (oldTaskId != null && oldTaskId != -1) {
                Bukkit.getScheduler().cancelTask(oldTaskId)
            }

            // 创建新的定时器：5秒后移除 BossBar
            val newTaskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currentBar = globalBossBarCache[guildId]?.get(taskKey)
                if (currentBar == existingBar) {
                    currentBar.removeAll()
                    globalBossBarCache[guildId]?.remove(taskKey)
                    globalBossBarTimerCache[guildId]?.remove(taskKey)
                }
            }, 5 * 20L).taskId

            // 保存新的定时器ID
            globalBossBarTimerCache.getOrPut(guildId) { mutableMapOf() }[taskKey] = newTaskId
        } else {
            // 创建新的 BossBar
            val barText = plugin.langManager.get("task-progress-bar")
                .replace("%progress%", progress.toString())
                .replace("%target%", target.toString())

            val progressValue = progress.toDouble() / target.toDouble().coerceAtLeast(1.0)

            val bossBar = Bukkit.createBossBar(
                "§b${taskType}: §f${taskName} $barText",
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
            )

            bossBar.progress = progressValue.coerceIn(0.0, 1.0)

            // 添加本服该公会的所有在线玩家到 BossBar
            plugin.server.onlinePlayers.forEach { player ->
                val cachedGuildId = plugin.playerGuildCache[player.uniqueId]
                if (cachedGuildId == guildId) {
                    bossBar.addPlayer(player)
                }
            }

            // 缓存 BossBar
            globalBossBarCache[guildId]!![taskKey] = bossBar

            // 创建定时器：5秒后移除 BossBar
            val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val cachedBar = globalBossBarCache[guildId]?.get(taskKey)
                if (cachedBar == bossBar) {
                    bossBar.removeAll()
                    globalBossBarCache[guildId]?.remove(taskKey)
                    globalBossBarTimerCache[guildId]?.remove(taskKey)
                }
            }, 5 * 20L).taskId

            // 保存定时器ID
            globalBossBarTimerCache.getOrPut(guildId) { mutableMapOf() }[taskKey] = taskId
        }
    }


    /**
     * 移除跨服全局任务 BossBar
     * @param guildId 公会ID
     * @param taskKey 任务键
     */
    fun removeCrossServerGlobalBossBar(guildId: Int, taskKey: String) {
        // 取消并清理定时器
        val oldTaskId = globalBossBarTimerCache[guildId]?.get(taskKey)
        if (oldTaskId != null && oldTaskId != -1) {
            Bukkit.getScheduler().cancelTask(oldTaskId)
            globalBossBarTimerCache[guildId]?.remove(taskKey)
        }

        // 移除 BossBar
        globalBossBarCache[guildId]?.get(taskKey)?.removeAll()
        globalBossBarCache[guildId]?.remove(taskKey)
    }

    /**
     * 发送全局任务完成消息到代理端（跨服）
     * @param guildId 公会ID
     * @param taskKey 任务键
     * @param taskName 任务名称
     */
    private fun sendGlobalTaskCompletedToProxy(guildId: Int, taskKey: String, taskName: String) {
        val out = plugin.guildService.createDataOutputForTask()
        out.outputStream.writeUTF("GlobalTaskCompleted")
        out.outputStream.writeInt(guildId)
        out.outputStream.writeUTF(taskKey)
        out.outputStream.writeUTF(taskName)

        // 借用第一个在线玩家发包
        plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
    }

    /**
     * 获取所有加载的任务定义
     */
    fun getAllTaskDefinitions(): Map<String, TaskDefinition> {
        return taskDefinitions
    }
}
