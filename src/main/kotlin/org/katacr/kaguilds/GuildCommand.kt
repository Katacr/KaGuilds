package org.katacr.kaguilds

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.katacr.kaguilds.command.AdminCommandHandler
import org.katacr.kaguilds.command.GuildInfoCommandHandler
import org.katacr.kaguilds.command.GuildLifecycleCommandHandler
import org.katacr.kaguilds.command.GuildMembershipCommandHandler
import org.katacr.kaguilds.command.GuildRoleCommandHandler
import org.katacr.kaguilds.command.GuildSettingsCommandHandler
import org.katacr.kaguilds.command.GuildTabCompleter
import org.katacr.kaguilds.command.GuildUtilityCommandHandler
import org.katacr.kaguilds.command.PvpCommandHandler

class GuildCommand(private val plugin: KaGuilds) : CommandExecutor, TabCompleter {
    private val adminCommandHandler = AdminCommandHandler(plugin)
    private val guildInfoCommandHandler = GuildInfoCommandHandler(plugin)
    private val guildLifecycleCommandHandler = GuildLifecycleCommandHandler(plugin)
    private val guildMembershipCommandHandler = GuildMembershipCommandHandler(plugin)
    private val guildRoleCommandHandler = GuildRoleCommandHandler(plugin)
    private val guildSettingsCommandHandler = GuildSettingsCommandHandler(plugin)
    private val guildTabCompleter = GuildTabCompleter(plugin)
    private val guildUtilityCommandHandler = GuildUtilityCommandHandler(plugin)
    private val pvpCommandHandler = PvpCommandHandler(plugin)

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        val lang = plugin.langManager
        if (args.isEmpty()) {
            if (sender is Player) {
                guildInfoCommandHandler.openDefaultMenu(sender)
            } else {
                sender.sendMessage(lang.get("help-hint"))
            }
            return true
        }

        val whiteList = listOf("help", "create", "join", "accept", "requests", "reload", "admin", "confirm", "yes", "no", "menu", "chat")
        val subCommand = args[0].lowercase()

        if (subCommand == "help") {
            val page = args.getOrNull(1)?.toIntOrNull() ?: 1
            if (sender is Player) guildInfoCommandHandler.sendHelp(sender, page) else sender.sendMessage(lang.get("console-help"))
            return true
        }

        if (subCommand == "reload") {
            if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.reload")) {
                sender.sendMessage(lang.get("no-permission"))
                return true
            }
            plugin.reloadPlugin(sender)
            return true
        }

        if (subCommand != "admin" && sender !is Player) {
            sender.sendMessage(lang.get("player-only"))
            return true
        }

        if (!whiteList.contains(subCommand)) {
            val player = sender as Player
            val guildId = plugin.playerGuildCache[player.uniqueId] ?: plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)

            if (guildId == null) {
                player.sendMessage(lang.get("error-not-has-guild"))
                return true
            } else {
                plugin.playerGuildCache[player.uniqueId] = guildId
            }
        }

        when (subCommand) {
            "info" -> guildInfoCommandHandler.handleInfo(sender as Player)
            "create" -> guildLifecycleCommandHandler.handleCreate(sender as Player, args)
            "invite" -> guildMembershipCommandHandler.handleInvite(sender as Player, args)
            "join" -> guildMembershipCommandHandler.handleJoin(sender as Player, args)
            "requests" -> guildMembershipCommandHandler.handleRequests(sender as Player)
            "accept" -> guildMembershipCommandHandler.handleAccept(sender as Player, args)
            "deny" -> guildMembershipCommandHandler.handleDeny(sender as Player, args)
            "promote" -> guildRoleCommandHandler.handlePromote(sender as Player, args)
            "demote" -> guildRoleCommandHandler.handleDemote(sender as Player, args)
            "leave" -> guildMembershipCommandHandler.handleLeave(sender as Player)
            "delete" -> guildLifecycleCommandHandler.handleDelete(sender as Player)
            "kick" -> guildMembershipCommandHandler.handleKick(sender as Player, args)
            "chat" -> guildUtilityCommandHandler.handleChat(sender as Player, args)
            "bank" -> guildUtilityCommandHandler.handleBank(sender as Player, args)
            "yes" -> guildUtilityCommandHandler.handleYes(sender as Player)
            "no" -> guildUtilityCommandHandler.handleNo(sender as Player)
            "settp" -> guildSettingsCommandHandler.handleSetTp(sender as Player)
            "tp" -> guildSettingsCommandHandler.handleTp(sender as Player)
            "rename" -> guildSettingsCommandHandler.handleRename(sender as Player, args)
            "buff" -> guildUtilityCommandHandler.handleBuff(sender as Player, args)
            "confirm" -> guildLifecycleCommandHandler.handleConfirm(
                sender as Player,
                guildMembershipCommandHandler,
                guildSettingsCommandHandler::performRename
            )
            "admin" -> adminCommandHandler.handle(sender, args)
            "transfer" -> guildMembershipCommandHandler.handleTransfer(sender as Player, args)
            "vault" -> guildUtilityCommandHandler.handleVault(sender as Player, args)
            "seticon" -> guildSettingsCommandHandler.handleSetIcon(sender as Player)
            "motd" -> guildSettingsCommandHandler.handleMotd(sender as Player, args)
            "upgrade" -> guildSettingsCommandHandler.handleUpgrade(sender as Player)
            "pvp" -> pvpCommandHandler.handle(sender as Player, args)
            "menu" -> guildInfoCommandHandler.handleMenu(sender as Player)
            else -> sender.sendMessage(lang.get("help-hint"))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, cmd: Command, alias: String, args: Array<out String>): List<String>? {
        return guildTabCompleter.complete(sender, args)
    }
}
