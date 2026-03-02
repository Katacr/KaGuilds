package org.katacr.kaguilds.service

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.configuration.ConfigurationSection
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.MessageUtil
import java.text.SimpleDateFormat
import java.util.*

/**
 * 任务管理器
 * 负责管理公会任务系统的配置、进度跟踪和奖励执行
 */
class TaskManager(private val plugin: KaGuilds) {
    private val tasksByType = mutableMapOf<String, MutableList<TaskDefinition>>()
    private val guildCompletedCache = mutableMapOf<Int, MutableSet<String>>()
    private val dailyCompletedCache = mutableMapOf<UUID, MutableSet<String>>()
    private val guildDoneCache = mutableMapOf<Int, MutableSet<String>>()
    private val dailyDoneCache = mutableMapOf<UUID, MutableSet<String>>()
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

    private val taskDefinitions = mutableMapOf<String, TaskDefinition>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    /**
     * 初始化任务管理器
     */
    fun initialize() {
        loadTaskDefinitions()
        plugin.logger.info("任务管理器初始化完成,共加载 ${taskDefinitions.size} 个任务")
    }

    /**
     * 从配置文件加载任务定义
     */
    private fun loadTaskDefinitions() {
        taskDefinitions.clear()
        tasksByType.clear()

        val tasksSection = plugin.tasksConfig.getConfigurationSection("tasks") ?: return

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
        }
    }

    /**
     * 处理任务事件
     * @param player 触发事件的玩家
     * @param eventType 事件类型
     * @param target 目标值(如实体类型、方块类型等)
     */
    fun handleEvent(player: Player, eventType: String, target: String = "*") {
        val tasks = tasksByType[eventType] ?: return
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return

        for (task in tasks) {
            // 1. 基础匹配
            if (task.target != "*" && !task.target.equals(target, ignoreCase = true)) continue

            // 2. 内存检查 (如果内存没有，isTaskDone 会自动去数据库预热)
            if (isTaskDone(guildId, player, task)) continue

            // 3. 数据库原子更新
            val result = plugin.dbManager.incrementTaskProgress(
                guildId, task.key,
                if (task.type == "daily") player.uniqueId else null,
                1, task.amount
            )

            // 4. 处理完成逻辑
            if (result?.completed == true) {
                // 更新内存，确保下次直接从内存返回 true
                if (task.type == "daily") {
                    dailyDoneCache.getOrPut(player.uniqueId) { mutableSetOf() }.add(task.key)
                } else {
                    guildDoneCache.getOrPut(guildId) { mutableSetOf() }.add(task.key)
                }

                // 执行奖励（因为有内存和数据库双重判定，这里绝不会重复触发）
                executeTaskActions(player, guildId, task)
                notifyTaskCompletion(player, task)
            }
        }
    }

    /**
     * 执行任务完成后的动作
     */
    private fun executeTaskActions(player: Player, guildId: Int, task: TaskDefinition) {
        val guildData = plugin.dbManager.getGuildData(guildId) ?: return

        for (action in task.actions) {
            try {
                // 解析动作指令
                val parts = action.split(":")
                if (parts.size < 2) continue

                val actionType = parts[0].lowercase()
                val command = parts.drop(1).joinToString(":")

                // 替换变量
                val processedCommand = command
                    .replace("%player_name%", player.name)
                    .replace("%player_uuid%", player.uniqueId.toString())
                    .replace("%kaguilds_id%", guildId.toString())
                    .replace("%kaguilds_name%", guildData.name)

                when (actionType) {
                    "console" -> {
                        // 控制台执行指令
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
                    }
                    "player" -> {
                        // 玩家执行指令
                        player.performCommand(processedCommand)
                    }
                    "message" -> {
                        // 发送消息给玩家
                        player.sendMessage(processedCommand)
                    }
                    "broadcast" -> {
                        // 广播消息
                        Bukkit.broadcastMessage(processedCommand)
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("执行任务动作时出错: $action, ${e.message}")
            }
        }
    }

    /**
     * 通知玩家任务完成
     */
    private fun notifyTaskCompletion(player: Player, task: TaskDefinition) {
        try {
            val msg = MessageUtil.createText("§a✓ 任务完成: ${task.name}")
            player.spigot().sendMessage(msg)
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        } catch (e: Exception) {
            plugin.logger.warning("发送任务完成通知时出错: ${e.message}")
        }
    }

    /**
     * 获取公会的任务进度信息
     */
    fun getGuildTaskInfo(guildId: Int): List<Pair<TaskDefinition, Boolean>> {
        val (globalProgress, dailyProgress) = plugin.dbManager.getAllGuildTaskProgress(guildId)
        val result = mutableListOf<Pair<TaskDefinition, Boolean>>()

        for ((key, task) in taskDefinitions) {
            val progress = if (task.type == "global") {
                globalProgress[key]
            } else {
                // 每日任务显示任意一个玩家的进度（或显示总数）
                dailyProgress.find { it.taskKey == key }
            }
            val completed = progress?.completed ?: false
            val current = progress?.progress ?: 0
            result.add(task to completed)
        }

        return result
    }

    /**
     * 获取任务进度百分比
     * @param playerUuid 玩家UUID（每日任务需要）
     */
    fun getTaskProgressPercentage(guildId: Int, taskKey: String, playerUuid: UUID? = null): Int {
        val progress = plugin.dbManager.getGuildTaskProgress(guildId, taskKey, playerUuid)
        val task = taskDefinitions[taskKey] ?: return 0

        return if (progress != null && task.amount > 0) {
            (progress.progress * 100 / task.amount).coerceAtMost(100)
        } else {
            0
        }
    }

    /**
     * 重新加载任务配置
     */
    fun reload() {
        loadTaskDefinitions()
        plugin.logger.info("任务配置已重新加载,共 ${taskDefinitions.size} 个任务")
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
     * 获取所有加载的任务定义
     */
    fun getTaskDefinitions(): Map<String, TaskDefinition> {
        return taskDefinitions
    }
}
