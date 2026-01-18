package org.katacr.kaguilds

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

class GuildCommand(private val plugin: KaGuilds) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        val lang = plugin.langManager

        // 1. 如果没有参数或输入 help
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            if (sender is Player) {
                sendHelp(sender)
            } else {
                sender.sendMessage(lang.get("console-help"))
            }
            return true
        }

        val subCommand = args[0].lowercase()

        // 2. 处理 reload (支持控制台)
        if (subCommand == "reload") {
            if (!sender.hasPermission("kaguilds.admin")) {
                sender.sendMessage(lang.get("no-permission"))
                return true
            }
            plugin.reloadConfig()
            plugin.langManager.load()
            sender.sendMessage(lang.get("reload-success"))
            return true
        }

        // 3. 处理 info (支持控制台查询特定公会)
        if (subCommand == "info") {
            val targetName = if (args.size >= 2) args[1] else null

            if (sender !is Player && targetName == null) {
                sender.sendMessage(lang.get("info-console-usage"))
                return true
            }

            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                try {
                    plugin.dbManager.connection.use { conn ->
                        var finalGuildName = targetName

                        if (finalGuildName == null && sender is Player) {
                            val psJoin = conn.prepareStatement(
                                "SELECT d.name FROM guild_data d JOIN guild_members m ON d.id = m.guild_id WHERE m.player_uuid = ?"
                            )
                            psJoin.setString(1, sender.uniqueId.toString())
                            val rsJoin = psJoin.executeQuery()
                            if (rsJoin.next()) {
                                finalGuildName = rsJoin.getString("name")
                            } else {
                                sender.sendMessage(lang.get("not-in-guild"))
                                return@Runnable
                            }
                        }

                        val ps = conn.prepareStatement("SELECT * FROM guild_data WHERE name = ?")
                        ps.setString(1, finalGuildName)
                        val rs = ps.executeQuery()

                        if (rs.next()) {
                            val id = rs.getInt("id")
                            val ownerUuid = UUID.fromString(rs.getString("owner_uuid"))
                            val ownerName = plugin.server.getOfflinePlayer(ownerUuid).name ?: "Unknown"

                            // info 输出不带前缀
                            sender.sendMessage(lang.get("info-header", "name" to finalGuildName!!, withPrefix = false))
                            sender.sendMessage(lang.get("info-owner", "name" to ownerName, withPrefix = false))
                            sender.sendMessage(lang.get("info-members",
                                "current" to plugin.dbManager.getMemberCount(id, conn).toString(),
                                "max" to rs.getInt("max_members").toString(), withPrefix = false))
                            sender.sendMessage(lang.get("info-level", "level" to rs.getInt("level").toString(), withPrefix = false))
                            sender.sendMessage(lang.get("info-balance", "balance" to rs.getDouble("balance").toString(), withPrefix = false))
                            sender.sendMessage(lang.get("info-announcement", "announcement" to rs.getString("announcement"), withPrefix = false))
                            sender.sendMessage(lang.get("info-footer", withPrefix = false))
                        } else {
                            sender.sendMessage(lang.get("unknown-guild", "name" to finalGuildName!!))
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            })
            return true
        }

        // 4. 拦截其他必须是玩家的指令
        if (sender !is Player) {
            sender.sendMessage(lang.get("player-only"))
            return true
        }

        // 玩家专属指令逻辑
        when (subCommand) {
            "create" -> {
                if (args.size < 2) {
                    sender.sendMessage(lang.get("create-usage"))
                    return true
                }
                val guildName = args[1]
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    try {
                        plugin.dbManager.connection.use { conn ->
                            val checkMember = conn.prepareStatement("SELECT guild_id FROM guild_members WHERE player_uuid = ?")
                            checkMember.setString(1, sender.uniqueId.toString())
                            if (checkMember.executeQuery().next()) {
                                sender.sendMessage(lang.get("already-in-guild"))
                                return@Runnable
                            }
                            val checkName = conn.prepareStatement("SELECT id FROM guild_data WHERE name = ?")
                            checkName.setString(1, guildName)
                            if (checkName.executeQuery().next()) {
                                sender.sendMessage(lang.get("name-exists"))
                                return@Runnable
                            }
                            val insertGuild = conn.prepareStatement(
                                "INSERT INTO guild_data (name, owner_uuid, create_time, announcement, icon) VALUES (?, ?, ?, ?, ?)",
                                java.sql.Statement.RETURN_GENERATED_KEYS
                            )
                            insertGuild.setString(1, guildName)
                            insertGuild.setString(2, sender.uniqueId.toString())
                            insertGuild.setLong(3, System.currentTimeMillis())
                            insertGuild.setString(4, "Welcome to $guildName")
                            insertGuild.setString(5, "SHIELD")
                            insertGuild.executeUpdate()
                            val res = insertGuild.generatedKeys
                            if (res.next()) {
                                val guildId = res.getInt(1)
                                val insertMember = conn.prepareStatement("INSERT INTO guild_members (player_uuid, guild_id, role) VALUES (?, ?, ?)")
                                insertMember.setString(1, sender.uniqueId.toString())
                                insertMember.setInt(2, guildId)
                                insertMember.setString(3, "OWNER")
                                insertMember.executeUpdate()
                                sender.sendMessage(lang.get("create-success", "name" to guildName, "id" to guildId.toString()))
                            }
                        }
                    } catch (e: Exception) { sender.sendMessage(lang.get("create-failed")) }
                })
            }

            "join" -> {
                if (args.size < 2) {
                    sender.sendMessage(lang.get("join-usage"))
                    return true
                }
                val guildName = args[1]
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    try {
                        plugin.dbManager.connection.use { conn ->
                            val psGuild = conn.prepareStatement("SELECT id, max_members FROM guild_data WHERE name = ?")
                            psGuild.setString(1, guildName)
                            val rsGuild = psGuild.executeQuery()
                            if (!rsGuild.next()) {
                                sender.sendMessage(lang.get("join-guild-not-found"))
                                return@Runnable
                            }
                            val guildId = rsGuild.getInt("id")
                            if (plugin.dbManager.getMemberCount(guildId, conn) >= rsGuild.getInt("max_members")) {
                                sender.sendMessage(lang.get("join-full"))
                                return@Runnable
                            }
                            val insertReq = conn.prepareStatement("INSERT INTO guild_requests (player_uuid, guild_id, request_time) VALUES (?, ?, ?)")
                            insertReq.setString(1, sender.uniqueId.toString())
                            insertReq.setInt(2, guildId)
                            insertReq.setLong(3, System.currentTimeMillis())
                            insertReq.executeUpdate()
                            sender.sendMessage(lang.get("join-success"))
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                })
            }

            "requests" -> {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
                    if (!plugin.dbManager.isStaff(sender.uniqueId, guildId)) {
                        sender.sendMessage(lang.get("not-staff"))
                        return@Runnable
                    }
                    val requests = plugin.dbManager.getRequests(guildId)
                    if (requests.isEmpty()) sender.sendMessage(lang.get("requests-none"))
                    else {
                        sender.sendMessage(lang.get("requests-header"))
                        requests.forEach { (uuid, _) ->
                            val name = plugin.server.getOfflinePlayer(uuid).name ?: "Unknown"
                            sender.sendMessage(lang.get("requests-format", "name" to name))
                        }
                    }
                })
            }

            "accept" -> {
                if (args.size < 2) sender.sendMessage(lang.get("accept-usage"))
                else {
                    val targetName = args[1]
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
                        if (!plugin.dbManager.isStaff(sender.uniqueId, guildId)) {
                            sender.sendMessage(lang.get("not-staff"))
                            return@Runnable
                        }
                        val targetUuid = plugin.server.getOfflinePlayer(targetName).uniqueId
                        if (plugin.dbManager.acceptRequest(guildId, targetUuid)) {
                            sender.sendMessage(lang.get("accept-success-sender", "name" to targetName))
                            plugin.server.getPlayer(targetUuid)?.sendMessage(lang.get("accept-success-target"))
                        } else sender.sendMessage(lang.get("accept-failed"))
                    })
                }
            }

            "promote" -> handleRoleChange(sender, args, "ADMIN", lang.get("role-admin"))
            "demote" -> handleRoleChange(sender, args, "MEMBER", lang.get("role-member"))

            "leave" -> {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val role = plugin.dbManager.getPlayerRole(sender.uniqueId)
                    if (role == "OWNER") sender.sendMessage(lang.get("leave-owner-limit"))
                    else if (plugin.dbManager.removeMember(sender.uniqueId)) sender.sendMessage(lang.get("leave-success"))
                })
            }

            "delete" -> {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
                    if (plugin.dbManager.getPlayerRole(sender.uniqueId) != "OWNER") sender.sendMessage(lang.get("delete-only-owner"))
                    else if (plugin.dbManager.deleteGuild(guildId)) sender.sendMessage(lang.get("delete-success"))
                })
            }

            "kick" -> {
                if (args.size < 2) sender.sendMessage(lang.get("kick-usage"))
                else {
                    val targetName = args[1]
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
                        val myRole = plugin.dbManager.getPlayerRole(sender.uniqueId) ?: return@Runnable
                        val targetOffline = plugin.server.getOfflinePlayer(targetName)
                        val targetRole = plugin.dbManager.getRoleInGuild(guildId, targetOffline.uniqueId)
                        if (myRole != "OWNER" && myRole != "ADMIN") sender.sendMessage(lang.get("not-staff"))
                        else if (targetRole == "OWNER") sender.sendMessage(lang.get("kick-cannot-owner"))
                        else if (myRole == "ADMIN" && targetRole == "ADMIN") sender.sendMessage(lang.get("kick-admin-limit"))
                        else if (plugin.dbManager.removeMember(targetOffline.uniqueId)) {
                            sender.sendMessage(lang.get("kick-success-sender", "name" to targetName))
                            targetOffline.player?.sendMessage(lang.get("kick-success-target"))
                        }
                    })
                }
            }
        }
        return true
    }

    private fun handleRoleChange(sender: Player, args: Array<out String>, newRole: String, roleDisplay: String) {
        val lang = plugin.langManager
        if (args.size < 2) {
            sender.sendMessage(lang.get("promote-usage", "cmd" to args[0]))
            return
        }
        val targetName = args[1]
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
            if (plugin.dbManager.getPlayerRole(sender.uniqueId) != "OWNER") {
                sender.sendMessage(lang.get("only-owner-can-manage"))
                return@Runnable
            }
            val targetOffline = plugin.server.getOfflinePlayer(targetName)
            val currentRole = plugin.dbManager.getRoleInGuild(guildId, targetOffline.uniqueId)
            when {
                currentRole == null -> sender.sendMessage(lang.get("target-not-member", "name" to targetName))
                currentRole == "OWNER" -> sender.sendMessage(lang.get("promote-cannot-self"))
                currentRole == newRole -> sender.sendMessage(lang.get("already-has-role", "name" to targetName, "role" to roleDisplay))
                else -> {
                    if (plugin.dbManager.updateMemberRole(guildId, targetOffline.uniqueId, newRole)) {
                        sender.sendMessage(lang.get("promote-success", "name" to targetName, "action" to args[0]))
                        targetOffline.player?.sendMessage(lang.get("role-updated-target", "role" to roleDisplay))
                    }
                }
            }
        })
    }

    private fun sendHelp(sender: Player) {
        val lang = plugin.langManager
        sender.sendMessage(lang.get("help-header", withPrefix = false))
        val cmds = mapOf(
            "create <Name>" to "desc-create", "join <Name>" to "desc-join", "info [Name]" to "desc-info",
            "requests" to "desc-requests", "accept <Player>" to "desc-accept", "promote <Player>" to "desc-promote",
            "demote <Player>" to "desc-demote", "kick <Player>" to "desc-kick", "leave" to "desc-leave", "delete" to "desc-delete"
        )
        cmds.forEach { (u, d) ->
            sender.sendMessage(lang.get("help-format", "usage" to u, "description" to lang.get(d, withPrefix = false), withPrefix = false))
        }
        if (sender.hasPermission("kaguilds.admin")) {
            sender.sendMessage(lang.get("help-format", "usage" to "reload", "description" to lang.get("desc-reload", withPrefix = false), withPrefix = false))
        }
        sender.sendMessage(lang.get("help-footer", withPrefix = false))
    }

    override fun onTabComplete(sender: CommandSender, cmd: Command, alias: String, args: Array<out String>): List<String>? {
        val list = mutableListOf("help", "create", "join", "info", "requests", "accept", "promote", "demote", "leave", "kick", "delete")
        if (sender.hasPermission("kaguilds.admin")) list.add("reload")
        return if (args.size == 1) list.filter { it.startsWith(args[0], ignoreCase = true) } else null
    }
}