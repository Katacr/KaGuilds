package org.katacr.kaguilds.command

import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds

/**
 * 处理公会成员职位调整命令。
 */
class GuildRoleCommandHandler(private val plugin: KaGuilds) {

    fun handlePromote(player: Player, args: Array<out String>) {
        handleRoleChange(player, args, "ADMIN", plugin.langManager.get("role-admin"))
    }

    fun handleDemote(player: Player, args: Array<out String>) {
        handleRoleChange(player, args, "MEMBER", plugin.langManager.get("role-member"))
    }

    private fun handleRoleChange(sender: Player, args: Array<out String>, newRole: String, roleDisplay: String) {
        val lang = plugin.langManager
        val cmd = args[0]

        if (!checkPermission(sender, cmd)) return

        if (args.size < 2) {
            sender.sendMessage(lang.get("promote-usage", "cmd" to cmd))
            return
        }

        val targetName = args[1]
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
            if (plugin.dbManager.memberRepository.getPlayerRole(sender.uniqueId) != "OWNER") {
                sender.sendMessage(lang.get("only-owner-can-manage"))
                return@Runnable
            }

            val targetUuid = plugin.dbManager.memberRepository.getPlayerUuid(targetName)
            if (targetUuid == null) {
                sender.sendMessage(lang.get("target-not-member", "name" to targetName))
                return@Runnable
            }

            val targetOffline = plugin.server.getOfflinePlayer(targetUuid)
            val currentRole = plugin.dbManager.memberRepository.getRoleInGuild(guildId, targetOffline.uniqueId)
            when (currentRole) {
                null -> sender.sendMessage(lang.get("target-not-member", "name" to targetName))
                "OWNER" -> sender.sendMessage(lang.get("promote-cannot-self"))
                newRole -> sender.sendMessage(lang.get("already-has-role", "name" to targetName, "role" to roleDisplay))
                else -> {
                    if (plugin.dbManager.memberRepository.updateMemberRole(guildId, targetOffline.uniqueId, newRole)) {
                        sender.sendMessage(lang.get("promote-success", "name" to targetName, "action" to lang.get("promote-action-$cmd")))
                        targetOffline.player?.sendMessage(lang.get("role-updated-target", "role" to roleDisplay))
                    }
                }
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
}
