package org.katacr.kaguilds.command

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.service.OperationResult

/**
 * 处理公会常用功能命令，包括 Buff、聊天、金库、邀请响应和仓库打开。
 */
class GuildUtilityCommandHandler(private val plugin: KaGuilds) {

    fun handleBuff(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkPermission(player, "buff")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("buff-usage"))
            return
        }

        val buffKey = args[1]
        plugin.guildService.buyBuff(player, buffKey) { result ->
            when (result) {
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleChat(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkPermission(player, "chat")) return

        if (args.size < 2) {
            if (plugin.guildChatPlayers.contains(player.uniqueId)) {
                plugin.guildChatPlayers.remove(player.uniqueId)
                player.sendMessage(lang.get("chat-quit-success"))
                return
            }

            val guildId = plugin.playerGuildCache[player.uniqueId]
                ?: plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)

            if (guildId == null) {
                player.sendMessage(lang.get("error-not-has-guild"))
                return
            }

            plugin.guildChatPlayers.add(player.uniqueId)
            player.sendMessage(lang.get("chat-join-success"))
            return
        }

        val message = args.sliceArray(1 until args.size).joinToString(" ")
        plugin.guildService.sendGuildChat(player, message)
    }

    fun handleBank(player: Player, args: Array<out String>) {
        if (!checkPermission(player, "bank")) return

        plugin.bankService.handleBank(player, args)
    }

    fun handleYes(player: Player) {
        val lang = plugin.langManager

        if (!checkPermission(player, "yes")) return

        plugin.guildService.acceptInvite(player) { result ->
            when (result) {
                is OperationResult.Success -> {
                    player.sendMessage(lang.get("invite-accepted"))
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
                is OperationResult.AlreadyInGuild -> player.sendMessage(lang.get("join-already-in-guild"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleNo(player: Player) {
        val lang = plugin.langManager

        if (!checkPermission(player, "no")) return

        if (!plugin.inviteCache.containsKey(player.uniqueId)) {
            player.sendMessage(lang.get("invite-none"))
            return
        }
        plugin.guildService.declineInvite(player)
        player.sendMessage(lang.get("invite-declined"))
    }

    fun handleVault(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkPermission(player, "vault")) return

        val guildId = plugin.playerGuildCache[player.uniqueId]
        if (guildId == null) {
            player.sendMessage(lang.get("error-no-guild"))
            return
        }

        val index = if (args.size > 1) {
            val input = args[1].toIntOrNull()
            if (input == null || input < 1 || input > 9) {
                player.sendMessage(lang.get("error-invalid-vault-index"))
                return
            }
            input
        } else {
            1
        }

        plugin.guildService.openVault(player, index)
    }

    private fun checkPermission(player: Player, command: String? = null): Boolean {
        val lang = plugin.langManager

        if (player.hasPermission("kaguilds.use")) return true
        if (command != null && player.hasPermission("kaguilds.command.$command")) return true

        player.sendMessage(lang.get("no-permission"))
        return false
    }
}
