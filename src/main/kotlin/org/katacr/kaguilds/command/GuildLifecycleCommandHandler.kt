package org.katacr.kaguilds.command

import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.service.GuildService
import org.katacr.kaguilds.service.OperationResult
import org.katacr.kaguilds.util.MessageUtil

/**
 * 处理公会生命周期相关玩家命令，包括创建、解散和确认流程。
 */
class GuildLifecycleCommandHandler(private val plugin: KaGuilds) {

    fun handleCreate(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)

        if (guildId != null) {
            player.sendMessage(lang.get("already-in-guild"))
            return
        }

        if (!checkPermission(player, "create")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("create-usage"))
            return
        }

        val guildName = args[1]
        val min = plugin.config.getInt("guild.name-settings.min-length", 2)
        val max = plugin.config.getInt("guild.name-settings.max-length", 10)
        val regexStr = plugin.config.getString("guild.name-settings.regex") ?: "^[\\u4e00-\\u9fa5a-zA-Z0-9]+$"

        if (guildName.length !in min..max) {
            player.sendMessage(lang.get("create-invalid-length", "min" to min.toString(), "max" to max.toString()))
            return
        }

        if (!guildName.matches(Regex(regexStr))) {
            player.sendMessage(lang.get("create-invalid-name"))
            return
        }

        plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Create(guildName))

        val cost = plugin.config.getDouble("balance.create", 1000.0)
        player.sendMessage(lang.get("confirm-create", "name" to guildName, "cost" to cost.toString()))
        sendConfirmHint(player)
    }

    fun handleDelete(player: Player) {
        val lang = plugin.langManager
        val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)

        if (!checkPermission(player, "delete")) return

        if (role != "OWNER") {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Delete)
        player.sendMessage(lang.get("confirm-delete"))
        sendConfirmHint(player)
    }

    fun handleConfirm(
        player: Player,
        membershipHandler: GuildMembershipCommandHandler,
        renameHandler: (Player, String) -> Unit
    ) {
        val lang = plugin.langManager

        if (!checkPermission(player, "confirm")) return

        val action = plugin.guildService.consumePendingAction(player.uniqueId) ?: run {
            player.sendMessage(lang.get("confirm-no-pending"))
            return
        }

        when (action) {
            is GuildService.PendingAction.Create -> performCreate(player, action.guildName)
            is GuildService.PendingAction.Delete -> performDelete(player)
            is GuildService.PendingAction.Leave -> membershipHandler.performLeave(player)
            is GuildService.PendingAction.Transfer -> membershipHandler.performTransfer(player, action.targetName)
            is GuildService.PendingAction.Rename -> renameHandler(player, action.newName)
            is GuildService.PendingAction.Kick -> membershipHandler.performKick(player, action.targetName)
        }
    }

    private fun performCreate(player: Player, guildName: String) {
        val lang = plugin.langManager
        plugin.guildService.createGuild(player, guildName) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("create-success", "name" to guildName))
                is OperationResult.NameAlreadyExists -> player.sendMessage(lang.get("create-name-exists"))
                is OperationResult.InsufficientFunds -> player.sendMessage(lang.get("create-insufficient-funds"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    private fun performDelete(player: Player) {
        val lang = plugin.langManager
        plugin.guildService.deleteGuild(player) { result ->
            if (result is OperationResult.Success) {
                player.sendMessage(lang.get("delete-success"))
            } else if (result is OperationResult.Error) {
                player.sendMessage(result.message)
            }
        }
    }

    private fun checkPermission(player: Player, command: String? = null): Boolean {
        val lang = plugin.langManager

        if (player.hasPermission("kaguilds.use")) return true
        if (command != null && player.hasPermission("kaguilds.command.$command")) return true

        player.sendMessage(lang.get("no-permission"))
        return false
    }

    private fun sendConfirmHint(player: Player) {
        val lang = plugin.langManager
        val prefix = TextComponent(lang.get("confirm-hint-prefix"))
        val clickBtn = MessageUtil.createClickableText(
            lang.get("confirm-hint-button"),
            lang.get("confirm-hint-button-hover"),
            "/kg confirm"
        )
        val suffix = TextComponent(lang.get("confirm-hint-suffix"))

        player.spigot().sendMessage(prefix, clickBtn, suffix)
    }
}
