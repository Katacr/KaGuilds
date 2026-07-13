package org.katacr.kaguilds.command

import org.bukkit.command.CommandSender
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.service.OperationResult

/**
 * 处理公会基础管理类 /kg admin 子命令，包括重命名、删除、信息、转让、踢出和强制加入。
 */
class AdminGuildCommandHandler(private val plugin: KaGuilds) {

    fun handleRename(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "rename")) return
        if (args.size < 4) {
            sender.sendMessage(lang.get("admin-rename-usage"))
            return
        }

        val guildId = args[2].replace("#", "").toIntOrNull()
        if (guildId == null) {
            sender.sendMessage(lang.get("error-invalid-id"))
            return
        }

        val newName = args[3]
        val min = plugin.config.getInt("guild.name-settings.min-length", 2)
        val max = plugin.config.getInt("guild.name-settings.max-length", 10)

        if (newName.length !in min..max) {
            sender.sendMessage(lang.get("create-invalid-length", "min" to min.toString(), "max" to max.toString()))
            return
        }

        if (!newName.matches(Regex(plugin.config.getString("guild.name-settings.regex") ?: ""))) {
            sender.sendMessage(lang.get("create-invalid-name"))
            return
        }

        plugin.guildService.adminRenameGuild(guildId, newName) { result ->
            when (result) {
                is OperationResult.Success ->
                    sender.sendMessage(lang.get("admin-rename-success", "id" to guildId.toString(), "name" to newName))
                is OperationResult.NameAlreadyExists ->
                    sender.sendMessage(lang.get("create-name-exists"))
                is OperationResult.Error ->
                    sender.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleDelete(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "delete")) return
        if (args.size < 3) {
            sender.sendMessage(lang.get("admin-delete-usage"))
            return
        }

        val guildId = args[2].replace("#", "").toIntOrNull()
            ?: return sender.sendMessage(lang.get("error-invalid-id"))

        plugin.guildService.adminDeleteGuild(guildId) { result ->
            when (result) {
                is OperationResult.Success ->
                    sender.sendMessage(lang.get("admin-delete-success", "id" to guildId.toString()))
                is OperationResult.Error ->
                    sender.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleInfo(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "info")) return
        if (args.size < 3) {
            sender.sendMessage(lang.get("admin-info-usage"))
            return
        }

        val guildId = args[2].replace("#", "").toIntOrNull()
            ?: return sender.sendMessage(lang.get("error-invalid-id"))

        plugin.guildService.getAdminGuildInfo(guildId) { result, info ->
            if (result is OperationResult.Success && info != null) {
                val data = info.data
                val coloredList = renderMemberList(info.memberNames, guildId)

                sender.sendMessage(lang.get("info-admin-header", "id" to data.id.toString()))
                sender.sendMessage(lang.get("info-name", "name" to data.name, "id" to data.id.toString()))
                sender.sendMessage(lang.get("info-owner", "owner" to (data.ownerName ?: "未知")))
                sender.sendMessage(lang.get("info-level", "level" to data.level.toString(), "exp" to data.exp.toString()))
                sender.sendMessage(lang.get("info-balance", "balance" to data.balance.toString()))
                sender.sendMessage(
                    lang.get(
                        "info-members",
                        "current" to info.memberNames.size.toString(),
                        "max" to data.maxMembers.toString(),
                        "online" to info.onlineCount.toString()
                    )
                )
                sender.sendMessage(lang.get("info-list", "list" to coloredList))
                sender.sendMessage(lang.get("info-footer"))
            } else if (result is OperationResult.Error) {
                sender.sendMessage(result.message)
            }
        }
    }

    fun handleTransfer(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "transfer")) return
        if (args.size < 4) return sender.sendMessage(lang.get("admin-transfer-usage"))

        val guildId = args[2].replace("#", "").toIntOrNull()
            ?: return sender.sendMessage(lang.get("error-invalid-id"))
        val targetName = args[3]

        plugin.guildService.adminTransferGuild(guildId, targetName) { result ->
            if (result is OperationResult.Success) {
                sender.sendMessage(lang.get("admin-transfer-success", "player" to targetName))
            } else if (result is OperationResult.Error) {
                sender.sendMessage(result.message)
            }
        }
    }

    fun handleKick(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "kick")) return
        if (args.size < 4) return sender.sendMessage(lang.get("admin-kick-usage"))

        val guildId = args[2].replace("#", "").toIntOrNull()
            ?: return sender.sendMessage(lang.get("error-invalid-id"))
        val targetName = args[3]

        plugin.guildService.adminKickMember(guildId, targetName) { result ->
            if (result is OperationResult.Success) {
                sender.sendMessage(lang.get("admin-kick-member-success", "player" to targetName, "id" to guildId.toString()))
            } else if (result is OperationResult.Error) {
                sender.sendMessage(result.message)
            }
        }
    }

    fun handleJoin(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "join")) return
        if (args.size < 4) return sender.sendMessage(lang.get("admin-join-usage"))

        val guildId = args[2].replace("#", "").toIntOrNull()
            ?: return sender.sendMessage(lang.get("error-invalid-id"))
        val targetPlayer = plugin.server.getPlayer(args[3])
            ?: return sender.sendMessage(lang.get("error-player-not-online"))

        plugin.guildService.adminJoinMember(guildId, targetPlayer) { result ->
            if (result is OperationResult.Success) {
                sender.sendMessage(lang.get("admin-join-member-success", "player" to targetPlayer.name, "id" to guildId.toString()))
            } else if (result is OperationResult.Error) {
                sender.sendMessage(result.message)
            }
        }
    }

    private fun renderMemberList(memberNames: List<String>, guildId: Int): String {
        val onlineNames = plugin.server.onlinePlayers
            .filter { plugin.playerGuildCache[it.uniqueId] == guildId }
            .map { it.name }
            .toSet()

        return memberNames.joinToString("§7, ") { name ->
            if (onlineNames.contains(name)) "§a$name" else "§f$name"
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
