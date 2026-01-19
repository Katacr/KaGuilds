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
                val cost = plugin.config.getDouble("balance.create", 10000.0)
                val econ = plugin.economy

                // 1. 基础检查：经济系统是否可用
                if (econ == null) {
                    sender.sendMessage(lang.get("econ-not-ready"))
                    return true
                }

                // 2. 异步执行数据库预检查
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    try {
                        plugin.dbManager.connection.use { conn ->
                            // 检查玩家是否已有公会
                            val checkMember = conn.prepareStatement("SELECT guild_id FROM guild_members WHERE player_uuid = ?")
                            checkMember.setString(1, sender.uniqueId.toString())
                            if (checkMember.executeQuery().next()) {
                                sender.sendMessage(lang.get("already-in-guild"))
                                return@Runnable
                            }

                            // 检查公会名是否已存在
                            val checkName = conn.prepareStatement("SELECT id FROM guild_data WHERE name = ?")
                            checkName.setString(1, guildName)
                            if (checkName.executeQuery().next()) {
                                sender.sendMessage(lang.get("name-exists"))
                                return@Runnable
                            }

                            // 3. 回到主线程进行金币检查与扣除
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                if (!econ.has(sender, cost)) {
                                    sender.sendMessage(lang.get("create-insufficient-funds", "cost" to cost.toString()))
                                    return@Runnable
                                }

                                val response = econ.withdrawPlayer(sender, cost)
                                if (!response.transactionSuccess()) {
                                    sender.sendMessage("§cError: ${response.errorMessage}")
                                    return@Runnable
                                }

                                // 4. 扣费成功后，再次回到异步线程执行插入操作
                                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                                    try {
                                        val insertGuild = conn.prepareStatement(
                                            "INSERT INTO guild_data (name, owner_uuid, create_time, announcement) VALUES (?, ?, ?, ?)",
                                            java.sql.Statement.RETURN_GENERATED_KEYS
                                        )
                                        insertGuild.setString(1, guildName)
                                        insertGuild.setString(2, sender.uniqueId.toString())
                                        insertGuild.setLong(3, System.currentTimeMillis())
                                        insertGuild.setString(4, "Welcome to $guildName")
                                        insertGuild.executeUpdate()

                                        val res = insertGuild.generatedKeys
                                        if (res.next()) {
                                            val guildId = res.getInt(1)

                                            val insertMember = conn.prepareStatement(
                                                "INSERT INTO guild_members (player_uuid, guild_id, role, join_time) VALUES (?, ?, ?, ?)"
                                            )
                                            insertMember.setString(1, sender.uniqueId.toString())
                                            insertMember.setInt(2, guildId)
                                            insertMember.setString(3, "OWNER")
                                            insertMember.setLong(4, System.currentTimeMillis())
                                            insertMember.executeUpdate()

                                            // 5. 更新本地缓存
                                            plugin.playerGuildCache[sender.uniqueId] = guildId

                                            // 6. 发送成功消息
                                            sender.sendMessage(lang.get("create-pay-success", "cost" to cost.toString()))
                                            sender.sendMessage(lang.get("create-success", "name" to guildName, "id" to guildId.toString()))
                                        }
                                    } catch (e: Exception) {
                                        // 容错：如果数据库写入失败，退款
                                        plugin.server.scheduler.runTask(plugin, Runnable {
                                            econ.depositPlayer(sender, cost)
                                            sender.sendMessage(lang.get("create-failed-refund"))
                                        })
                                        e.printStackTrace()
                                    }
                                })
                            })
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        sender.sendMessage(lang.get("create-failed"))
                    }
                })
                return true
            }
            "invite" -> {
                if (args.size < 2) {
                    sender.sendMessage(lang.get("invite-usage"))
                    return true
                }
                val targetName = args[1]

                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
                    val guildName = plugin.dbManager.getGuildName(guildId) ?: "Unknown"

                    // 1. 权限检查
                    if (!plugin.dbManager.isStaff(sender.uniqueId, guildId)) {
                        sender.sendMessage(lang.get("invite-no-permission"))
                        return@Runnable
                    }

                    // 2. 尝试在本服找人
                    val localTarget = plugin.server.getPlayer(targetName)
                    if (localTarget != null) {
                        // 在本服的操作（之前的逻辑）
                        executeInvite(sender, localTarget.uniqueId, guildId, guildName)
                    } else {
                        // 3. 关键：在本服找不到，发送跨服邀请请求
                        val out = com.google.common.io.ByteStreams.newDataOutput()
                        out.writeUTF("CrossInvite")   // 子频道
                        out.writeUTF(targetName)      // 目标玩家名
                        out.writeInt(guildId)         // 公会 ID
                        out.writeUTF(guildName)       // 公会名
                        out.writeUTF(sender.name)     // 邀请者名字

                        sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
                        sender.sendMessage(lang.get("invite-sent-sender", "name" to targetName))
                    }
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
                            // --- 新增：跨服通知管理层 ---
                            val out = com.google.common.io.ByteStreams.newDataOutput()
                            out.writeUTF("NotifyStaff") // 子频道
                            out.writeInt(guildId)        // 目标公会ID
                            out.writeUTF(sender.name)    // 申请人名字

                            sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                })
            }
            "deny" -> {
                if (args.size < 2) {
                    sender.sendMessage(lang.get("deny-usage"))
                    return true
                }
                val targetName = args[1]
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
                    if (!plugin.dbManager.isStaff(sender.uniqueId, guildId)) {
                        sender.sendMessage(lang.get("not-staff"))
                        return@Runnable
                    }

                    val targetOffline = plugin.server.getOfflinePlayer(targetName)

                    // 在 DatabaseManager 中实现 deleteRequest 方法
                    if (plugin.dbManager.deleteRequest(guildId, targetOffline.uniqueId)) {
                        sender.sendMessage(lang.get("deny-success-sender", "name" to targetName))

                        // 如果被拒绝者在线（无论在哪个服），通知他
                        // 这里我们也可以写一个跨服通知，但简单起见，如果他在本服直接发：
                        targetOffline.player?.sendMessage(lang.get("deny-success-target"))

                        // 进阶：如果你想跨服通知被拒绝者，可以参考 NotifyStaff 的逻辑写一个 NotifyPlayer
                    } else {
                        sender.sendMessage(lang.get("deny-failed"))
                    }
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
                if (args.size < 2) {
                    sender.sendMessage(lang.get("accept-usage"))
                    return true
                }
                // 这个参数可能是：申请人的名字（管理员视角） 或 公会的名字（被邀请人视角）
                val targetOrGuild = args[1]

                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    // --- 逻辑 A：作为“被邀请人”接受公会邀请 ---
                    val invitedGuildId = plugin.inviteCache[sender.uniqueId]
                    if (invitedGuildId != null) {
                        val realGuildName = plugin.dbManager.getGuildName(invitedGuildId)

                        // 校验输入的名称是否匹配邀请的公会名
                        if (targetOrGuild.equals(realGuildName, ignoreCase = true)) {
                            if (plugin.dbManager.addMember(invitedGuildId, sender.uniqueId, "MEMBER")) {
                                // 1. 清理邀请缓存
                                plugin.inviteCache.remove(sender.uniqueId)

                                // 2. 更新本地公会缓存
                                plugin.playerGuildCache[sender.uniqueId] = invitedGuildId

                                // 3. 跨服同步更新全服缓存 (SyncCache)
                                val out = com.google.common.io.ByteStreams.newDataOutput()
                                out.writeUTF("SyncCache")
                                out.writeUTF("UPDATE")
                                out.writeUTF(sender.uniqueId.toString())
                                out.writeInt(invitedGuildId)
                                sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())

                                sender.sendMessage(lang.get("accept-success-target"))
                                return@Runnable
                            }
                        } else {
                            sender.sendMessage(lang.get("invite-name-mismatch"))
                            return@Runnable
                        }
                    }

                    // --- 逻辑 B：作为“管理员”批准他人的入会申请 ---
                    val myGuildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable

                    // 权限检查
                    if (!plugin.dbManager.isStaff(sender.uniqueId, myGuildId)) {
                        sender.sendMessage(lang.get("not-staff"))
                        return@Runnable
                    }

                    val targetOffline = plugin.server.getOfflinePlayer(targetOrGuild)
                    val targetUuid = targetOffline.uniqueId

                    // 检查该玩家是否真的提交过申请
                    if (!plugin.dbManager.hasRequest(myGuildId, targetUuid)) {
                        sender.sendMessage(lang.get("accept-failed")) // 或提示“没有找到该申请”
                        return@Runnable
                    }

                    // 执行批准逻辑（从申请表移动到成员表）
                    if (plugin.dbManager.acceptRequest(myGuildId, targetUuid)) {

                        // 1. 本地缓存更新（如果目标玩家正好在本服）
                        plugin.playerGuildCache[targetUuid] = myGuildId

                        // 2. 跨服同步更新 (告知全服：这个玩家现在有公会了)
                        val out = com.google.common.io.ByteStreams.newDataOutput()
                        out.writeUTF("SyncCache")
                        out.writeUTF("UPDATE")
                        out.writeUTF(targetUuid.toString())
                        out.writeInt(myGuildId)
                        sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())

                        // 3. 反馈消息
                        sender.sendMessage(lang.get("accept-success-sender", "name" to targetOrGuild))
                        targetOffline.player?.sendMessage(lang.get("accept-success-target"))
                    } else {
                        sender.sendMessage(lang.get("accept-failed"))
                    }
                })
            }

            "promote" -> handleRoleChange(sender, args, "ADMIN", lang.get("role-admin"))
            "demote" -> handleRoleChange(sender, args, "MEMBER", lang.get("role-member"))

            "leave" -> {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val role = plugin.dbManager.getPlayerRole(sender.uniqueId)
                    if (role == "OWNER") sender.sendMessage(lang.get("leave-owner-limit"))
                    else if (plugin.dbManager.removeMember(sender.uniqueId)) sender.sendMessage(lang.get("leave-success"))
                })
                if (plugin.dbManager.removeMember(sender.uniqueId)) {plugin.playerGuildCache.remove(sender.uniqueId)

                    // 同步给全服
                    val out = com.google.common.io.ByteStreams.newDataOutput()
                    out.writeUTF("SyncCache")
                    out.writeUTF("REMOVE")
                    out.writeUTF(sender.uniqueId.toString())
                    sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())

                    sender.sendMessage(lang.get("leave-success"))
                }
            }

            "delete" -> {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
                    if (plugin.dbManager.getPlayerRole(sender.uniqueId) != "OWNER") sender.sendMessage(lang.get("delete-only-owner"))
                    else if (plugin.dbManager.deleteGuild(guildId)) {val out = com.google.common.io.ByteStreams.newDataOutput()
                        out.writeUTF("SyncCache")
                        out.writeUTF("CLEAR_GUILD")
                        out.writeInt(guildId)
                        sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())

                        sender.sendMessage(lang.get("delete-success"))
                    }

                })
            }

            "kick" -> {
                if (args.size < 2) {
                    sender.sendMessage(lang.get("kick-usage"))
                    return true
                }
                val targetName = args[1]

                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
                    val myRole = plugin.dbManager.getPlayerRole(sender.uniqueId) ?: return@Runnable

                    // 权限检查
                    if (myRole != "OWNER" && myRole != "ADMIN") {
                        sender.sendMessage(lang.get("not-staff"))
                        return@Runnable
                    }

                    val targetOffline = plugin.server.getOfflinePlayer(targetName)
                    val targetUuid = targetOffline.uniqueId
                    val targetRole = plugin.dbManager.getRoleInGuild(guildId, targetUuid)

                    when {
                        targetRole == null -> sender.sendMessage(lang.get("target-not-member", "name" to targetName))
                        targetRole == "OWNER" -> sender.sendMessage(lang.get("kick-cannot-owner"))
                        myRole == "ADMIN" && targetRole == "ADMIN" -> sender.sendMessage(lang.get("kick-admin-limit"))
                        else -> {
                            // 1. 数据库操作
                            if (plugin.dbManager.removeMember(targetUuid)) {

                                // 2. 本地缓存清理
                                plugin.playerGuildCache.remove(targetUuid)

                                // 3. 跨服同步清理：通过 Plugin Message 通知全服
                                val out = com.google.common.io.ByteStreams.newDataOutput()
                                out.writeUTF("SyncCache") // 子频道
                                out.writeUTF("REMOVE")    // 操作类型
                                out.writeUTF(targetUuid.toString()) // 目标 UUID

                                sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())

                                // 4. 消息反馈
                                sender.sendMessage(lang.get("kick-success-sender", "name" to targetName))
                                targetOffline.player?.sendMessage(lang.get("kick-success-target"))
                            }
                        }
                    }
                })
            }
            "chat", "c" -> {
                if (args.size < 2) {
                    sender.sendMessage(lang.get("chat-usage")) // 别忘了在 lang 加 key
                    return true
                }

                // 获取完整的消息内容
                val message = args.sliceArray(1 until args.size).joinToString(" ")

                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId)
                    if (guildId == null) {
                        sender.sendMessage(lang.get("chat-no-guild"))
                        return@Runnable
                    }

                    // 1. 本服直接显示 (让发送者立即看到)
                    // 注意：这里可以根据需要决定是本服广播还是统一等 Proxy 回传

                    // 2. 打包发给 Velocity
                    val out = com.google.common.io.ByteStreams.newDataOutput()
                    out.writeUTF("Chat")      // 子指令类型
                    out.writeInt(guildId)     // 公会 ID
                    out.writeUTF(sender.name) // 发送者
                    out.writeUTF(message)     // 内容

                    sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
                })
            }
            "bank" -> {
                if (args.size < 2) {
                    sender.sendMessage(lang.get("bank-usage"))
                    return true
                }

                val action = args[1].lowercase()

                // 处理 log 子指令
                if (action == "log") {
                    // 1. 解析页码，默认为第1页
                    val page = if (args.size >= 3) args[2].toIntOrNull() ?: 1 else 1
                    if (page < 1) {
                        sender.sendMessage(lang.get("bank-log-invalid-page"))
                        return true
                    }

                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable

                        // 权限检查：只有管理员能看日志
                        if (!plugin.dbManager.isStaff(sender.uniqueId, guildId)) {
                            sender.sendMessage(lang.get("not-staff"))
                            return@Runnable
                        }

                        val totalPages = plugin.dbManager.getBankLogTotalPages(guildId)

                        // 如果请求页码超过总页数
                        if (page > totalPages) {
                            sender.sendMessage(lang.get("bank-log-invalid-page"))
                            return@Runnable
                        }

                        val logs = plugin.dbManager.getBankLogs(guildId, page)

                        sender.sendMessage(lang.get("bank-log-header", "page" to page.toString(), "total" to totalPages.toString()))
                        if (logs.isEmpty()) {
                            sender.sendMessage(lang.get("bank-log-empty"))
                        } else {
                            logs.forEach { sender.sendMessage(it) }
                            // 提示下一页指令
                            if (page < totalPages) {
                                sender.sendMessage("§7使用 §f/kg bank log ${page + 1} §7查看下一页")
                            }
                        }
                    })
                    return true
                }

                // 处理 add/get 子指令
                if (args.size < 3) {
                    sender.sendMessage(lang.get("bank-usage"))
                    return true
                }

                val amount = args[2].toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage(lang.get("bank-invalid-amount"))
                    return true
                }

                val econ = plugin.economy ?: return true

                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
                    val guildData = plugin.dbManager.getGuildData(guildId) ?: return@Runnable
                    val maxBank = plugin.config.getDouble("level.${guildData.level}.max-money", 50000.0)

                    when (action) {
                        "add" -> {
                            if (guildData.balance + amount > maxBank) {
                                sender.sendMessage(lang.get("bank-full", "max" to maxBank.toString()))
                                return@Runnable
                            }
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                if (!econ.has(sender, amount)) {
                                    sender.sendMessage(lang.get("bank-insufficient-player"))
                                    return@Runnable
                                }
                                econ.withdrawPlayer(sender, amount)
                                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                                    if (plugin.dbManager.updateGuildBalance(guildId, amount)) {
                                        plugin.dbManager.logBankTransaction(guildId, sender.name, "ADD", amount)
                                        sender.sendMessage(lang.get("bank-add-success", "amount" to amount.toString()))
                                    }
                                })
                            })
                        }
                        "get" -> {
                            // 已移除权限检查，所有成员均可取钱
                            if (guildData.balance < amount) {
                                sender.sendMessage(lang.get("bank-insufficient-guild"))
                                return@Runnable
                            }
                            if (plugin.dbManager.updateGuildBalance(guildId, -amount)) {
                                plugin.dbManager.logBankTransaction(guildId, sender.name, "GET", amount)
                                plugin.server.scheduler.runTask(plugin, Runnable {
                                    econ.depositPlayer(sender, amount)
                                    sender.sendMessage(lang.get("bank-get-success", "amount" to amount.toString()))
                                })
                            }
                        }
                    }
                })
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
            when (currentRole) {
                null -> sender.sendMessage(lang.get("target-not-member", "name" to targetName))
                "OWNER" -> sender.sendMessage(lang.get("promote-cannot-self"))
                newRole -> sender.sendMessage(lang.get("already-has-role", "name" to targetName, "role" to roleDisplay))
                else -> {
                    if (plugin.dbManager.updateMemberRole(guildId, targetOffline.uniqueId, newRole)) {
                        sender.sendMessage(lang.get("promote-success", "name" to targetName, "action" to args[0]))
                        targetOffline.player?.sendMessage(lang.get("role-updated-target", "role" to roleDisplay))
                    }
                }
            }
        })
    }
    private fun executeInvite(sender: Player, targetUuid: UUID, guildId: Int, guildName: String) {
        plugin.inviteCache[targetUuid] = guildId
        sender.sendMessage(plugin.langManager.get("invite-sent-sender", "name" to plugin.server.getOfflinePlayer(targetUuid).name!!))
        plugin.server.getPlayer(targetUuid)?.sendMessage(plugin.langManager.get("invite-received-target", "guild" to guildName))

        // 60秒过期
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.inviteCache.remove(targetUuid)
        }, 1200L)
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
        val list = mutableListOf("help", "create", "join", "info", "requests", "accept", "promote", "demote", "leave", "kick", "delete", "chat")
        if (sender.hasPermission("kaguilds.admin")) list.add("reload")
        return if (args.size == 1) list.filter { it.startsWith(args[0], ignoreCase = true) } else null
    }
}