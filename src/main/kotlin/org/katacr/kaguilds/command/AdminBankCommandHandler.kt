package org.katacr.kaguilds.command

import org.bukkit.command.CommandSender
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.service.OperationResult

/**
 * 处理 /kg admin bank 子命令，负责查看余额、查看日志和修改金库余额。
 */
class AdminBankCommandHandler(private val plugin: KaGuilds) {

    fun handle(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "bank")) {
            return
        }

        if (args.size < 4) {
            sender.sendMessage(lang.get("admin-bank-usage"))
            return
        }

        val guildId = args[2].replace("#", "").toIntOrNull()
            ?: return sender.sendMessage(lang.get("error-invalid-id"))

        when (val action = args[3].lowercase()) {
            "see" -> handleSee(sender, guildId)
            "log" -> handleLog(sender, args, guildId)
            "add", "remove", "set" -> handleModify(sender, args, guildId, action)
        }
    }

    private fun handleSee(sender: CommandSender, guildId: Int) {
        val lang = plugin.langManager

        plugin.guildService.getAdminGuildInfo(guildId) { _, info ->
            val balance = info?.data?.balance ?: 0.0
            sender.sendMessage(
                lang.get(
                    "admin-bank-see",
                    "id" to guildId.toString(),
                    "balance" to balance.toString()
                )
            )
        }
    }

    private fun handleLog(sender: CommandSender, args: Array<out String>, guildId: Int) {
        val page = if (args.size >= 5) args[4].toIntOrNull() ?: 1 else 1

        plugin.guildService.getAdminBankLogs(guildId, page) { logs ->
            val lang = plugin.langManager

            sender.sendMessage(
                lang.get(
                    "admin-bank-log-header",
                    "id" to guildId.toString(),
                    "page" to page.toString()
                )
            )

            if (logs.isEmpty()) {
                sender.sendMessage(lang.get("admin-bank-no-log"))
            } else {
                logs.forEach { sender.sendMessage(it) }
                sender.sendMessage(lang.get("admin-bank-log-footer", "page" to (page + 1).toString(), "id" to guildId.toString()))
            }
        }
    }

    private fun handleModify(sender: CommandSender, args: Array<out String>, guildId: Int, action: String) {
        val lang = plugin.langManager

        if (args.size < 5) {
            sender.sendMessage(lang.get("admin-bank-amount-required"))
            return
        }

        val amount = args[4].toDoubleOrNull() ?: return sender.sendMessage(lang.get("error-invalid-number"))
        val silent = args.drop(5).any { it.equals("-s", ignoreCase = true) }

        plugin.guildService.adminManageBank(guildId, action, amount) { result, newBalance ->
            if (result is OperationResult.Success) {
                if (silent) return@adminManageBank

                sender.sendMessage(
                    lang.get(
                        "admin-bank-success",
                        "id" to guildId.toString(),
                        "balance" to newBalance.toString()
                    )
                )
            } else if (result is OperationResult.Error) {
                sender.sendMessage(result.message)
            }
        }
    }

    private fun checkAdminPermission(sender: CommandSender, action: String? = null): Boolean {
        val lang = plugin.langManager

        if (sender.hasPermission("kaguilds.admin")) return true
        if (action != null && sender.hasPermission("kaguilds.admin.$action")) return true

        sender.sendMessage(lang.get("no-permission"))
        return false
    }
}
