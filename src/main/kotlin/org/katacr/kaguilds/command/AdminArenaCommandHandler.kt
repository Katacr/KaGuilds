package org.katacr.kaguilds.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds

/**
 * 处理 /kg admin arena 子命令，负责设置对战区域、出生点、队伍装备和查看配置状态。
 */
class AdminArenaCommandHandler(private val plugin: KaGuilds) {

    fun handle(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkAdminPermission(sender, "arena")) {
            return
        }

        val action = args.getOrNull(2)?.lowercase()
        if (action == null) {
            sender.sendMessage(lang.get("admin-arena-usage"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(lang.get("player-only"))
            return
        }

        val loc = sender.location
        val arena = plugin.arenaManager.arena

        when (action) {
            "setpos" -> {
                when (args.getOrNull(3)) {
                    "1" -> {
                        arena.pos1 = loc
                        sender.sendMessage(lang.get("admin-arena-set-pos1"))
                    }
                    "2" -> {
                        arena.pos2 = loc
                        sender.sendMessage(lang.get("admin-arena-set-pos2"))
                    }
                    else -> {
                        sender.sendMessage(lang.get("admin-arena-setpos-usage"))
                        return
                    }
                }
            }

            "setspawn" -> {
                when (args.getOrNull(3)?.lowercase()) {
                    "red" -> {
                        arena.redSpawn = loc
                        sender.sendMessage(lang.get("admin-arena-set-redspawn"))
                    }
                    "blue" -> {
                        arena.blueSpawn = loc
                        sender.sendMessage(lang.get("admin-arena-set-bluespawn"))
                    }
                    else -> {
                        sender.sendMessage(lang.get("admin-arena-setspawn-usage"))
                        return
                    }
                }
            }

            "setkit" -> {
                val team = args.getOrNull(3)?.lowercase()
                if (team != "red" && team != "blue") {
                    sender.sendMessage(lang.get("admin-arena-setkit-usage"))
                    return
                }

                plugin.arenaManager.saveKit(sender, team)
                val teamDisplay = if (team == "red") {
                    lang.get("arena-pvp-red-team-name")
                } else {
                    lang.get("arena-pvp-blue-team-name")
                }
                sender.sendMessage(lang.get("admin-arena-set-kit", "team" to teamDisplay))
                return
            }

            "info" -> {
                sendInfo(sender)
                return
            }

            else -> {
                sender.sendMessage(lang.get("admin-arena-usage"))
                return
            }
        }

        plugin.arenaManager.saveArena()
    }

    private fun sendInfo(sender: Player) {
        val lang = plugin.langManager
        val arena = plugin.arenaManager.arena
        val set = lang.get("admin-arena-info-set")
        val unset = lang.get("admin-arena-info-unset")

        sender.sendMessage(lang.get("admin-arena-info-header"))
        sender.sendMessage("${lang.get("admin-arena-info-pos1")} ${if (arena.pos1 != null) set else unset}")
        sender.sendMessage("${lang.get("admin-arena-info-pos2")} ${if (arena.pos2 != null) set else unset}")
        sender.sendMessage("${lang.get("admin-arena-info-redspawn")} ${if (arena.redSpawn != null) set else unset}")
        sender.sendMessage("${lang.get("admin-arena-info-bluespawn")} ${if (arena.blueSpawn != null) set else unset}")

        val redKitStatus = if (plugin.arenaManager.redKitContents != null) set else unset
        val blueKitStatus = if (plugin.arenaManager.blueKitContents != null) set else unset
        sender.sendMessage("${lang.get("admin-arena-info-redkit")} $redKitStatus")
        sender.sendMessage("${lang.get("admin-arena-info-bluekit")} $blueKitStatus")
    }

    private fun checkAdminPermission(sender: CommandSender, action: String? = null): Boolean {
        val lang = plugin.langManager

        if (sender.hasPermission("kaguilds.admin")) return true
        if (action != null && sender.hasPermission("kaguilds.admin.$action")) return true

        sender.sendMessage(lang.get("no-permission"))
        return false
    }
}
