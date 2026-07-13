package org.katacr.kaguilds.command

import org.bukkit.command.CommandSender
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.MessageUtil

/**
 * 处理 /kg admin task 子命令，负责查看、重置和增加公会任务进度。
 */
class AdminTaskCommandHandler(private val plugin: KaGuilds) {

    fun handle(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "task")) {
            return
        }

        if (args.size < 5) {
            sender.sendMessage(lang.get("admin-task-usage"))
            return
        }

        val idStr = args[2].replace("#", "")
        val guildId = idStr.toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-id"))
        val taskKey = args[3]
        val action = args[4].lowercase()

        when (action) {
            "see" -> handleSee(sender, guildId, taskKey)
            "reset" -> handleReset(sender, guildId, taskKey)
            "add" -> handleAdd(sender, args, guildId, taskKey)
            else -> sender.sendMessage(lang.get("admin-task-usage"))
        }
    }

    private fun handleSee(sender: CommandSender, guildId: Int, taskKey: String) {
        val lang = plugin.langManager

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val taskDef = plugin.taskManager.getTaskDefinition(taskKey)

            if (taskDef == null) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage(lang.get("task-not-found", "task_key" to taskKey))
                })
                return@Runnable
            }

            val (globalProgress, dailyProgress) = plugin.dbManager.taskRepository.getAllGuildTaskProgress(guildId)

            plugin.server.scheduler.runTask(plugin, Runnable {
                sender.sendMessage(lang.get("task-detail-header"))
                sender.sendMessage(lang.get("task-detail-key", "key" to taskDef.key))
                sender.sendMessage(lang.get("task-detail-name", "name" to taskDef.name))
                sender.sendMessage(lang.get("task-detail-type", "type" to taskDef.type))
                sender.sendMessage(lang.get("task-detail-event-type", "event_type" to taskDef.eventType))
                sender.sendMessage(lang.get("task-detail-target", "target" to taskDef.target))
                sender.sendMessage(lang.get("task-detail-amount", "amount" to taskDef.amount.toString()))

                if (taskDef.type == "global") {
                    val progress = globalProgress[taskKey]
                    val progressStr = if (progress != null) "§a${progress.progress}" else "§c0"
                    sender.sendMessage(
                        lang.get(
                            "task-detail-progress",
                            "progress" to progressStr,
                            "amount" to taskDef.amount.toString()
                        )
                    )
                    val statusKey = if (progress?.completed == true) {
                        "task-detail-status-completed"
                    } else {
                        "task-detail-status-incomplete"
                    }
                    sender.sendMessage(lang.get(statusKey))
                    val dateStr = if (progress?.lastDate != null) "§a${progress.lastDate}" else "§c无数据"
                    sender.sendMessage(lang.get("task-detail-last-date", "date" to dateStr))
                } else {
                    val playerProgress = dailyProgress.filter { it.taskKey == taskKey }
                    if (playerProgress.isEmpty()) {
                        sender.sendMessage(lang.get("task-detail-no-player-progress"))
                    } else {
                        sender.sendMessage(lang.get("task-detail-player-header"))
                        playerProgress.forEach { progress ->
                            val playerName = progress.playerUuid?.let { plugin.server.getOfflinePlayer(it) }?.name ?: "未知玩家"
                            val statusKey = if (progress.completed) "task-status-completed" else "task-status-incomplete"
                            val status = lang.get(statusKey)
                            sender.sendMessage(
                                lang.get(
                                    "task-detail-player-progress",
                                    "player" to playerName,
                                    "progress" to progress.progress.toString(),
                                    "amount" to taskDef.amount.toString(),
                                    "status" to status
                                )
                            )
                        }
                    }
                }

                sender.sendMessage(lang.get("task-detail-description"))
                taskDef.lore.forEach { lore ->
                    sender.sendMessage("  ${MessageUtil.translateColorCodes(lore)}")
                }

                sender.sendMessage(lang.get("task-detail-rewards"))
                taskDef.actions.forEach { action ->
                    sender.sendMessage("  §f${MessageUtil.translateColorCodes(action)}")
                }
                sender.sendMessage(lang.get("task-detail-footer"))
            })
        })
    }

    private fun handleReset(sender: CommandSender, guildId: Int, taskKey: String) {
        val lang = plugin.langManager

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val taskDef = plugin.taskManager.getTaskDefinition(taskKey)

            if (taskDef == null) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage(lang.get("task-not-found", "task_key" to taskKey))
                })
                return@Runnable
            }

            val success = if (taskDef.type == "global") {
                plugin.dbManager.taskRepository.resetTaskProgress(guildId, taskKey, null)
            } else {
                val (_, dailyProgress) = plugin.dbManager.taskRepository.getAllGuildTaskProgress(guildId)
                val taskPlayerProgress = dailyProgress.filter { it.taskKey == taskKey }
                var allReset = true
                for (progress in taskPlayerProgress) {
                    val resetSuccess = plugin.dbManager.taskRepository.resetTaskProgress(guildId, taskKey, progress.playerUuid)
                    if (!resetSuccess) allReset = false
                }
                if (taskPlayerProgress.isEmpty()) true else allReset
            }

            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    sender.sendMessage(lang.get("admin-task-reset-success", "task_key" to taskKey))
                    if (taskDef.type == "global") {
                        plugin.taskManager.guildDoneCache[guildId]?.remove(taskKey)
                    } else {
                        val playerUuids = plugin.dbManager.memberRepository.getGuildMembers(guildId).map { it.uuid }
                        playerUuids.forEach { uuid ->
                            plugin.taskManager.dailyDoneCache[uuid]?.remove(taskKey)
                        }
                    }
                } else {
                    sender.sendMessage(lang.get("admin-task-reset-failed"))
                }
            })
        })
    }

    private fun handleAdd(sender: CommandSender, args: Array<out String>, guildId: Int, taskKey: String) {
        val lang = plugin.langManager

        if (args.size < 6) {
            sender.sendMessage(lang.get("admin-task-add-require-amount"))
            return
        }

        val amount = args[5].toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-number"))

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val taskDef = plugin.taskManager.getTaskDefinition(taskKey)

            if (taskDef == null) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage(lang.get("task-not-found", "task_key" to taskKey))
                })
                return@Runnable
            }

            val result = plugin.dbManager.taskRepository.incrementTaskProgress(
                guildId,
                taskKey,
                if (taskDef.type == "daily") null else null,
                amount,
                taskDef.amount
            )

            plugin.server.scheduler.runTask(plugin, Runnable {
                if (result != null) {
                    sender.sendMessage(
                        lang.get(
                            "admin-task-add-success",
                            "task_key" to taskKey,
                            "amount" to amount.toString()
                        )
                    )
                    if (result.completed && taskDef.type == "global") {
                        plugin.taskManager.guildDoneCache[guildId]?.add(taskKey)
                    }
                } else {
                    sender.sendMessage(lang.get("admin-task-add-failed"))
                }
            })
        })
    }

    private fun checkAdminPermission(sender: CommandSender, action: String? = null): Boolean {
        val lang = plugin.langManager

        if (sender.hasPermission("kaguilds.admin")) return true
        if (action != null && sender.hasPermission("kaguilds.admin.$action")) return true

        sender.sendMessage(lang.get("no-permission"))
        return false
    }
}
