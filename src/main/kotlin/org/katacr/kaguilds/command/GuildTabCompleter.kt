package org.katacr.kaguilds.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds

/**
 * 处理 /kg 命令的 Tab 补全。
 */
class GuildTabCompleter(private val plugin: KaGuilds) {
    private val adminActions = listOf(
        "rename", "delete", "info", "bank", "transfer", "kick", "join", "vault",
        "unlockall", "setlevel", "exp", "arena", "open", "task", "contribution", "release"
    )

    fun complete(sender: CommandSender, args: Array<out String>): List<String>? {
        return when (args.size) {
            1 -> {
                val list = mutableListOf(
                    "help", "create", "join", "info", "requests", "accept", "promote",
                    "demote", "leave", "kick", "delete", "chat", "bank", "invite",
                    "settp", "tp", "rename", "buff", "deny", "transfer", "vault",
                    "menu", "seticon", "motd", "upgrade", "pvp"
                )
                if (hasAnyAdminPermission(sender)) {
                    list.addAll(listOf("reload", "admin"))
                }
                if (sender is Player && plugin.guildService.hasPendingAction(sender.uniqueId)) {
                    list.add("confirm")
                }
                filterList(list, args[0])
            }

            2 -> {
                val sub = args[0].lowercase()
                val list = when (sub) {
                    "pvp" -> listOf("start", "accept", "ready", "exit")
                    "admin" -> if (hasAnyAdminPermission(sender)) {
                        getVisibleAdminActions(sender)
                    } else emptyList()
                    "bank" -> listOf("add", "take", "log")
                    "vault" -> (1..9).map { it.toString() }
                    "buff" -> if (sender is Player) getBuffTab(sender) else emptyList()
                    "invite" -> getCrossServerOnlinePlayers()
                    "kick", "promote", "demote", "transfer" -> getGuildMemberNames(sender)
                    "join", "accept", "deny" -> null
                    else -> emptyList()
                }
                filterList(list, args[1])
            }

            3 -> {
                val sub = args[0].lowercase()
                val adminAction = args[1].lowercase()

                if (sub == "admin") {
                    if (!hasAnyAdminPermission(sender)) return emptyList()
                    if (adminAction != "help" && !hasAdminPermission(sender, adminAction)) return emptyList()

                    return when (adminAction) {
                        "help" -> filterList(listOf("1", "2"), args[2])
                        "arena" -> filterList(listOf("setpos", "setspawn", "setkit", "info"), args[2])
                        "unlockall" -> emptyList()
                        "open" -> filterList(plugin.guiMenuFiles, args[2])
                        "release" -> filterList(listOf("CN", "EN"), args[2])
                        "task", "contribution" -> filterList(listOf("<公会ID>"), args[2])
                        else -> filterList(listOf("#"), args[2])
                    }
                }

                if (sub == "pvp" && adminAction == "start") return null
                emptyList()
            }

            4 -> {
                val sub = args[0].lowercase()
                val adminAction = args[1].lowercase()

                if (sub == "admin") {
                    if (!hasAdminPermission(sender, adminAction)) return emptyList()

                    return when (adminAction) {
                        "task" -> {
                            val taskKeys = plugin.taskManager.getAllTaskDefinitions().keys.toList()
                            filterList(taskKeys, args[3])
                        }
                        "contribution" -> {
                            val playerList = getCrossServerOnlinePlayers().toMutableList()
                            playerList.add("-all")
                            filterList(playerList, args[3])
                        }
                        "transfer" -> null
                        "kick" -> null
                        "join" -> null
                        "arena" -> when (args[2].lowercase()) {
                            "setpos" -> filterList(listOf("1", "2"), args[3])
                            "setspawn", "setkit" -> filterList(listOf("red", "blue"), args[3])
                            else -> emptyList()
                        }
                        "bank" -> filterList(listOf("see", "log", "add", "remove", "set"), args[3])
                        "exp" -> filterList(listOf("add", "remove", "set"), args[3])
                        "vault" -> filterList((1..9).map { it.toString() }, args[3])
                        "rename" -> filterList(listOf("<name>"), args[3])
                        "setlevel" -> filterList(listOf("<level>"), args[3])
                        else -> emptyList()
                    }
                }
                emptyList()
            }

            5 -> {
                val sub = args[0].lowercase()
                val adminAction = args[1].lowercase()

                if (sub == "admin") {
                    if (!hasAdminPermission(sender, adminAction)) return emptyList()

                    return when (adminAction) {
                        "task" -> filterList(listOf("see", "reset", "add"), args[4])
                        "contribution" -> filterList(listOf("set", "add", "clear"), args[4])
                        "bank" -> filterList(listOf("<数值>"), args[4])
                        "exp" -> filterList(listOf("<数值>"), args[4])
                        else -> emptyList()
                    }
                }
                emptyList()
            }

            6 -> {
                val sub = args[0].lowercase()
                val adminAction = args[1].lowercase()

                if (sub == "admin") {
                    if (!hasAdminPermission(sender, adminAction)) return emptyList()

                    return when (adminAction) {
                        "bank", "exp" -> filterList(listOf("-s"), args[5])
                        else -> emptyList()
                    }
                }
                emptyList()
            }

            else -> emptyList()
        }
    }

    private fun filterList(list: List<String>?, input: String): List<String>? {
        return list?.filter { it.startsWith(input, ignoreCase = true) }
    }

    /**
     * 根据发送者拥有的管理员细分权限返回可补全的 /kg admin 子命令。
     */
    private fun getVisibleAdminActions(sender: CommandSender): List<String> {
        val list = adminActions.filter { hasAdminPermission(sender, it) }.toMutableList()
        if (list.isNotEmpty() || hasAdminPermission(sender, "help")) {
            list.add(0, "help")
        }
        return list
    }

    /**
     * 判断发送者是否至少拥有一个 /kg admin 子命令权限，用于显示 admin 入口和帮助页。
     */
    private fun hasAnyAdminPermission(sender: CommandSender): Boolean {
        return sender.hasPermission("kaguilds.admin") ||
            sender.hasPermission("kaguilds.admin.help") ||
            adminActions.any { sender.hasPermission("kaguilds.admin.$it") }
    }

    /**
     * 判断发送者是否拥有指定管理员动作权限。
     */
    private fun hasAdminPermission(sender: CommandSender, action: String): Boolean {
        return sender.hasPermission("kaguilds.admin") || sender.hasPermission("kaguilds.admin.$action")
    }

    private fun getCrossServerOnlinePlayers(): List<String> {
        val isProxy = plugin.config.getBoolean("proxy", false)
        return if (isProxy) {
            plugin.crossServerOnlinePlayers.keys.toList()
        } else {
            plugin.server.onlinePlayers.map { it.name }
        }
    }

    private fun getBuffTab(player: Player): List<String> {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return emptyList()
        val level = plugin.dbManager.guildRepository.getGuildData(guildId)?.level ?: 1
        return plugin.levelsConfig.getStringList("levels.$level.use-buff")
    }

    private fun getGuildMemberNames(sender: CommandSender): List<String>? {
        if (sender !is Player) return null
        val guildId = plugin.playerGuildCache[sender.uniqueId] ?: plugin.dbManager.memberRepository.getGuildIdByPlayer(sender.uniqueId)
        if (guildId == null) return null
        return plugin.guildService.getGuildMemberNames(guildId)
    }
}
