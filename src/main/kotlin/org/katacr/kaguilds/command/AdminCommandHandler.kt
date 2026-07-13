package org.katacr.kaguilds.command

import org.bukkit.command.CommandSender
import org.katacr.kaguilds.KaGuilds
import kotlin.math.ceil
import kotlin.math.min

/**
 * 管理员命令总入口，负责分发 /kg admin 的各个子命令到对应处理器。
 */
class AdminCommandHandler(private val plugin: KaGuilds) {
    private val adminArenaCommandHandler = AdminArenaCommandHandler(plugin)
    private val adminBankCommandHandler = AdminBankCommandHandler(plugin)
    private val adminContributionCommandHandler = AdminContributionCommandHandler(plugin)
    private val adminGuildCommandHandler = AdminGuildCommandHandler(plugin)
    private val adminTaskCommandHandler = AdminTaskCommandHandler(plugin)
    private val adminUtilityCommandHandler = AdminUtilityCommandHandler(plugin)
    private val adminHelpEntries = listOf(
        AdminHelpEntry("help [page]", "admin-desc-help", "help"),
        AdminHelpEntry("info #ID", "admin-desc-info", "info"),
        AdminHelpEntry("rename #ID <name>", "admin-desc-rename", "rename"),
        AdminHelpEntry("delete #ID", "admin-desc-delete", "delete"),
        AdminHelpEntry("bank #ID see", "admin-desc-bank-see", "bank"),
        AdminHelpEntry("bank #ID log [page]", "admin-desc-bank-log", "bank"),
        AdminHelpEntry("bank #ID <add|remove|set> <amount> [-s]", "admin-desc-bank-modify", "bank"),
        AdminHelpEntry("transfer #ID <player>", "admin-desc-transfer", "transfer"),
        AdminHelpEntry("kick #ID <player>", "admin-desc-kick", "kick"),
        AdminHelpEntry("join #ID <player>", "admin-desc-join", "join"),
        AdminHelpEntry("vault #ID [num]", "admin-desc-vault", "vault"),
        AdminHelpEntry("unlockall", "admin-desc-unlockall", "unlockall"),
        AdminHelpEntry("setlevel #ID <level>", "admin-desc-setlevel", "setlevel"),
        AdminHelpEntry("exp #ID <add|remove|set> <amount> [-s]", "admin-desc-exp", "exp"),
        AdminHelpEntry("open <menu>", "admin-desc-open", "open"),
        AdminHelpEntry("arena <setpos|setspawn|setkit|info>", "admin-desc-arena", "arena"),
        AdminHelpEntry("task #ID <task> <see|reset|add>", "admin-desc-task", "task"),
        AdminHelpEntry("contribution #ID <player|-all> <set|add|clear>", "admin-desc-contribution", "contribution"),
        AdminHelpEntry("release <CN|EN>", "admin-desc-release", "release")
    )

    fun handle(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (args.size < 2) {
            sender.sendMessage(lang.get("admin-usage"))
            return
        }

        when (args[1].lowercase()) {
            "help" -> sendHelp(sender, args.getOrNull(2)?.toIntOrNull() ?: 1)
            "rename" -> adminGuildCommandHandler.handleRename(sender, args)
            "delete" -> adminGuildCommandHandler.handleDelete(sender, args)
            "info" -> adminGuildCommandHandler.handleInfo(sender, args)
            "bank" -> adminBankCommandHandler.handle(sender, args)
            "transfer" -> adminGuildCommandHandler.handleTransfer(sender, args)
            "kick" -> adminGuildCommandHandler.handleKick(sender, args)
            "join" -> adminGuildCommandHandler.handleJoin(sender, args)
            "vault" -> adminUtilityCommandHandler.handleVault(sender, args)
            "unlockall" -> adminUtilityCommandHandler.handleUnlockAll(sender)
            "setlevel" -> adminUtilityCommandHandler.handleSetLevel(sender, args)
            "exp" -> adminUtilityCommandHandler.handleExp(sender, args)
            "open" -> adminUtilityCommandHandler.handleOpen(sender, args)
            "arena" -> adminArenaCommandHandler.handle(sender, args)
            "task" -> adminTaskCommandHandler.handle(sender, args)
            "contribution" -> adminContributionCommandHandler.handle(sender, args)
            "release" -> adminUtilityCommandHandler.handleRelease(sender, args)
            else -> sender.sendMessage(lang.get("admin-usage"))
        }
    }

    private fun sendHelp(sender: CommandSender, page: Int) {
        val lang = plugin.langManager

        val allCmds = adminHelpEntries.filter { hasAdminPermission(sender, it.permission) }

        if (allCmds.isEmpty()) {
            sender.sendMessage(lang.get("no-permission"))
            return
        }

        val pageSize = 10
        val maxPage = ceil(allCmds.size.toDouble() / pageSize).toInt().coerceAtLeast(1)
        val currentPage = when {
            page < 1 -> 1
            page > maxPage -> maxPage
            else -> page
        }

        sender.sendMessage(lang.get("admin-help-header"))

        val start = (currentPage - 1) * pageSize
        val end = min(start + pageSize, allCmds.size)
        for (i in start until end) {
            val entry = allCmds[i]
            sender.sendMessage(" §6/guild admin ${entry.usage} §7- §f${lang.get(entry.descKey)}")
        }

        sender.sendMessage(lang.get("help-footer", "page" to currentPage.toString(), "max" to maxPage.toString()))
        if (currentPage < maxPage) {
            sender.sendMessage(lang.get("admin-help-next-page", "page" to (currentPage + 1).toString()))
        }
    }

    private fun hasAdminPermission(sender: CommandSender, action: String): Boolean {
        return sender.hasPermission("kaguilds.admin") || sender.hasPermission("kaguilds.admin.$action")
    }

    /**
     * 管理员帮助页条目，定义展示用法、语言描述键和对应的细分权限。
     */
    private data class AdminHelpEntry(
        val usage: String,
        val descKey: String,
        val permission: String
    )
}
