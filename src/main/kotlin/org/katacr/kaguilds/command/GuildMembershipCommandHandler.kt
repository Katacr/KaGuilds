package org.katacr.kaguilds.command

import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.service.GuildService
import org.katacr.kaguilds.service.OperationResult
import org.katacr.kaguilds.util.MessageUtil

/**
 * 处理公会成员流转相关玩家命令，包括邀请、申请、审批、退出、踢出和转让。
 */
class GuildMembershipCommandHandler(private val plugin: KaGuilds) {

    fun handleKick(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        if (!checkPermission(player, "kick")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("error-missing-args"))
            player.sendMessage(lang.get("kick-usage"))
            return
        }

        val targetName = args[1]

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val targetUuid = plugin.dbManager.memberRepository.getUuidByPlayerName(targetName)
            if (targetUuid == null) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(lang.get("player-not-found", "player" to targetName))
                })
                return@Runnable
            }

            val targetGuildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(targetUuid)

            plugin.server.scheduler.runTask(plugin, Runnable {
                if (targetGuildId == null) {
                    player.sendMessage(lang.get("player-not-in-guild", "player" to targetName))
                    return@Runnable
                }

                player.sendMessage(lang.get("confirm-kick", "player" to targetName))
                plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Kick(targetName))
                sendConfirmHint(player)
            })
        })
    }

    fun handleLeave(player: Player) {
        val lang = plugin.langManager
        val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)

        if (!checkPermission(player, "leave")) return

        if (role == "OWNER") {
            player.sendMessage(lang.get("owner-cannot-leave"))
            return
        }

        plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Leave)
        player.sendMessage(lang.get("confirm-leave"))
        sendConfirmHint(player)
    }

    fun handleInvite(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        if (!checkPermission(player, "invite")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("error-missing-args"))
            player.sendMessage(lang.get("invite-usage"))
            return
        }

        plugin.guildService.invitePlayer(player, args[1]) { result ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                when (result) {
                    is OperationResult.Success -> {
                        val isProxy = plugin.config.getBoolean("proxy", false)
                        val msgKey = if (isProxy) "invite-success-proxy" else "invite-success"
                        player.sendMessage(lang.get(msgKey, "player" to args[1]))
                    }
                    is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                    is OperationResult.NotInGuild -> player.sendMessage(lang.get("not-in-guild"))
                    is OperationResult.Error -> player.sendMessage(result.message)
                    else -> {}
                }
            })
        }
    }

    fun handleJoin(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        if (!checkPermission(player, "join")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("join-usage"))
            return
        }

        val input = args[1]

        plugin.guildService.requestJoin(player, input) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val displayName = if (input.startsWith("#")) {
                        val id = input.substring(1).toIntOrNull() ?: -1
                        plugin.dbManager.guildRepository.getGuildById(id)?.name ?: input
                    } else {
                        input
                    }

                    player.sendMessage(lang.get("join-success", "guild" to displayName))
                }
                is OperationResult.AlreadyInGuild -> player.sendMessage(lang.get("join-already"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleRequests(player: Player) {
        val lang = plugin.langManager
        if (!checkPermission(player, "requests")) return

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)
                ?: return@Runnable player.sendMessage(lang.get("not-in-guild"))

            val requests = plugin.dbManager.requestRepository.getRequests(guildId)

            player.sendMessage(lang.get("requests-header"))
            if (requests.isEmpty()) {
                player.sendMessage(lang.get("requests-none"))
            } else {
                requests.forEach { (uuid, _) ->
                    val requesterName = plugin.server.getOfflinePlayer(uuid).name ?: "Unknown"
                    val msg = MessageUtil.createText(lang.get("requests-format", "name" to requesterName))
                    val acceptBtn = MessageUtil.createClickableText(
                        text = lang.get("requests-accept-btn"),
                        hoverText = lang.get("requests-accept-btn-hover", "name" to requesterName),
                        command = "/kg accept $requesterName"
                    )
                    val space = MessageUtil.createText(" ")
                    val denyBtn = MessageUtil.createClickableText(
                        text = lang.get("requests-deny-btn"),
                        hoverText = lang.get("requests-deny-btn-hover", "name" to requesterName),
                        command = "/kg deny $requesterName"
                    )

                    msg.addExtra(acceptBtn)
                    msg.addExtra(space)
                    msg.addExtra(denyBtn)

                    player.spigot().sendMessage(msg)
                }
            }
            player.sendMessage(lang.get("requests-footer"))
        })
    }

    fun handleAccept(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        if (!checkPermission(player, "accept")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("error-missing-args"))
            player.sendMessage(lang.get("accept-usage"))
            return
        }

        plugin.guildService.acceptRequest(player, args[1]) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("accept-success", "player" to args[1]))
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleDeny(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        if (!checkPermission(player, "deny")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("error-missing-args"))
            return
        }

        plugin.guildService.denyRequest(player, args[1]) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("deny-success", "player" to args[1]))
                is OperationResult.Error -> player.sendMessage("§c${result.message}")
                else -> {}
            }
        }
    }

    fun handleTransfer(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        if (!checkPermission(player, "transfer")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("transfer-usage"))
            return
        }
        val targetName = args[1]

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)
            if (guildId == null) {
                player.sendMessage(lang.get("not-in-guild"))
                return@Runnable
            }

            val guildData = plugin.dbManager.guildRepository.getGuildData(guildId)
            if (guildData?.ownerUuid != player.uniqueId.toString()) {
                player.sendMessage(lang.get("not-staff"))
                return@Runnable
            }

            if (player.name.equals(targetName, ignoreCase = true)) {
                player.sendMessage(lang.get("error-player-is-yourself"))
                return@Runnable
            }

            plugin.server.scheduler.runTask(plugin, Runnable {
                player.sendMessage(lang.get("transfer-confirm-notice", "player" to targetName))
                plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Transfer(targetName))
                sendConfirmHint(player)
            })
        })
    }

    fun performLeave(player: Player) {
        val lang = plugin.langManager
        plugin.guildService.leaveGuild(player) { result ->
            if (result is OperationResult.Success) {
                player.sendMessage(lang.get("leave-success"))
            } else if (result is OperationResult.Error) {
                player.sendMessage(result.message)
            }
        }
    }

    fun performKick(player: Player, targetName: String) {
        val lang = plugin.langManager
        plugin.guildService.kickMember(player, targetName) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("kick-success-sender", "name" to targetName))
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage("§c${result.message}")
                else -> {}
            }
        }
    }

    fun performTransfer(player: Player, targetName: String) {
        val lang = plugin.langManager

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId) ?: return@Runnable

            plugin.guildService.adminTransferGuild(guildId, targetName) { result ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (result is OperationResult.Success) {
                        player.sendMessage(lang.get("transfer-success", "player" to targetName))
                    } else if (result is OperationResult.Error) {
                        player.sendMessage(result.message)
                    }
                })
            }
        })
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
