package org.katacr.kaguilds.service

import org.katacr.kaguilds.KaGuilds
import org.bukkit.entity.Player
import org.katacr.kaguilds.DatabaseManager

class GuildService(private val plugin: KaGuilds) {
    data class GuildInfo(
        val data: DatabaseManager.GuildData,
        val memberNames: List<String>,
        val onlineCount: Int
    )
    /**
     * 获取公会详细展示信息
     */
    fun getDetailedInfo(player: Player, callback: (OperationResult, GuildInfo?) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 获取玩家所属公会 ID
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
            if (guildId == null) {
                callback(OperationResult.NotInGuild, null)
                return@Runnable
            }

            // 2. 获取公会基础数据
            val data = plugin.dbManager.getGuildData(guildId)
            if (data == null) {
                callback(OperationResult.Error("无法加载公会数据"), null)
                return@Runnable
            }

            // 3. 获取成员名称列表 (逻辑复用我们之前的设计)
            val uuids = plugin.dbManager.getMemberUUIDs(guildId)
            var online = 0
            val names = uuids.map { uuid ->
                val p = plugin.server.getPlayer(uuid)
                if (p != null && p.isOnline) {
                    online++
                    "§a${p.name}" // 在线显示绿色
                } else {
                    "§7${plugin.server.getOfflinePlayer(uuid).name ?: "未知"}" // 离线显示灰色
                }
            }

            // 4. 返回结果
            val info = GuildInfo(data, names, online)
            callback(OperationResult.Success, info)
        })
    }
    /**
     * 核心业务：创建公会
     */
    fun createGuild(player: Player, guildName: String, callback: (OperationResult) -> Unit) {
        // 1. 获取配置
        val cost = plugin.config.getDouble("balance.create", 10000.0)

        // 2. 异步处理数据库检查
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 检查玩家状态
            if (plugin.dbManager.getGuildIdByPlayer(player.uniqueId) != null) {
                callback(OperationResult.AlreadyInGuild)
                return@Runnable
            }

            // 检查公会名是否存在
            if (plugin.dbManager.isNameExists(guildName)) {
                callback(OperationResult.NameAlreadyExists)
                return@Runnable
            }

            // 3. 切换回主线程处理经济（Vault 非线程安全）
            plugin.server.scheduler.runTask(plugin, Runnable {
                val econ = plugin.economy ?: return@Runnable callback(OperationResult.Error("经济系统未就绪"))

                if (!econ.has(player, cost)) {
                    callback(OperationResult.InsufficientFunds(cost))
                    return@Runnable
                }

                // 扣款
                val withdrawResult = econ.withdrawPlayer(player, cost)
                if (!withdrawResult.transactionSuccess()) {
                    callback(OperationResult.Error("交易失败: ${withdrawResult.errorMessage}"))
                    return@Runnable
                }

                // 4. 再次异步写入数据库
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val guildId = plugin.dbManager.createGuild(guildName, player.uniqueId, player.name)
                    if (guildId != -1) {
                        plugin.playerGuildCache[player.uniqueId] = guildId

                        callback(OperationResult.Success)
                    } else {
                        // 物理失败：退款
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            econ.depositPlayer(player, cost)
                        })
                        callback(OperationResult.Error("数据库写入失败，已退款"))
                    }
                })
            })
        })
    }

    /**
     * 核心业务：解散公会
     */
    fun deleteGuild(player: Player, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
                ?: return@Runnable callback(OperationResult.NotInGuild)

            val role = plugin.dbManager.getPlayerRole(player.uniqueId)
            if (role != "OWNER") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            if (plugin.dbManager.deleteGuild(guildId)) {
                // 立即清理所有该公会成员的缓存
                // 在实际跨服中，这里还需要发送 SyncCache REMOVE_GUILD 消息
                plugin.playerGuildCache.entries.removeIf { it.value == guildId }
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error("数据库执行失败"))
            }
        })
    }

    /**
     * 踢出公会成员
     */
    fun kickMember(admin: Player, targetName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.playerGuildCache[admin.uniqueId] ?: return@Runnable callback(OperationResult.NotInGuild)

            // 1. 权限检查
            val role = plugin.dbManager.getPlayerRole(admin.uniqueId)
            if (role != "OWNER" && role != "ADMIN") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            // 2. 获取目标玩家的 UUID 和资料
            val targetUuid = plugin.dbManager.getUuidByPlayerName(targetName)
                ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("kick-failed")))

            // 3. 检查目标是否在该公会
            if (plugin.dbManager.getGuildIdByPlayer(targetUuid) != guildId) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("kick-not-in-your-guild")))
            }

            // 4. 检查职位（管理员不能踢会长，管理员不能踢管理员）
            val targetRole = plugin.dbManager.getPlayerRole(targetUuid)
            if (targetRole == "OWNER" || (role == "ADMIN" && targetRole == "ADMIN")) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("kick-role-limit")))
            }

            // 5. 执行踢出
            if (plugin.dbManager.removeMember(guildId, targetUuid)) {

                // --- 核心逻辑：跨服同步与通知 ---
                // A. 同步全服缓存 (让所有子服知道该玩家已没公会)
                syncPlayerCache(targetUuid, -1)

                // B. 发送踢出通知
                dispatchKickNotification(guildId, targetName)

                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 内部工具：分发踢出通知
     */
    private fun dispatchKickNotification(guildId: Int, targetName: String) {
        val isProxy = plugin.config.getBoolean("proxy", false)

        if (isProxy) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                val out = com.google.common.io.ByteStreams.newDataOutput()
                out.writeUTF("MemberKick")
                out.writeInt(guildId)
                out.writeUTF(targetName)
                plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
            })
        } else {
            // 单服逻辑
            sendLocalKickMessage(guildId, targetName)
        }
    }

    // 提取本地发送逻辑，方便复用
    private fun sendLocalKickMessage(guildId: Int, targetName: String) {
        val kickMsg = plugin.langManager.get("member-kick-notify", "player" to targetName)
        val targetPrivateMsg = plugin.langManager.get("kick-notice-to-target")

        plugin.server.onlinePlayers.forEach { p ->
            // 1. 通知公会内成员
            if (plugin.playerGuildCache[p.uniqueId] == guildId) {
                p.sendMessage(kickMsg)
            }
            // 2. 专门给被踢的玩家发私信 (如果他在本服)
            if (p.name.equals(targetName, ignoreCase = true)) {
                p.sendMessage(targetPrivateMsg)
                p.playSound(p.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            }
        }
    }
    /**
     * 邀请玩家加入公会
     */
    fun invitePlayer(sender: Player, targetName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 权限与状态预检 (本地校验)
            val guildId = plugin.playerGuildCache[sender.uniqueId]
                ?: return@Runnable callback(OperationResult.NotInGuild)

            val role = plugin.dbManager.getPlayerRole(sender.uniqueId)
            if (role != "OWNER" && role != "ADMIN") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            // 2. 不能邀请自己
            if (sender.name.equals(targetName, ignoreCase = true)) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("invite-self-limit")))
            }

            val guildData = plugin.dbManager.getGuildData(guildId) ?: return@Runnable
            val isProxy = plugin.config.getBoolean("proxy", false)

            if (isProxy) {
                // --- 代理模式：盲发广播 ---
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val out = com.google.common.io.ByteStreams.newDataOutput()
                    out.writeUTF("CrossInvite")
                    out.writeUTF(targetName)   // 目标玩家名
                    out.writeInt(guildId)      // 公会ID
                    out.writeUTF(guildData.name) // 公会名
                    out.writeUTF(sender.name)  // 邀请人名

                    sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
                })
                // 在跨服模式下，我们无法立即知道玩家是否在线，所以返回成功，提示“邀请已发出”
                callback(OperationResult.Success)

            } else {
                // --- 单服模式：直接查找 ---
                val targetPlayer = plugin.server.getPlayer(targetName)
                if (targetPlayer == null) {
                    callback(OperationResult.Error(plugin.langManager.get("player-not-online")))
                    return@Runnable
                }

                // 检查目标是否有公会
                if (plugin.dbManager.getGuildIdByPlayer(targetPlayer.uniqueId) != null) {
                    callback(OperationResult.Error(plugin.langManager.get("join-already-in-guild")))
                    return@Runnable
                }

                // 存入邀请缓存并通知
                plugin.inviteCache[targetPlayer.uniqueId] = guildId
                targetPlayer.sendMessage(plugin.langManager.get("invite-received-target",
                    "player" to sender.name, "guild" to guildData.name))
                callback(OperationResult.Success)
            }
        })
    }

    /**
     * 申请加入公会
     */
    fun requestJoin(player: Player, guildName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            if (plugin.dbManager.getGuildIdByPlayer(player.uniqueId) != null) {
                callback(OperationResult.AlreadyInGuild)
                return@Runnable
            }

            val guildId = plugin.dbManager.getGuildIdByName(guildName)
                ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("join-guild-not-found")))

            val currentRequests = plugin.dbManager.getRequests(guildId)
            if (currentRequests.any { it.first == player.uniqueId }) {
                callback(OperationResult.Error(plugin.langManager.get("join-already-requested")))
                return@Runnable
            }

            if (plugin.dbManager.addRequest(guildId, player.uniqueId, player.name)) {
                // 统一分发申请通知
                dispatchGuildNotification(guildId, "NotifyRequest", guildId, guildName, player.name)
                callback(OperationResult.Success)
            }
        })
    }
    /**
     * 内部工具：发送公会通知（兼容单服/跨服）
     */
    private fun dispatchGuildNotification(targetGuildId: Int, subChannel: String, vararg data: Any) {
        val isProxy = plugin.config.getBoolean("proxy", false)

        if (isProxy) {
            // --- 代理模式：发包给代理端转发 ---
            val out = com.google.common.io.ByteStreams.newDataOutput()
            out.writeUTF(subChannel)
            // 动态写入后续参数
            data.forEach {
                when (it) {
                    is Int -> out.writeInt(it)
                    is String -> out.writeUTF(it)
                }
            }
            // 借用第一个在线玩家发包（如果没有在线玩家，单服消息通常也无意义）
            plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())

        } else {
            // --- 单服模式：直接在当前服务器内查找并发送 ---
            when (subChannel) {
                "NotifyRequest" -> {
                    val guildName = data[1] as String
                    val applicantName = data[2] as String
                    val msg = plugin.langManager.get("notify-new-request", "player" to applicantName, "guild" to guildName)

                    plugin.server.onlinePlayers.forEach { p ->
                        if (plugin.playerGuildCache[p.uniqueId] == targetGuildId) {
                            // 异步查一下权限
                            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                                val role = plugin.dbManager.getPlayerRole(p.uniqueId)
                                if (role == "OWNER" || role == "ADMIN") {
                                    p.sendMessage(msg)
                                    p.playSound(p.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                                }
                            })
                        }
                    }
                }
                "Chat" -> {
                    val senderName = data[1] as String
                    val msgContent = data[2] as String
                    val formatted = plugin.langManager.get("chat-format", "player" to senderName, "message" to msgContent)

                    plugin.server.onlinePlayers.forEach { p ->
                        if (plugin.playerGuildCache[p.uniqueId] == targetGuildId) {
                            p.sendMessage(formatted)
                        }
                    }
                }
            }
        }
    }
    /**
     * 接受公会申请 (由管理员执行 /kg accept <玩家>)
     */
    fun acceptRequest(admin: Player, targetName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.playerGuildCache[admin.uniqueId] ?: return@Runnable callback(OperationResult.NotInGuild)

            // 1. 权限检查
            val role = plugin.dbManager.getPlayerRole(admin.uniqueId)
            if (role != "OWNER" && role != "ADMIN") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            // 2. 获取申请列表并匹配目标名字
            val requests = plugin.dbManager.getRequests(guildId)
            val targetPair = requests.find { (uuid, _) ->
                plugin.server.getOfflinePlayer(uuid).name?.equals(targetName, ignoreCase = true) == true
            }

            if (targetPair == null) {
                callback(OperationResult.Error(plugin.langManager.get("request-not-found")))
                return@Runnable
            }

            val targetUuid = targetPair.first

            // 3. 检查目标是否已加入其他公会
            if (plugin.dbManager.getGuildIdByPlayer(targetUuid) != null) {
                plugin.dbManager.removeRequest(guildId, targetUuid)
                callback(OperationResult.Error(plugin.langManager.get("join-already-in-other")))
                return@Runnable
            }

            // 4. 执行添加成员
            if (plugin.dbManager.addMember(guildId, targetUuid, targetName, "MEMBER")) {
                plugin.dbManager.removeRequest(guildId, targetUuid)

                // === 核心修复 1: 立即更新本地内存缓存 (解决无法聊天问题) ===
                plugin.playerGuildCache[targetUuid] = guildId

                // === 核心修复 2: 跨服模式则发包，单服模式则直接本地通知 ===
                val isProxy = plugin.config.getBoolean("proxy", false)
                if (isProxy) {
                    syncPlayerCache(targetUuid, guildId) // 告诉其他子服同步缓存
                    broadcastMemberStatus(guildId, targetName, "JOIN") // 告诉其他子服发欢迎消息
                } else {
                    // 单服模式：直接在这里处理通知逻辑
                    val targetPlayer = plugin.server.getPlayer(targetUuid)

                    // 1. 给新人发私信提示 (解决没收到通知问题)
                    targetPlayer?.let {
                        it.sendMessage(plugin.langManager.get("request-accepted-notice"))
                        it.playSound(it.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    }

                    // 2. 调用单服广播给公会成员 (显示热烈欢迎)
                    broadcastMemberStatus(guildId, targetName, "JOIN")
                }

                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 拒绝申请
     */
    fun denyRequest(sender: Player, targetName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable

            val requests = plugin.dbManager.getRequests(guildId)
            val targetUuid = requests.find { (uuid, _) ->
                plugin.server.getOfflinePlayer(uuid).name?.equals(targetName, true) == true
            }?.first

            if (targetUuid != null) {
                plugin.dbManager.removeRequest(guildId, targetUuid)
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("accept-failed")))
            }
        })
    }
    /**
     * 玩家退出公会逻辑
     */
    fun leaveGuild(player: Player, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
                ?: return@Runnable callback(OperationResult.NotInGuild)

            // 核心逻辑：会长不能直接退出公会
            val role = plugin.dbManager.getPlayerRole(player.uniqueId)
            if (role == "OWNER") {
                callback(OperationResult.Error(plugin.langManager.get("leave-owner-limit")))
                return@Runnable
            }


            if (plugin.dbManager.removeMember(guildId, player.uniqueId)) {

                val playerName = player.name
                syncPlayerCache(player.uniqueId, -1)

                broadcastMemberStatus(guildId, playerName, "LEAVE")

                plugin.playerGuildCache.remove(player.uniqueId)
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error("数据库操作失败，请联系管理员"))
            }
        })
    }

    /**
     * 发送公会频道聊天 (支持跨服)
     */
    fun sendGuildChat(player: Player, message: String) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return
        // 统一分发聊天消息
        dispatchGuildNotification(guildId, "Chat", guildId, player.name, message)
    }
    /**
     * 接受公会邀请 (由被邀请人执行 /kg yes)
     */
    fun acceptInvite(player: Player, callback: (OperationResult) -> Unit) {
        val guildId = plugin.inviteCache[player.uniqueId]
        if (guildId == null) {
            callback(OperationResult.Error(plugin.langManager.get("invite-none")))
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查自己是否已经在公会中 (双重检查)
            if (plugin.dbManager.getGuildIdByPlayer(player.uniqueId) != null) {
                plugin.inviteCache.remove(player.uniqueId)
                callback(OperationResult.AlreadyInGuild)
                return@Runnable
            }

            // 2. 写入数据库
            if (plugin.dbManager.addMember(guildId, player.uniqueId, player.name, "MEMBER")) {
                // 3. 成功后移除邀请缓存
                plugin.inviteCache.remove(player.uniqueId)

                // 4. 更新本地公会缓存
                plugin.playerGuildCache[player.uniqueId] = guildId

                // 5. 跨服同步：通知其他服务器该玩家入会了
                syncPlayerCache(player.uniqueId, guildId)

                // 6. 通知其他服务器该玩家入会了
                broadcastMemberStatus(guildId, player.name, "JOIN")

                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }
    /**
     * 跨服同步玩家公会缓存
     * @param uuid 玩家UUID
     * @param guildId 公会ID (若为-1或不存在可表示退出公会，具体看业务逻辑)
     */
    private fun syncPlayerCache(uuid: java.util.UUID, guildId: Int) {
        // 如果没开代理模式，不需要同步，因为只有一个服务器
        if (!plugin.config.getBoolean("proxy", false)) return

        plugin.server.scheduler.runTask(plugin, Runnable {
            val out = com.google.common.io.ByteStreams.newDataOutput()
            out.writeUTF("SyncCache")      // 子频道标识
            out.writeUTF(uuid.toString())   // 玩家UUID
            out.writeInt(guildId)           // 公会ID

            // 借用任意在线玩家发送
            plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
        })
    }
    /**
     * 拒绝公会邀请 (由被邀请人执行 /kg no)
     */
    fun declineInvite(player: Player) {
        plugin.inviteCache.remove(player.uniqueId)
    }
    // GuildService.kt

    /**
     * 统一分发成员变动通知 (加入/退出/踢出)
     */
    fun broadcastMemberStatus(guildId: Int, playerName: String, type: String) {
        val isProxy = plugin.config.getBoolean("proxy", false)
        val subChannel = if (type == "JOIN") "MemberJoin" else "MemberLeave"

        if (isProxy) {
            // --- 跨服模式：发包给代理端 ---
            plugin.server.scheduler.runTask(plugin, Runnable {
                val out = com.google.common.io.ByteStreams.newDataOutput()
                out.writeUTF(subChannel)
                out.writeInt(guildId)
                out.writeUTF(playerName)

                plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
            })
        } else {
            // --- 单服模式：直接向本地公会成员发送 ---
            sendLocalMemberStatus(guildId, playerName, type)
        }
    }

    private fun sendLocalMemberStatus(guildId: Int, playerName: String, type: String) {
        val langKey = if (type == "JOIN") "member-join-notify" else "member-leave-notify"
        val msg = plugin.langManager.get(langKey, "player" to playerName)

        plugin.server.onlinePlayers.forEach { p ->
            if (plugin.playerGuildCache[p.uniqueId] == guildId) {
                p.sendMessage(msg)
            }
        }
    }
}