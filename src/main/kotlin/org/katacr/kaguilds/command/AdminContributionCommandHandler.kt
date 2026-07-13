package org.katacr.kaguilds.command

import org.bukkit.command.CommandSender
import org.katacr.kaguilds.KaGuilds

/**
 * 处理 /kg admin contribution 子命令，负责单人或全员贡献度设置、增加和清零。
 */
class AdminContributionCommandHandler(private val plugin: KaGuilds) {

    fun handle(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "contribution")) {
            return
        }

        if (args.size < 5) {
            sender.sendMessage(lang.get("admin-contribution-usage"))
            return
        }

        val idStr = args[2].replace("#", "")
        val guildId = idStr.toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-id"))
        val targetName = args[3]
        val action = args[4].lowercase()

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            if (targetName == "-all") {
                handleAllMembers(sender, args, guildId, action)
            } else {
                handleSingleMember(sender, args, guildId, targetName, action)
            }
        })
    }

    private fun handleAllMembers(sender: CommandSender, args: Array<out String>, guildId: Int, action: String) {
        val lang = plugin.langManager
        val members = plugin.dbManager.memberRepository.getGuildMembers(guildId)

        if (members.isEmpty()) {
            sender.sendMessage(lang.get("error-not-has-guild"))
            return
        }

        when (action) {
            "set" -> {
                val amount = parseNonNegativeAmount(sender, args, "admin-contribution-set-usage") ?: return
                var successCount = 0
                members.forEach { member ->
                    if (plugin.dbManager.memberRepository.setContribution(member.uuid, amount)) {
                        successCount++
                    }
                }

                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage(
                        lang.get(
                            "admin-contribution-set-all-success",
                            "count" to successCount.toString(),
                            "total" to members.size.toString(),
                            "amount" to amount.toString()
                        )
                    )
                })
            }

            "add" -> {
                val amount = parseNonNegativeAmount(sender, args, "admin-contribution-add-usage") ?: return
                var successCount = 0
                members.forEach { member ->
                    if (plugin.dbManager.memberRepository.addContribution(member.uuid, amount)) {
                        successCount++
                    }
                }

                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage(
                        lang.get(
                            "admin-contribution-add-all-success",
                            "count" to successCount.toString(),
                            "total" to members.size.toString(),
                            "amount" to amount.toString()
                        )
                    )
                })
            }

            "clear" -> {
                var successCount = 0
                members.forEach { member ->
                    if (plugin.dbManager.memberRepository.setContribution(member.uuid, 0)) {
                        successCount++
                    }
                }

                plugin.server.scheduler.runTask(plugin, Runnable {
                    sender.sendMessage(
                        lang.get(
                            "admin-contribution-clear-all-success",
                            "count" to successCount.toString(),
                            "total" to members.size.toString()
                        )
                    )
                })
            }

            else -> sender.sendMessage(lang.get("admin-contribution-usage"))
        }
    }

    private fun handleSingleMember(
        sender: CommandSender,
        args: Array<out String>,
        guildId: Int,
        targetName: String,
        action: String
    ) {
        val lang = plugin.langManager
        val targetUuid = plugin.dbManager.memberRepository.getPlayerUuidByName(targetName)

        if (targetUuid == null) {
            sender.sendMessage(lang.get("error-player-not-found"))
            return
        }

        val playerGuildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(targetUuid)
        if (playerGuildId != guildId) {
            sender.sendMessage(lang.get("admin-contribution-not-in-guild", "player" to targetName, "id" to guildId.toString()))
            return
        }

        when (action) {
            "set" -> {
                val amount = parseNonNegativeAmount(sender, args, "admin-contribution-set-usage") ?: return
                val success = plugin.dbManager.memberRepository.setContribution(targetUuid, amount)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (success) {
                        sender.sendMessage(lang.get("admin-contribution-set-success", "player" to targetName, "amount" to amount.toString()))
                    } else {
                        sender.sendMessage(lang.get("admin-contribution-failed"))
                    }
                })
            }

            "add" -> {
                val amount = parseNonNegativeAmount(sender, args, "admin-contribution-add-usage") ?: return
                val success = plugin.dbManager.memberRepository.addContribution(targetUuid, amount)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (success) {
                        sender.sendMessage(lang.get("admin-contribution-add-success", "player" to targetName, "amount" to amount.toString()))
                    } else {
                        sender.sendMessage(lang.get("admin-contribution-failed"))
                    }
                })
            }

            "clear" -> {
                val success = plugin.dbManager.memberRepository.setContribution(targetUuid, 0)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (success) {
                        sender.sendMessage(lang.get("admin-contribution-clear-success", "player" to targetName))
                    } else {
                        sender.sendMessage(lang.get("admin-contribution-failed"))
                    }
                })
            }

            else -> sender.sendMessage(lang.get("admin-contribution-usage"))
        }
    }

    private fun parseNonNegativeAmount(sender: CommandSender, args: Array<out String>, usageKey: String): Int? {
        val lang = plugin.langManager

        if (args.size < 6) {
            sender.sendMessage(lang.get(usageKey))
            return null
        }

        val amount = args[5].toIntOrNull()
        if (amount == null) {
            sender.sendMessage(lang.get("error-invalid-number"))
            return null
        }

        if (amount < 0) {
            sender.sendMessage(lang.get("admin-contribution-negative"))
            return null
        }

        return amount
    }

    private fun checkAdminPermission(sender: CommandSender, action: String? = null): Boolean {
        val lang = plugin.langManager

        if (sender.hasPermission("kaguilds.admin")) return true
        if (action != null && sender.hasPermission("kaguilds.admin.$action")) return true

        sender.sendMessage(lang.get("no-permission"))
        return false
    }
}
