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
        val playerArg = args.getOrNull(6)

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val taskDef = plugin.taskManager.getTaskDefinition(taskKey)

            if (taskDef == null) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage(lang.get("task-not-found", "task_key" to taskKey))
                })
                return@Runnable
            }

            // 每日任务需要指定玩家：解析玩家名 → UUID
            var targetUuid: java.util.UUID? = null
            var targetPlayer: org.bukkit.entity.Player? = null
            if (taskDef.type == "daily") {
                if (playerArg.isNullOrBlank()) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        sender.sendMessage(lang.get("admin-task-add-require-player"))
                    })
                    return@Runnable
                }
                targetUuid = plugin.dbManager.memberRepository.getUuidByPlayerName(playerArg)
                if (targetUuid == null) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        sender.sendMessage(lang.get("task-not-found", "task_key" to taskKey))
                    })
                    return@Runnable
                }
                targetPlayer = plugin.server.getPlayer(targetUuid)
                // 每日任务奖励含玩家占位符，必须能定位到在线玩家才能安全发放，否则拒绝写入以避免"凭证已写但奖励丢失"
                if (targetPlayer == null) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        sender.sendMessage(lang.get("admin-task-add-require-player"))
                    })
                    return@Runnable
                }
            }

            val result = plugin.dbManager.taskRepository.incrementTaskProgress(
                guildId,
                taskKey,
                targetUuid,
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
                    if (result.completed) {
                        if (taskDef.type == "global") {
                            plugin.taskManager.guildDoneCache.getOrPut(guildId) { mutableSetOf() }.add(taskKey)
                        } else {
                            plugin.taskManager.dailyDoneCache.getOrPut(targetUuid!!) { mutableSetOf() }.add(taskKey)
                        }
                        // 关键修复：管理员加进度触发完成时同样执行奖励动作，避免"凭证已写但奖励未发"
                        if (result.rewardGranted) {
                            plugin.taskManager.grantTaskReward(guildId, taskDef, targetPlayer)
                        }
                    }
                } else {
                    sender.sendMessage(lang.get("admin-task-add-failed"))
                }
            })
        })
    }

private fun checkAdminPermission(sender: CommandSender, action: String? = null): Boolean =
        CommandSupport.checkAdminPermission(plugin, sender, action)
}
