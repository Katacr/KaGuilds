package org.katacr.kaguilds.service

import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.MessageUtil
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*

/**
 * 任务管理器
 * 负责管理公会任务系统的配置、进度跟踪和奖励执行
 */
class TaskManager(val plugin: KaGuilds) {
    private val tasksByType = mutableMapOf<String, MutableList<TaskDefinition>>()
    val guildDoneCache = mutableMapOf<Int, MutableSet<String>>()
    val dailyDoneCache = mutableMapOf<UUID, MutableSet<String>>()
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

    /**
     * 初始化任务管理器
     */
    fun initialize() {
        loadTaskDefinitions()
        startMidnightCacheCheck()
    }

    /**
     * 启动午夜缓存检查任务
     * 使用系统时间精确在 0:00 触发
     */
    private fun startMidnightCacheCheck() {
        if (midnightCheckRunning) {
            plugin.logger.warning("[Task] 午夜检查任务已在运行，跳过")
            return
        }

        val now = java.time.LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val delay = java.time.Duration.between(now, nextMidnight).toMillis()

        midnightTimer.schedule(object : TimerTask() {
            override fun run() {
                plugin.logger.info("[Task] 午夜缓存重置触发 (系统时间)")

                // 切换到 Bukkit 主线程执行 Bukkit API
                plugin.server.scheduler.runTask(plugin, Runnable {
                    checkAllOnlinePlayersCache()
                })

                // 递归调度下一个午夜
                scheduleNextMidnight()
            }
        }, delay)

        val delayMinutes = delay / 60000
        plugin.logger.info("[Task] 午夜缓存检查已启动，将在 $delayMinutes 分钟后首次触发")
        midnightCheckRunning = true
    }

    /**
     * 调度下一个午夜的检查任务
     */
    private fun scheduleNextMidnight() {
        // 每 24 小时精确触发一次 (86400000 毫秒)
        val delay = 24 * 60 * 60 * 1000L

        midnightTimer.schedule(object : java.util.TimerTask() {
            override fun run() {
                plugin.logger.info("[Task] 午夜缓存重置触发 (系统时间)")

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
            plugin.logger.fine("[Task] 没有在线玩家，跳过缓存检查")
            return
        }

        plugin.logger.info("[Task] 开始检查 ${onlinePlayers.size} 个在线玩家的缓存...")

        var updatedCount = 0
        var loginTriggeredCount = 0
        onlinePlayers.forEach { player ->
            val guildId = plugin.playerGuildCache[player.uniqueId]
            if (guildId != null && dailyDoneCache.containsKey(player.uniqueId)) {
                // 从数据库重新加载今日已完成的任务
                val completed = plugin.dbManager.getCompletedTaskKeys(guildId, player.uniqueId)
                val oldCache = dailyDoneCache[player.uniqueId]

                // 比较缓存是否需要更新
                if (oldCache != completed) {
                    dailyDoneCache[player.uniqueId] = completed.toMutableSet()
                    updatedCount++
                    plugin.logger.info("[Task] 已更新玩家 ${player.name} 的缓存")
                }

                // 检查是否有 login 类型的任务，如果有则直接触发
                val loginTasks = tasksByType["login"]
                if (loginTasks != null && loginTasks.isNotEmpty()) {
                    // 检查该玩家的 login 任务是否已完成
                    val hasUnfinishedLoginTask = loginTasks.any { task ->
                        !completed.contains(task.key)
                    }

                    if (hasUnfinishedLoginTask) {
                        plugin.logger.info("[Task] 触发玩家 ${player.name} 的 login 任务（午夜跨日自动触发）")
                        handleEvent(player, "login")
                        loginTriggeredCount++
                    }
                }
            }
        }

        plugin.logger.info("[Task] 缓存检查完成，更新了 $updatedCount 个玩家的缓存，触发了 $loginTriggeredCount 个玩家的 login 任务")
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
     * 从配置文件加载任务定义
     */
    private fun loadTaskDefinitions() {
        taskDefinitions.clear()
        tasksByType.clear()

        val tasksSection = plugin.tasksConfig.getConfigurationSection("tasks") ?: run {
            plugin.logger.warning("[Task] 未找到 tasks 配置节点")
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

        plugin.logger.info("[Task] 已加载 $loadedCount 个任务定义")
        plugin.logger.info("[Task] 事件类型分组: ${tasksByType.keys.map { "$it=${tasksByType[it]?.size}" }}")
    }

    /**
     * 处理任务事件
     * @param player 触发事件的玩家
     * @param eventType 事件类型
     * @param target 目标值(如实体类型、方块类型等)
     * @param increment 增量值，默认为 1
     */
    fun handleEvent(player: Player, eventType: String, target: String = "*", increment: Int = 1) {
        val tasks = tasksByType[eventType] ?: run {
            plugin.logger.fine("[Task] 事件类型 $eventType 没有匹配的任务")
            return
        }
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: run {
            plugin.logger.fine("[Task] 玩家 ${player.name} 没有公会，跳过任务处理")
            return
        }

        plugin.logger.info("[Task] 玩家 ${player.name} (公会ID: $guildId) 触发事件: $eventType, 目标: $target, 增量: $increment")

        for (task in tasks) {
            // 1. 基础匹配
            if (task.target != "*" && !task.target.equals(target, ignoreCase = true)) {
                plugin.logger.fine("[Task] 任务 ${task.key} 目标不匹配: 需要 ${task.target}, 实际 $target")
                continue
            }

            // 2. 内存检查 (如果内存没有，isTaskDone 会自动去数据库预热)
            val isDone = isTaskDone(guildId, player, task)
            if (isDone) {
                plugin.logger.fine("[Task] 任务 ${task.key} (${task.name}) 已完成，跳过")
                continue
            }

            plugin.logger.info("[Task] 处理任务 ${task.key} (${task.name}), 类型: ${task.type}, 目标: ${task.amount}")

            // 3. 数据库原子更新
            val result = plugin.dbManager.incrementTaskProgress(
                guildId, task.key,
                if (task.type == "daily") player.uniqueId else null,
                increment, task.amount
            )

            if (result != null) {
                plugin.logger.info("[Task] 任务 ${task.key} 进度更新: ${result.progress}/${result.target}, 完成: ${result.completed}")
            } else {
                plugin.logger.warning("[Task] 任务 ${task.key} 进度更新失败，返回 null")
            }

            // 4. 处理完成逻辑
            if (result?.completed == true) {
                plugin.logger.info("[Task] 任务 ${task.key} (${task.name}) 已完成！")
                // 更新内存，确保下次直接从内存返回 true
                if (task.type == "daily") {
                    dailyDoneCache.getOrPut(player.uniqueId) { mutableSetOf() }.add(task.key)
                    plugin.logger.info("[Task] 已将任务 ${task.key} 添加到玩家 ${player.name} 的每日完成缓存")
                } else {
                    guildDoneCache.getOrPut(guildId) { mutableSetOf() }.add(task.key)
                    plugin.logger.info("[Task] 已将任务 ${task.key} 添加到公会 $guildId 的全局完成缓存")
                }

                // 执行奖励
                executeTaskActions(player, guildId, task)
                notifyTaskCompletion(player, task, guildId)
            }
        }
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
                plugin.logger.fine("[Task] 玩家 ${player.name} 每日任务缓存为空，从数据库加载")
                val completed = plugin.dbManager.getCompletedTaskKeys(guildId, player.uniqueId)
                dailyDoneCache[player.uniqueId] = completed.toMutableSet()
                plugin.logger.fine("[Task] 玩家 ${player.name} 已加载 ${completed.size} 个完成的每日任务")
            }
            return dailyDoneCache[player.uniqueId]?.contains(task.key) ?: false
        } else {
            // 如果内存里没有该公会的记录，去数据库查一次（预热）
            if (!guildDoneCache.containsKey(guildId)) {
                plugin.logger.fine("[Task] 公会 $guildId 全局任务缓存为空，从数据库加载")
                val completed = plugin.dbManager.getCompletedTaskKeys(guildId, null)
                guildDoneCache[guildId] = completed.toMutableSet()
                plugin.logger.fine("[Task] 公会 $guildId 已加载 ${completed.size} 个完成的全局任务")
            }
            return guildDoneCache[guildId]?.contains(task.key) ?: false
        }
    }

    /**
     * 清除玩家过期的每日任务缓存（新的一天登录时调用）
     */
    fun clearExpiredDailyCache(player: Player) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return

        plugin.logger.info("[Task] 玩家 ${player.name} 登录，检查并清除过期的每日任务缓存")

        // 从数据库重新加载今日已完成的任务
        val completed = plugin.dbManager.getCompletedTaskKeys(guildId, player.uniqueId)

        // 更新缓存（会自动清除昨天的任务）
        val oldCache = dailyDoneCache[player.uniqueId]
        dailyDoneCache[player.uniqueId] = completed.toMutableSet()

        if (oldCache != null && oldCache != completed) {
            plugin.logger.info("[Task] 已清除玩家 ${player.name} 的过期每日任务缓存")
            plugin.logger.info("[Task] 旧缓存: ${oldCache.joinToString()}, 新缓存: ${completed.joinToString()}")
        }
    }

    /**
     * 清除玩家的每日任务缓存（玩家退出游戏时调用）
     */
    fun clearDailyCache(playerUuid: UUID) {
        val cache = dailyDoneCache.remove(playerUuid)
        if (cache != null) {
            plugin.logger.info("[Task] 已清除玩家 $playerUuid 的每日任务缓存 (移除 ${cache.size} 个任务)")
        }
    }

    /**
     * 获取所有加载的任务定义
     */
    fun getAllTaskDefinitions(): Map<String, TaskDefinition> {
        return taskDefinitions
    }
}
