package org.katacr.kaguilds.service

import org.katacr.kaguilds.KaGuilds
import org.bukkit.entity.Player
import org.katacr.kaguilds.DatabaseManager
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

class GuildService(private val plugin: KaGuilds) {
    data class GuildInfo(
        val data: DatabaseManager.GuildData,
        val memberNames: List<String>,
        val onlineCount: Int
    )
    // 定义待处理动作类型
    sealed class PendingAction {
        data class Create(val guildName: String) : PendingAction()
        data class Transfer(val targetName: String) : PendingAction()
        object Delete : PendingAction()
        object Leave : PendingAction()
    }


    // 记录玩家 UUID 和对应的 BukkitTask
    private val teleportTasks = mutableMapOf<UUID, org.bukkit.scheduler.BukkitTask>()
    // 缓存：UUID -> 动作
    private val pendingConfirmations = mutableMapOf<UUID, PendingAction>()
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
                val econ = plugin.economy ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("error-vault")))

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
                        callback(OperationResult.Error(plugin.langManager.get("error-database")))
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
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
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

    /**
     * 单服逻辑：发送踢出通知
     */
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
     * 申请加入公会 (支持名称或 #ID)
     */
    fun requestJoin(player: Player, input: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查是否已在公会
            if (plugin.dbManager.getGuildIdByPlayer(player.uniqueId) != null) {
                callback(OperationResult.AlreadyInGuild)
                return@Runnable
            }

            // 2. 解析 ID 或 名称
            val guildId: Int
            val guildName: String

            if (input.startsWith("#")) {
                val id = input.substring(1).toIntOrNull() ?: -1
                val data = plugin.dbManager.getGuildById(id)
                if (data == null) {
                    callback(OperationResult.Error(plugin.langManager.get("join-guild-not-found")))
                    return@Runnable
                }
                guildId = data.id
                guildName = data.name
            } else {
                guildId = plugin.dbManager.getGuildIdByName(input)
                guildName = input
            }

            if (guildId == -1) {
                callback(OperationResult.Error(plugin.langManager.get("join-guild-not-found")))
                return@Runnable
            }

            // 3. 检查重复申请
            val currentRequests = plugin.dbManager.getRequests(guildId)
            if (currentRequests.any { it.first == player.uniqueId }) {
                callback(OperationResult.Error(plugin.langManager.get("join-already-requested")))
                return@Runnable
            }

            // 4. 添加申请并通知
            if (plugin.dbManager.addRequest(guildId, player.uniqueId, player.name)) {
                // 这里已经能拿到真实的 guildName 了
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

                plugin.playerGuildCache[targetUuid] = guildId

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
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
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

    /**
     * 单服模式：向本地公会成员发送通知
     */
    private fun sendLocalMemberStatus(guildId: Int, playerName: String, type: String) {
        val langKey = if (type == "JOIN") "member-join-notify" else "member-leave-notify"
        val msg = plugin.langManager.get(langKey, "player" to playerName)

        plugin.server.onlinePlayers.forEach { p ->
            if (plugin.playerGuildCache[p.uniqueId] == guildId) {
                p.sendMessage(msg)
            }
        }
    }

    /**
     * 设置公会传送点
     */
    fun setGuildTP(player: Player, callback: (OperationResult) -> Unit) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        val role = plugin.dbManager.getPlayerRole(player.uniqueId)
        if (role != "OWNER" && role != "ADMIN") {
            return callback(OperationResult.NoPermission)
        }
        // 检查世界是否被禁用
        val worldName = player.world.name
        val disabledWorlds = plugin.config.getStringList("guild.teleport.disabled-worlds")
        if (disabledWorlds.contains(worldName)) {
            return callback(OperationResult.Error(plugin.langManager.get("tp-world-disabled")))
        }
        // 格式修改为: serverName:worldName,x,y,z,yaw,pitch
        // plugin.config 中应该有一个标识当前服务器名字的配置，比如 "server-name: lobby1"
        val serverName = plugin.config.getString("server-id", "unknown")
        val loc = player.location
        val locStr = "$serverName:${loc.world?.name},${loc.x},${loc.y},${loc.z},${loc.yaw},${loc.pitch}"

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            if (plugin.dbManager.setGuildLocation(guildId, locStr)) {
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 传送到公会点
     */
    fun teleportToGuild(player: Player, callback: (OperationResult) -> Unit) {
        val uuid = player.uniqueId
        val guildId = plugin.playerGuildCache[uuid] ?: return callback(OperationResult.NotInGuild)

        // 如果已经在传送中，不要重复开启
        if (isTeleporting(uuid)) return

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildData = plugin.dbManager.getGuildData(guildId) ?: return@Runnable
            val locStr = guildData.teleportLocation

            // 1. 检查传送点是否存在
            if (locStr.isNullOrEmpty()) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("tp-not-set")))
            }

            // 2. 跨服判断
            val mainParts = locStr.split(":")
            val targetServer = mainParts[0]
            val currentServer = plugin.config.getString("server-id", "unknown")

            if (plugin.config.getBoolean("proxy", false) && targetServer != currentServer) {
                return@Runnable callback(OperationResult.Error(
                    plugin.langManager.get("tp-wrong-server", "server" to targetServer)
                ))
            }

            // 3. 获取等级费用与倒计时设置
            val level = guildData.level
            val cost = plugin.config.getDouble("level.$level.tp-money", 500.0)
            val cooldown = plugin.config.getInt("guild.teleport.cooldown", 3)

            // 4. 检查经济
            if (plugin.economy?.has(player, cost) == false) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("tp-insufficient-money", "cost" to cost.toString())))
            }

            // 5. 核心逻辑：判断是否需要等待
            if (cooldown <= 0) {
                // --- 瞬间传送分支 ---
                plugin.server.scheduler.runTask(plugin, Runnable {
                    executeFinalTeleport(player, locStr, cost)
                    callback(OperationResult.Success)
                })
            } else {
                // --- 延迟传送分支 ---
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(plugin.langManager.get("tp-starting", "time" to cooldown.toString()))

                    val task = plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        executeFinalTeleport(player, locStr, cost)
                        teleportTasks.remove(uuid)
                        callback(OperationResult.Success)
                    }, cooldown * 20L)

                    teleportTasks[uuid] = task
                })
            }
        })
    }

    /**
     * 内部私有方法：执行扣费和物理传送
     */
    private fun executeFinalTeleport(player: Player, locStr: String, cost: Double) {
        // 扣费
        plugin.economy?.withdrawPlayer(player, cost)

        try {
            // 这里的 locStr 格式是 serverName:world,x,y,z,yaw,pitch
            // 我们需要先去掉 serverName: 部分，再解析坐标
            val coordPart = locStr.substringAfter(":")
            val parts = coordPart.split(",")

            val world = plugin.server.getWorld(parts[0])
            if (world == null) {
                player.sendMessage(plugin.langManager.get("tp-world-error"))
                return
            }

            val destination = org.bukkit.Location(
                world, parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble(),
                parts[4].toFloat(), parts[5].toFloat()
            )

            player.teleport(destination)
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
        } catch (e: Exception) {
            player.sendMessage("§c传送点数据解析失败，请联系管理员重新设置。")
            e.printStackTrace()
        }
    }

    /**
     * 检查玩家是否正在传送
     */
    fun isTeleporting(uuid: java.util.UUID): Boolean {
        return teleportTasks.containsKey(uuid)
    }
    /**
     * 取消玩家的传送任务
     */
    fun cancelTeleport(uuid: java.util.UUID) {
        teleportTasks.remove(uuid)?.cancel()
    }
    /**
     * 修改公会名称
     */
    fun renameGuild(player: Player, newName: String, callback: (OperationResult) -> Unit) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查 (仅限会长)
        val role = plugin.dbManager.getPlayerRole(player.uniqueId)
        if (role != "OWNER") {
            return callback(OperationResult.Error(plugin.langManager.get("rename-only-owner")))
        }

        // 2. 名字合法性检查
        if (newName.length > 16 || newName.isEmpty()) {
            return callback(OperationResult.Error(plugin.langManager.get("create-invalid-name")))
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 3. 检查名字是否已存在 (注意这里的逻辑已经修正为判断 -1)
            val existingId = plugin.dbManager.getGuildIdByName(newName)
            if (existingId != -1 && existingId != guildId) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("create-name-exists")))
            }

            // 4. 检查经济
            val cost = plugin.config.getDouble("balance.rename", 5000.0)
            if (plugin.economy?.has(player, cost) == false) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("create-insufficient")))
            }

            // 5. 执行扣费
            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.economy?.withdrawPlayer(player, cost)

                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    // 执行数据库改名
                    if (plugin.dbManager.renameGuild(guildId, newName)) {

                        // --- 核心复刻功能：本地广播给全公会成员 ---
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            val renameMsg = plugin.langManager.get("rename-success-notify", "name" to newName)
                            plugin.server.onlinePlayers.forEach { onlinePlayer ->
                                // 检查在线玩家是否属于该公会 (利用缓存提升效率)
                                if (plugin.playerGuildCache[onlinePlayer.uniqueId] == guildId) {
                                    onlinePlayer.sendMessage(renameMsg)
                                }
                            }
                        })

                        // 6. 跨服同步 (如果开启了代理模式)
                        syncRenameProxy(guildId, newName)

                        callback(OperationResult.Success)
                    } else {
                        callback(OperationResult.Error(plugin.langManager.get("error-database")))
                    }
                })
            })
        })
    }

    /**
     * 跨服同步改名通知
     */
    fun syncRenameProxy(guildId: Int, newName: String) {
        val isProxy = plugin.config.getBoolean("proxy", false)
        if (isProxy) {
            val out = com.google.common.io.ByteStreams.newDataOutput()
            out.writeUTF("RenameSync")
            out.writeInt(guildId)
            out.writeUTF(newName)
            plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
        }
    }

    /**
     * 分发金库变动通知
     * @param type "deposit" 或 "withdraw"
     */
    internal fun dispatchBankNotification(guildId: Int, playerName: String, type: String, amount: Double) {
        // 1. 本地通知 (单服模式必走)
        val langKey = if (type == "deposit") "bank-deposit-notify" else "bank-withdraw-notify"
        val msg = plugin.langManager.get(langKey,
            "player" to playerName,
            "amount" to amount.toString()
        )

        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.server.onlinePlayers.forEach { p ->
                if (plugin.playerGuildCache[p.uniqueId] == guildId) {
                    p.sendMessage(msg)
                }
            }
        })

        // 2. 跨服通知 (仅在开启 proxy 时发送)
        if (plugin.config.getBoolean("proxy", false)) {
            val out = com.google.common.io.ByteStreams.newDataOutput()
            out.writeUTF("BankSync")
            out.writeInt(guildId)
            out.writeUTF(playerName)
            out.writeUTF(type)
            out.writeDouble(amount)
            plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
        }
    }

    /**
     * 购买公会 Buff
     */
    fun buyBuff(player: Player, buffKey: String, callback: (OperationResult) -> Unit) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 获取公会数据以确认等级
            val guildData = plugin.dbManager.getGuildData(guildId) ?: return@Runnable
            val level = guildData.level

            // 2. 检查当前等级是否允许使用该 Buff
            // 从 level.X.use-buff 路径读取列表
            val allowedBuffs = plugin.config.getStringList("level.$level.use-buff")

            // 这里的 contains 区分大小写，建议确保 config 中的 key 和指令输入一致
            // 或者使用 val found = allowedBuffs.any { it.equals(buffKey, ignoreCase = true) }
            if (!allowedBuffs.contains(buffKey)) {
                val errorMsg = plugin.langManager.get("buff-level-low", "level" to level.toString())
                return@Runnable callback(OperationResult.Error(errorMsg))
            }

            // 3. 读取 Buff 具体配置
            val path = "guild.buffs.$buffKey"
            if (!plugin.config.contains(path)) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("buff-not-exist")))
            }

            val typeStr = plugin.config.getString("$path.type")
            val price = plugin.config.getDouble("$path.price")
            val amplifier = plugin.config.getInt("$path.amplifier", 0)
            val buffName = plugin.config.getString("$path.name", buffKey)

            val potionType = org.bukkit.potion.PotionEffectType.getByName(typeStr ?: "")
                ?: return@Runnable callback(OperationResult.Error("配置错误：无效的效果类型 $typeStr"))

            // 4. 检查经济
            if (plugin.economy?.has(player, price) == false) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("create-insufficient")))
            }

            // 5. 扣费并分发
            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.economy?.withdrawPlayer(player, price)

                val durationSeconds = plugin.config.getInt("guild.buff-time", 90)
                dispatchBuff(guildId, potionType, durationSeconds, amplifier, player.name, buffName!!)

                callback(OperationResult.Success)
            })
        })
    }

    /**
     * 存入待确认动作，30秒后自动过期
     */
    fun setPendingAction(uuid: java.util.UUID, action: PendingAction) {
        pendingConfirmations[uuid] = action
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            // 只有当当前的 action 还是我们要删除的那个时才移除（防止覆盖后被误删）
            if (pendingConfirmations[uuid] == action) {
                pendingConfirmations.remove(uuid)
            }
        }, 20 * 30L)
    }

    /**
     * 检查玩家是否有待确认的动作 (不消耗它)
     */
    fun hasPendingAction(uuid: java.util.UUID): Boolean {
        return pendingConfirmations.containsKey(uuid)
    }
    /**
     * 获取并消耗待确认动作
     */
    fun consumePendingAction(uuid: java.util.UUID): PendingAction? {
        return pendingConfirmations.remove(uuid)
    }

    /**
     * 管理员强行改名公会
     */
    fun adminRenameGuild(guildId: Int, newName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查名称是否被【其他】公会占用
            val existingId = plugin.dbManager.getGuildIdByName(newName)
            if (existingId != -1 && existingId != guildId) {
                callback(OperationResult.Error(plugin.langManager.get("create-name-exists")))
                return@Runnable
            }

            // 2. 执行改名
            if (plugin.dbManager.renameGuild(guildId, newName)) {
                // 1. 本地通知 (当前子服的公会成员)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val notifyMsg = plugin.langManager.get("admin-rename-notify", "name" to newName)
                    plugin.server.onlinePlayers.forEach { p ->
                        if (plugin.playerGuildCache[p.uniqueId] == guildId) {
                            p.sendMessage(notifyMsg)
                        }
                    }
                })

                // 2. 跨服广播通知 (发送给其他子服)
                if (plugin.config.getBoolean("proxy", false)) {
                    val out = com.google.common.io.ByteStreams.newDataOutput()
                    out.writeUTF("AdminRenameSync") // 新增子通道名
                    out.writeInt(guildId)
                    out.writeUTF(newName)

                    plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(
                        plugin, "kaguilds:chat", out.toByteArray()
                    )
                }

                // 3. 执行现有的同步逻辑 (同步数据库缓存等)
                syncRenameProxy(guildId, newName)

                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 管理员强行解散公会
     */
    fun adminDeleteGuild(guildId: Int, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查公会是否存在
            val data = plugin.dbManager.getGuildData(guildId)
            if (data == null) {
                callback(OperationResult.Error(plugin.langManager.get("error-guild-not-found")))
                return@Runnable
            }

            // 2. 执行数据库删除操作
            // 请确保 dbManager.deleteGuild(guildId) 内部会清理成员表和申请表
            if (plugin.dbManager.deleteGuild(guildId)) {

                // 3. 本地清理：通知在线成员并移除缓存
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val notifyMsg = plugin.langManager.get("admin-delete-notify")
                    plugin.server.onlinePlayers.forEach { player ->
                        if (plugin.playerGuildCache[player.uniqueId] == guildId) {
                            plugin.playerGuildCache.remove(player.uniqueId)
                            player.sendMessage(notifyMsg)
                        }
                    }
                })

                // 4. 跨服同步清理
                if (plugin.config.getBoolean("proxy", false)) {
                    val out = com.google.common.io.ByteStreams.newDataOutput()
                    out.writeUTF("DeleteSync")
                    out.writeInt(guildId)

                    // 使用第一个在线玩家作为发送媒介
                    plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(
                        plugin, "kaguilds:chat", out.toByteArray()
                    )
                }

                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 管理员获取指定 ID 的公会详细信息
     */
    fun getAdminGuildInfo(guildId: Int, callback: (OperationResult, GuildInfo?) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 获取基础数据
            val data = plugin.dbManager.getGuildData(guildId)
            if (data == null) {
                callback(OperationResult.Error(plugin.langManager.get("error-guild-not-found", "id" to guildId.toString())), null)
                return@Runnable
            }

            // 2. 获取成员列表名称
            val members = plugin.dbManager.getMemberNames(guildId)

            // 3. 计算在线人数 (遍历全服缓存)
            val onlineCount = plugin.playerGuildCache.values.count { it == guildId }

            val info = GuildInfo(data, members, onlineCount)
            callback(OperationResult.Success, info)
        })
    }

    /**
     * 管理员强行操作公会金库 (适配增量 SQL 版本)
     */
    fun adminManageBank(guildId: Int, action: String, amount: Double, callback: (OperationResult, Double) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 先获取当前数据（仅用于计算逻辑和日志）
            val guildData = plugin.dbManager.getGuildData(guildId)
            if (guildData == null) {
                callback(OperationResult.Error(plugin.langManager.get("error-guild-not-found")), 0.0)
                return@Runnable
            }

            // 2. 根据不同动作确定【传给数据库的变化量】
            val delta = when (action.lowercase()) {
                "add" -> amount // 存 100，传给 SQL 就是 +100
                "remove" -> -amount // 取 100，传给 SQL 就是 -100
                "set" -> {
                    // 特殊处理 SET：由于底层是 balance = balance + ?
                    // 目标值 x 的变化量应该是：x - 当前余额
                    amount - guildData.balance
                }
                else -> 0.0
            }

            // 3. 拦截非法扣费：如果是取钱，且余额不足
            if (action.lowercase() == "remove" && guildData.balance < amount) {
                // 强制把扣除量改为当前余额，防止变成负数 (或者直接报错)
                // delta = -guildData.balance
            }

            // 4. 调用你提供的增量更新方法
            if (plugin.dbManager.updateGuildBalance(guildId, delta)) {
                // 记录日志 (记录的是变动金额 amount)
                plugin.dbManager.logBankTransaction(guildId, "ADMIN", action.uppercase(), amount)

                // 获取最新余额返回给回调
                val newBalance = guildData.balance + delta

                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Success, newBalance)
                })
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")), 0.0)
            }
        })
    }

    /**
     * 管理员查看指定公会的金库日志
     */
    fun getAdminBankLogs(guildId: Int, page: Int, callback: (List<String>) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val logs = plugin.dbManager.getBankLogs(guildId, page)
            // 回到主线程执行回调
            plugin.server.scheduler.runTask(plugin, Runnable {
                callback(logs)
            })
        })
    }
    /**
     * 分发 Buff (本地 + 跨服)
     */
    private fun dispatchBuff(guildId: Int, type: PotionEffectType, seconds: Int, amplifier: Int, buyerName: String, buffName: String) {
        val durationTicks = seconds * 20

        // 1. 本地分发 (切回主线程确保安全)
        plugin.server.scheduler.runTask(plugin, Runnable {
            // 构建药水效果
            val effect = PotionEffect(type, durationTicks, amplifier)

            // 遍历在线玩家
            plugin.server.onlinePlayers.forEach { p ->
                if (plugin.playerGuildCache[p.uniqueId] == guildId) {
                    p.addPotionEffect(effect)
                    p.sendMessage(plugin.langManager.get("buff-received",
                        "player" to buyerName,
                        "buff" to buffName
                    ))
                }
            }
        })

        // 2. 跨服分发
        if (plugin.config.getBoolean("proxy", false)) {
            val out = com.google.common.io.ByteStreams.newDataOutput()
            out.writeUTF("BuffSync")
            out.writeInt(guildId)
            out.writeUTF(type.name) // 传递药水枚举名
            out.writeInt(seconds)
            out.writeInt(amplifier)
            out.writeUTF(buyerName)
            out.writeUTF(buffName)

            plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
        }
    }

    /**
     * 转让公会所有权
     */
    fun adminTransferGuild(guildId: Int, newOwnerName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val data = plugin.dbManager.getGuildData(guildId) ?: return@Runnable callback(OperationResult.Error("未找到公会"))

            val oldOwnerUuid = UUID.fromString(data.ownerUuid)
            val newOwnerUuid = plugin.dbManager.getPlayerUuidByName(newOwnerName)
                ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("error-not-in-guild", "player" to newOwnerName)))

            if (plugin.dbManager.transferGuildOwnership(guildId, oldOwnerUuid, newOwnerUuid, newOwnerName)) {

                // 1. 更新缓存
                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                    if (plugin.playerGuildCache[onlinePlayer.uniqueId] == guildId) {
                        plugin.playerGuildCache.remove(onlinePlayer.uniqueId)
                    }
                }
                plugin.playerGuildCache.remove(oldOwnerUuid)
                plugin.playerGuildCache.remove(newOwnerUuid)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Success)
                })
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 管理员强制踢人
     */
    fun adminKickMember(guildId: Int, targetName: String, callback: (OperationResult) -> Unit) {
        val lang = plugin.langManager
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val targetUuid = plugin.dbManager.getPlayerUuidByName(targetName)
                ?: return@Runnable callback(OperationResult.Error(lang.get("error-not-has-player", "player" to targetName)))

            // 1. 检查是否在指定的公会中
            val currentGuildId = plugin.dbManager.getGuildIdByPlayer(targetUuid)
            if (currentGuildId != guildId) {
                return@Runnable callback(OperationResult.Error(lang.get("error-not-in-guild", "player" to targetName)))
            }

            // 2. 安全检查：禁止踢出会长
            val data = plugin.dbManager.getGuildData(guildId)
            if (data?.ownerUuid == targetUuid.toString()) {
                return@Runnable callback(OperationResult.Error(lang.get("error-player-is-master")))
            }

            // 3. 调用你现有的 removeMember(Int, UUID)
            if (plugin.dbManager.removeMember(guildId, targetUuid)) {
                plugin.playerGuildCache.remove(targetUuid) // 刷新缓存
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(lang.get("error-database")))
            }
        })
    }

    /**
     * 管理员强制加人
     */
    fun adminJoinMember(guildId: Int, targetPlayer: Player, callback: (OperationResult) -> Unit) {
        val lang = plugin.langManager
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查玩家是否已有公会
            if (plugin.dbManager.getGuildIdByPlayer(targetPlayer.uniqueId) != null) {
                return@Runnable callback(OperationResult.Error(lang.get("invite-already-member")))
            }

            // 2. 调用现有的 addMember(Int, UUID, String, String)
            if (plugin.dbManager.addMember(guildId, targetPlayer.uniqueId, targetPlayer.name, "MEMBER")) {
                plugin.playerGuildCache[targetPlayer.uniqueId] = guildId // 同步缓存
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(lang.get("error-database")))
            }
        })
    }
}