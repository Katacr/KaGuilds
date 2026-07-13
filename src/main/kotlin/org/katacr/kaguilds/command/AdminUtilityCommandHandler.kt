package org.katacr.kaguilds.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds

/**
 * 处理若干较薄的 /kg admin 子命令，包括仓库、解锁、等级、经验、打开菜单和释放 GUI 文件。
 */
class AdminUtilityCommandHandler(private val plugin: KaGuilds) {

    fun handleVault(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "vault")) return
        if (args.size < 3) {
            sender.sendMessage(lang.get("admin-vault-usage"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(lang.get("player-only-admin"))
            return
        }

        val guildId = args[2].replace("#", "").toIntOrNull()
            ?: return sender.sendMessage(lang.get("error-invalid-id"))
        val index = if (args.size > 3) args[3].toIntOrNull() ?: 1 else 1

        plugin.guildService.adminOpenVault(sender, guildId, index)
    }

    fun handleUnlockAll(sender: CommandSender) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "unlockall")) return

        plugin.guildService.forceResetAllLocks()
        sender.sendMessage(lang.get("admin-unlockall-success"))
    }

    fun handleSetLevel(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "setlevel")) return

        val targetId = args.getOrNull(2)?.replace("#", "")?.toIntOrNull() ?: return
        val newLevel = args.getOrNull(3)?.toIntOrNull() ?: return

        val levelSection = plugin.levelsConfig.getConfigurationSection("levels.$newLevel")
        if (levelSection == null) {
            sender.sendMessage(lang.get("admin-unknow-level", "level" to newLevel.toString()))
            return
        }

        val maxMembers = levelSection.getInt("max-members")
        val guild = plugin.dbManager.guildRepository.getGuildById(targetId)
        if (plugin.dbManager.guildRepository.updateGuildLevel(targetId, newLevel, maxMembers) && guild != null) {
            sender.sendMessage(lang.get("admin-success-modify-level", "name" to guild.name, "level" to newLevel.toString()))
        }
    }

    fun handleExp(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "exp")) return

        if (args.size < 5) {
            sender.sendMessage(lang.get("admin-exp-usage"))
            return
        }

        val guildId = args[2].replace("#", "").toIntOrNull() ?: return
        val action = args[3].lowercase()
        val amount = args[4].toIntOrNull() ?: return
        val silent = args.drop(5).any { it.equals("-s", ignoreCase = true) }

        plugin.guildService.adminModifyExp(sender, guildId, action, amount, silent)
    }

    fun handleOpen(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "open")) return

        if (args.size < 3) {
            sender.sendMessage(lang.get("admin-open-usage"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(lang.get("player-only"))
            return
        }

        plugin.menuManager.openMenu(sender, args[2])
    }

    fun handleRelease(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "release")) return

        if (args.size < 3) {
            sender.sendMessage(lang.get("admin-release-usage"))
            return
        }

        val langType = args[2].uppercase()
        if (langType != "CN" && langType != "EN") {
            sender.sendMessage(lang.get("admin-release-invalid-type"))
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = plugin.releaseGuiFiles(langType)
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (result.success) {
                    sender.sendMessage(
                        lang.get(
                            "admin-release-success",
                            "type" to langType,
                            "count" to result.count.toString()
                        )
                    )
                } else {
                    sender.sendMessage(
                        lang.get(
                            "admin-release-failed",
                            "error" to (result.error ?: "Unknown error")
                        )
                    )
                }
            })
        })
    }

    private fun checkAdminPermission(sender: CommandSender, action: String? = null): Boolean {
        val lang = plugin.langManager

        if (sender.hasPermission("kaguilds.admin")) return true
        if (action != null && sender.hasPermission("kaguilds.admin.$action")) return true

        sender.sendMessage(lang.get("no-permission"))
        return false
    }
}
