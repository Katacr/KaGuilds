package org.katacr.kaguilds.service

import org.bukkit.command.CommandSender
import org.katacr.kaguilds.KaGuilds
import org.bukkit.entity.Player
import org.katacr.kaguilds.DatabaseManager
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaguilds.listener.VaultHolder
import org.katacr.kaguilds.util.SerializationUtil
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
    private val teleportTasks = mutableMapOf<UUID, BukkitTask>()
    // 缓存：UUID -> 动作
    private val pendingConfirmations = mutableMapOf<UUID, PendingAction>()
    // 锁：Pair(公会ID, 仓库编号) -> 使用者的 UUID
    val vaultLocks = mutableMapOf<Pair<Int, Int>, UUID>()

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
                callback(OperationResult.Error(plugin.langManager.get("error-database")), null)
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
        // 异步执行数据库检查，避免主线程卡顿
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 强制去数据库拉取最新的 guildId，而不是只信缓存
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
            if (guildId == null) {
                // 同步回主线程发消息
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(plugin.langManager.get("not-in-guild"))
                })
                // 同时清理本地错误的缓存
                plugin.playerGuildCache.remove(player.uniqueId)
                return@Runnable
            }

            // 2. 更新一下本地缓存，确保后续逻辑也是对的
            plugin.playerGuildCache[player.uniqueId] = guildId

            // 3. 统一分发聊天消息
            dispatchGuildNotification(guildId, "Chat", guildId, player.name, message)
        })
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
    private fun syncPlayerCache(uuid: UUID, guildId: Int) {
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
     * 设置公会传送点 (消耗公会资金版)
     */
    fun setGuildTP(player: Player, callback: (OperationResult) -> Unit) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查 (会长或副会长)
        val role = plugin.dbManager.getPlayerRole(player.uniqueId)
        if (role != "OWNER" && role != "ADMIN") {
            return callback(OperationResult.NoPermission)
        }

        // 2. 环境检查：检查世界是否被禁用
        val worldName = player.world.name
        val disabledWorlds = plugin.config.getStringList("guild.teleport.disabled-worlds")
        if (disabledWorlds.contains(worldName)) {
            return callback(OperationResult.Error(plugin.langManager.get("tp-world-disabled")))
        }

        // 3. 异步处理经济与数据库更新
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 4. 获取公会数据并检查公会银行余额
            val guild = plugin.dbManager.getGuildById(guildId)
            val cost = plugin.config.getDouble("balance.settp", 1000.0)

            if (guild == null || guild.balance < cost) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(
                        plugin.langManager.get(
                            "error-balance-not-enough",
                            "cost" to cost.toString(),
                            "balance" to (guild?.balance ?: 0.0).toString()
                        )
                    ))
                })
                return@Runnable
            }

            // 5. 准备坐标字符串 (格式: serverName:worldName,x,y,z,yaw,pitch)
            val serverName = plugin.config.getString("server-id", "unknown")
            val loc = player.location
            val locStr = "$serverName:${loc.world?.name},${loc.x},${loc.y},${loc.z},${loc.yaw},${loc.pitch}"

            // 6. 执行数据库更新
            if (plugin.dbManager.setGuildLocation(guildId, locStr)) {

                // --- 核心扣费与细化日志记录 ---
                plugin.dbManager.updateGuildBalance(guildId, -cost)
                plugin.dbManager.logBankTransaction(guildId, player.name, "SET_TP", cost)

                // 7. 回到主线程执行成功回调
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Success)
                })
            } else {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-database")))
                })
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
            player.sendMessage(plugin.langManager.get("tp-data-error"))
            e.printStackTrace()
        }
    }

    /**
     * 检查玩家是否正在传送
     */
    fun isTeleporting(uuid: UUID): Boolean {
        return teleportTasks.containsKey(uuid)
    }
    /**
     * 取消玩家的传送任务
     */
    fun cancelTeleport(uuid: UUID) {
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
            // 3. 检查名字是否已存在
            val existingId = plugin.dbManager.getGuildIdByName(newName)
            if (existingId != -1 && existingId != guildId) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("create-name-exists")))
                })
                return@Runnable
            }

            // 4. 获取公会数据并检查公会银行经济
            val guild = plugin.dbManager.getGuildById(guildId)
            val cost = plugin.config.getDouble("balance.rename", 5000.0)

            if (guild == null || guild.balance < cost) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(
                        plugin.langManager.get(
                            "error-balance-not-enough",
                            "cost" to cost.toString(),
                            "balance" to (guild?.balance ?: 0.0).toString()
                        )
                    ))
                })
                return@Runnable
            }

            // 5. 执行数据库改名与扣费 (在异步线程执行)
            if (plugin.dbManager.renameGuild(guildId, newName)) {

                // --- 核心修改：扣除公会余额并记录细化日志 ---
                plugin.dbManager.updateGuildBalance(guildId, -cost)
                plugin.dbManager.logBankTransaction(guildId, player.name, "RENAME", cost)

                // 6. 跨服同步 (如果开启了代理模式)
                syncRenameProxy(guildId, newName)

                // 回到主线程处理通知和回调
                plugin.server.scheduler.runTask(plugin, Runnable {
                    // 本地广播给全公会成员
                    val renameMsg = plugin.langManager.get("rename-success-notify", "name" to newName)
                    plugin.server.onlinePlayers.forEach { onlinePlayer ->
                        if (plugin.playerGuildCache[onlinePlayer.uniqueId] == guildId) {
                            onlinePlayer.sendMessage(renameMsg)
                        }
                    }
                    callback(OperationResult.Success)
                })
            } else {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-database")))
                })
            }
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

        // 1. 权限检查 (建议仅限会长或副会长，防止普通成员耗尽公会资金)
        // val role = plugin.dbManager.getPlayerRole(player.uniqueId)
        // if (role != "OWNER" && role != "ADMIN") {
        //     return callback(OperationResult.NoPermission)
        // }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 2. 获取公会数据 (包含等级和余额)
            val guildData = plugin.dbManager.getGuildData(guildId)
                ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("error-database")))

            val level = guildData.level
            val balance = guildData.balance

            // 3. 等级限制检查
            val allowedBuffs = plugin.config.getStringList("level.$level.use-buff")
            if (!allowedBuffs.contains(buffKey)) {
                val errorMsg = plugin.langManager.get("buff-level-low", "level" to level.toString())
                return@Runnable callback(OperationResult.Error(errorMsg))
            }

            // 4. 读取 Buff 配置与价格
            val path = "guild.buffs.$buffKey"
            if (!plugin.config.contains(path)) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("buff-not-exist")))
            }

            val price = plugin.config.getDouble("$path.price")
            val typeStr = plugin.config.getString("$path.type")
            val amplifier = plugin.config.getInt("$path.amplifier", 0)
            val buffName = plugin.config.getString("$path.name", buffKey) ?: buffKey

            val potionType = PotionEffectType.getByName(typeStr ?: "")
                ?: return@Runnable callback(OperationResult.Error("配置错误：无效效果 $typeStr"))

            // 5. 检查公会银行余额
            if (balance < price) {
                val errorMsg = plugin.langManager.get("error-balance-not-enough",
                    "cost" to price.toString(),
                    "balance" to balance.toString()
                )
                return@Runnable callback(OperationResult.Error(errorMsg))
            }

            // 6. 执行扣费与记录 (异步更新数据库)
            if (plugin.dbManager.updateGuildBalance(guildId, -price)) {
                // 记录银行日志：类型为 BUY_BUFF
                plugin.dbManager.logBankTransaction(guildId, player.name, "BUY_BUFF", price)

                // 7. 切回主线程进行分发 (PotionEffect 必须在主线程操作)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val durationSeconds = plugin.config.getInt("guild.buff-time", 90)
                    dispatchBuff(guildId, potionType, durationSeconds, amplifier, player.name, buffName)
                    callback(OperationResult.Success)
                })
            } else {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-database")))
                })
            }
        })
    }

    /**
     * 存入待确认动作，30秒后自动过期
     */
    fun setPendingAction(uuid: UUID, action: PendingAction) {
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
    fun hasPendingAction(uuid: UUID): Boolean {
        return pendingConfirmations.containsKey(uuid)
    }
    /**
     * 获取并消耗待确认动作
     */
    fun consumePendingAction(uuid: UUID): PendingAction? {
        return pendingConfirmations.remove(uuid)
    }

    /**
     * 改名公会
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

            // 3. 调用你提供的增量更新方法
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
     * 转让公会所有权 (经过逻辑清理)
     */
    fun adminTransferGuild(guildId: Int, newOwnerName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 获取公会当前数据
            val data = plugin.dbManager.getGuildData(guildId) ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("error-guild-not-found", "id" to guildId.toString())))

            val oldOwnerUuid = UUID.fromString(data.ownerUuid)
            val newOwnerUuid = plugin.dbManager.getPlayerUuidByName(newOwnerName)
                ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("error-not-in-guild", "player" to newOwnerName)))

            // 1. 执行数据库更新 (事务处理)
            if (plugin.dbManager.transferGuildOwnership(guildId, oldOwnerUuid, newOwnerUuid, newOwnerName)) {

                // 2. 刷新本地所有成员缓存
                // 这个方法内部已经包含了遍历在线玩家并纠正 ID 的逻辑，不需要再手动循环 remove
                refreshLocalMembersCache(guildId)

                // 3. 处理跨服同步
                if (plugin.config.getBoolean("proxy")) {
                    // 发送全服成员刷新指令 (强制其他服执行 refreshLocalMembersCache)
                    sendForceRefreshPacket(guildId)

                    // 发送会长变更通知 (用于显示聊天信息和称号)
                    sendTransferSyncPacket(guildId, newOwnerUuid, newOwnerName)
                }

                // 4. 返回成功回调
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Success)
                })
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }
    /**
     * 强制刷新本地服务器中属于该公会的所有成员缓存
     */
    fun refreshLocalMembersCache(guildId: Int) {
        plugin.server.onlinePlayers.forEach { player ->
            // 方案 A：直接暴力清理，让玩家下次说话时触发你写的 sendGuildChat 里的查库逻辑
            // plugin.playerGuildCache.remove(player.uniqueId)

            // 方案 B：主动异步读取并纠正（更推荐，体验更好）
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                val realId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
                if (realId == guildId) {
                    plugin.playerGuildCache[player.uniqueId] = guildId
                } else {
                    // 如果发现该玩家其实不属于这个公会了，清理掉旧缓存
                    if (plugin.playerGuildCache[player.uniqueId] == guildId) {
                        plugin.playerGuildCache.remove(player.uniqueId)
                    }
                }
            })
        }
    }
    /**
     * 发送强制刷新包
     */
    private fun sendForceRefreshPacket(guildId: Int) {
        val out = com.google.common.io.ByteStreams.newDataOutput()
        out.writeUTF("ForceRefreshMembers")
        out.writeInt(guildId)
        plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
    }
    /**
     * 发送会长转让的跨服封包
     */
    private fun sendTransferSyncPacket(guildId: Int, newOwnerUuid: UUID, newOwnerName: String) {
        val out = com.google.common.io.ByteStreams.newDataOutput()
        out.writeUTF("GuildTransfer") // 子频道名称
        out.writeInt(guildId)         // 哪个公会
        out.writeUTF(newOwnerUuid.toString()) // 新会长UUID
        out.writeUTF(newOwnerName)    // 新会长名字

        // 找一个在线玩家作为发信人
        plugin.server.onlinePlayers.firstOrNull()?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
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

    /**
     * 玩家尝试开启仓库
     */
    fun openVault(player: Player, vaultIndex: Int) {
        val lang = plugin.langManager
        val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId) ?: return

        // 1. 等级检查
        val guildData = plugin.dbManager.getGuildData(guildId) ?: return
        val maxVaults = plugin.config.getInt("level.${guildData.level}.vaults", 1)
        if (vaultIndex > maxVaults) {
            player.sendMessage(lang.get("vault-max-vaults", "max" to maxVaults.toString()))
            return
        }

        // 2. 异步抢锁
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val success = plugin.dbManager.tryGrabLock(guildId, vaultIndex, player.uniqueId)

            if (!success) {
                // 抢锁失败，提示玩家
                player.sendMessage(lang.get("vault-locked", "index" to vaultIndex.toString()))
                return@Runnable
            }

            // 3. 抢锁成功，读取数据
            val rawData = plugin.dbManager.getVaultData(guildId, vaultIndex)

            // 4. 回到主线程渲染
            plugin.server.scheduler.runTask(plugin, Runnable {
                val holder = VaultHolder(guildId, vaultIndex)
                val inv = plugin.server.createInventory(holder, 54, lang.get("vault-title", "index" to vaultIndex.toString()))
                holder.setInventory(inv)

                if (!rawData.isNullOrEmpty()) {
                    inv.contents = SerializationUtil.itemStackArrayFromBase64(rawData)
                }

                player.openInventory(inv)
                // 5. 启动续租 (每 10 秒刷新一次数据库)
                holder.leaseTask = startVaultLeaseTask(player, guildId, vaultIndex)
            })
        })
    }
    /**
     * 尝试锁定仓库
     * @return 如果锁定成功返回 true，如果已被占用返回 false
     */
    fun tryLockVault(guildId: Int, vaultIndex: Int, player: Player, isFromNetwork: Boolean = false): Boolean {
        val lockKey = Pair(guildId, vaultIndex)

        // 1. 检查本地内存锁
        if (vaultLocks.containsKey(lockKey)) {
            val occupantUuid = vaultLocks[lockKey]
            if (occupantUuid != player.uniqueId) {
                player.sendMessage(plugin.langManager.get("vault-locked", "index" to vaultIndex.toString()))
                return false
            }
            return true
        }

        // 2. 检查跨服锁（如果开启了代理模式）
        if (plugin.config.getBoolean("proxy", false) && !isFromNetwork) {
            val isLocked = plugin.dbManager.isVaultLocked(guildId, vaultIndex)
            if (isLocked) {
                player.sendMessage(plugin.langManager.get("vault-locked", "index" to vaultIndex.toString()))
                return false
            }
        }

        // 3. 写入本地锁
        vaultLocks[lockKey] = player.uniqueId

        // 4. 跨服同步：如果不是从网络收到的，就发给其他服
        if (!isFromNetwork && plugin.config.getBoolean("proxy")) {
            sendVaultSyncPacket(guildId, vaultIndex, player.uniqueId, "Lock")
        }

        return true
    }

    /**
     * 玩家退出时，清理其持有的所有锁并同步到跨服
     */
    fun clearAllLocksByPlayer(playerUuid: UUID) {
        // 找出所有由该玩家持有的仓库锁
        val locksToRelease = vaultLocks.filterValues { it == playerUuid }.keys.toList()

        for (lockKey in locksToRelease) {
            val guildId = lockKey.first
            val index = lockKey.second

            // 1. 本地移除
            vaultLocks.remove(lockKey)

            // 2. 跨服通知解锁 (必须发送同步包)
            if (plugin.config.getBoolean("proxy")) {
                sendVaultSyncPacket(guildId, index, playerUuid, "Unlock")
            }

            plugin.logger.info(plugin.langManager.get("vault-unlocked", "id" to guildId.toString(), "index" to index.toString()))

        }
    }

    /**
     * 强制重置所有锁
     */
    fun forceResetAllLocks() {
        vaultLocks.clear() // 清空本地
        if (plugin.config.getBoolean("proxy")) {
            // 发送一个特殊的全局解锁包
            sendVaultSyncPacket(0, 0, UUID.randomUUID(), "ForceUnlockAll")
        }
    }

    /**
     * 释放仓库锁
     */
    fun releaseVaultLock(guildId: Int, vaultIndex: Int, isFromNetwork: Boolean = false) {
        val lockKey = Pair(guildId, vaultIndex)
        if (vaultLocks.containsKey(lockKey)) {
            vaultLocks.remove(lockKey)

            // 如果开启了跨服模式且不是来自网络的指令，则广播解锁
            if (!isFromNetwork && plugin.config.getBoolean("proxy")) {
                sendVaultSyncPacket(guildId, vaultIndex, UUID.randomUUID(), "Unlock")
            }
        }
    }
    /**
     * 同步远程仓库锁
     */
    fun syncRemoteLock(guildId: Int, index: Int, uuid: UUID) {
        vaultLocks[Pair(guildId, index)] = uuid
    }
    /**
     * 发送跨服同步封包
     */
    private fun sendVaultSyncPacket(guildId: Int, index: Int, uuid: UUID, type: String) {
        // 只有在 proxy 模式下才发送
        if (!plugin.config.getBoolean("proxy")) return

        val out = com.google.common.io.ByteStreams.newDataOutput()
        try {
            out.writeUTF("VaultSync") // 子频道名称
            out.writeUTF(type)        // "Lock" 或 "Unlock"
            out.writeInt(guildId)     // 公会 ID
            out.writeInt(index)       // 仓库编号
            out.writeUTF(uuid.toString()) // 操作者 UUID

            // 获取一个在线玩家作为传输管道发送消息
            val messenger = plugin.server.onlinePlayers.firstOrNull()
            messenger?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
        } catch (e: Exception) {
            plugin.logger.warning(plugin.langManager.get("error-send-packet", "error" to e.message.toString()))
        }
    }

    /**
     * 管理员打开仓库
     */
    fun adminOpenVault(admin: Player, guildId: Int, vaultIndex: Int) {
        val lang = plugin.langManager
        if (vaultIndex !in 1..9) {
            admin.sendMessage(plugin.langManager.get("error-invalid-vault-index"))
            return
        }

        // 1. 内存锁初步检查 (本服独占)
        if (!tryLockVault(guildId, vaultIndex, admin)) return

        // 2. 异步执行跨服抢锁
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 使用 tryGrabLock 尝试在数据库层面锁定
            // 如果物理锁未过期且 last_editor 不是自己，会返回 false
            val success = plugin.dbManager.tryGrabLock(guildId, vaultIndex, admin.uniqueId)

            if (!success) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    // 抢锁失败，立即释放内存锁
                    vaultLocks.remove(Pair(guildId, vaultIndex))
                    admin.sendMessage(lang.get("vault-locked", "index" to vaultIndex.toString()))
                })
                return@Runnable
            }

            // 3. 抢锁成功后，读取最新的数据副本
            val rawData = plugin.dbManager.getVaultData(guildId, vaultIndex)

            plugin.server.scheduler.runTask(plugin, Runnable {
                val holder = VaultHolder(guildId, vaultIndex)
                val inv = plugin.server.createInventory(
                    holder,
                    54,
                    plugin.langManager.get("admin-vault-title",
                        "index" to vaultIndex.toString(),
                        "id" to guildId.toString())
                )
                holder.setInventory(inv)

                if (!rawData.isNullOrEmpty()) {
                    inv.contents = SerializationUtil.itemStackArrayFromBase64(rawData)
                }

                admin.openInventory(inv)

                // 4. 开启续租任务，确保在关闭前锁不会过期
                holder.leaseTask = startVaultLeaseTask(admin, guildId, vaultIndex)
            })
        })
    }
    /**
     * 开启续租任务
     * @return 返回 BukkitTask 对象以便后续手动取消
     */
    fun startVaultLeaseTask(player: Player, guildId: Int, index: Int): BukkitTask {
        return plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            // 检查玩家是否还在看这个 GUI
            val topInv = player.openInventory.topInventory
            val holder = topInv.holder as? VaultHolder ?: return@Runnable

            if (holder.guildId != guildId || holder.vaultIndex != index) return@Runnable

            // 异步更新数据库过期时间，维持“我是锁定者”的状态
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                val nextExpire = System.currentTimeMillis() + 30000
                val sql = "UPDATE guild_vaults SET lock_expire = ? WHERE guild_id = ? AND vault_index = ? AND last_editor = ?"
                plugin.dbManager.dataSource?.connection?.use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setLong(1, nextExpire)
                        ps.setInt(2, guildId)
                        ps.setInt(3, index)
                        ps.setString(4, player.uniqueId.toString())
                        ps.executeUpdate()
                    }
                }
            })
        }, 100L, 100L) // 每 5 秒续租一次
    }

    /**
     * 设置公会图标 (消耗公会资金版)
     */
    fun setGuildIcon(player: Player, materialName: String, callback: (OperationResult) -> Unit) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查 (会长或副会长)
        val role = plugin.dbManager.getPlayerRole(player.uniqueId)
        if (role != "OWNER" && role != "ADMIN") {
            return callback(OperationResult.NoPermission)
        }

        // 2. 异步处理
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 3. 获取费用并检查公会银行
            val cost = plugin.config.getDouble("balance.seticon", 1000.0)
            val guild = plugin.dbManager.getGuildById(guildId)

            if (guild == null || guild.balance < cost) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(
                        plugin.langManager.get(
                            "error-balance-not-enough",
                            "cost" to cost.toString(),
                            "balance" to (guild?.balance ?: 0.0).toString()
                        )
                    ))
                })
                return@Runnable
            }

            // 4. 执行数据库更新
            if (plugin.dbManager.updateGuildIcon(guildId, materialName)) {

                // --- 核心扣费与记录细化日志 ---
                plugin.dbManager.updateGuildBalance(guildId, -cost)
                plugin.dbManager.logBankTransaction(guildId, player.name, "SET_ICON", cost)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Success)
                })
            } else {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-database")))
                })
            }
        })
    }

    /**
     * 修改公会公告 (消耗公会资金版)
     */
    fun setGuildMotd(player: Player, content: String, callback: (OperationResult) -> Unit) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查 (会长或副会长)
        val role = plugin.dbManager.getPlayerRole(player.uniqueId)
        if (role != "OWNER" && role != "ADMIN") {
            return callback(OperationResult.NoPermission)
        }

        // 2. 异步处理
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 3. 获取费用并检查公会银行
            val cost = plugin.config.getDouble("balance.motd", 100.0)
            val guild = plugin.dbManager.getGuildById(guildId)

            if (guild == null || guild.balance < cost) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(
                        plugin.langManager.get(
                            "error-balance-not-enough",
                            "cost" to cost.toString(),
                            "balance" to (guild?.balance ?: 0.0).toString()
                        )
                    ))
                })
                return@Runnable
            }

            // 4. 执行数据库更新
            if (plugin.dbManager.updateGuildAnnouncement(guildId, content)) {

                // --- 核心扣费与记录细化日志 ---
                plugin.dbManager.updateGuildBalance(guildId, -cost)
                plugin.dbManager.logBankTransaction(guildId, player.name, "SET_MOTD", cost)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Success)
                })
            } else {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-database")))
                })
            }
        })
    }

    /**
     * 检查是否有新的申请，如果有则通知玩家
     * @param player 玩家对象
     */
    fun checkAndNotifyRequests(player: Player) {
        // 从缓存获取玩家公会 ID
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 使用 isStaff 方法检查权限
            if (!plugin.dbManager.isStaff(player.uniqueId, guildId)) return@Runnable

            // 2. 获取该公会的详细申请列表
            val requests = plugin.dbManager.getDetailedRequests(guildId)
            if (requests.isEmpty()) return@Runnable

            // 3. 获取公会数据（用于显示公会名）
            val guildData = plugin.dbManager.getGuildData(guildId) ?: return@Runnable

            // 4. 回到主线程发送消息
            plugin.server.scheduler.runTask(plugin, Runnable {
                requests.forEach { (playerName, _, _) ->
                    val msg = plugin.langManager.get("notify-new-request")
                        .replace("%player%", playerName)
                        .replace("%guild%", guildData.name)
                    player.sendMessage(msg)
                }
            })
        })
    }

    /**
     * 升级公会
     * @param player 玩家对象
     * @param callback 回调函数，用于处理结果
     */
    fun upgradeGuild(player: Player, callback: (OperationResult) -> Unit) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.Error(plugin.langManager.get("error-no-guild")))

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 权限检查
            if (!plugin.dbManager.isStaff(player.uniqueId, guildId)) {
                return@Runnable syncCallback(callback, OperationResult.Error(plugin.langManager.get("error-no-permission")))
            }

            val guildData = plugin.dbManager.getGuildById(guildId) ?: return@Runnable
            val nextLevel = guildData.level + 1

            // 2. 检查配置中是否存在下一级
            val levelSection =
                plugin.config.getConfigurationSection("level.$nextLevel") ?: return@Runnable syncCallback(
                    callback,
                    OperationResult.Error("§c公会已达到最高等级！")
                )

            // 3. 检查经验值
            val needExp = levelSection.getInt("need-exp")
            if (guildData.exp < needExp) {
                return@Runnable syncCallback(callback, OperationResult.Error("§c经验值不足！升级需要 $needExp 经验（当前: ${guildData.exp}）"))
            }

            // 4. 获取新等级的属性
            val maxMembers = levelSection.getInt("max-members")

            // 5. 执行更新
            if (plugin.dbManager.updateGuildLevel(guildId, nextLevel, maxMembers)) {
                syncCallback(callback, OperationResult.Success)
                // 全服广播或公会消息
                plugin.server.broadcastMessage("§8[§b公会§8] §f${guildData.name} §7成功升级到了等级 §f$nextLevel§7！")
            } else {
                syncCallback(callback, OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 修改公会经验值
     * @param sender 命令发送者
     * @param guildId 公会 ID
     * @param type 类型（add/take/set）
     * @param amount 数量
     */
    fun adminModifyExp(sender: CommandSender, guildId: Int, type: String, amount: Int) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guild = plugin.dbManager.getGuildById(guildId)
            if (guild == null) {
                sender.sendMessage("§c未找到 ID 为 #$guildId 的公会")
                return@Runnable
            }

            val success = when (type.lowercase()) {
                "add" -> plugin.dbManager.updateGuildExp(guildId, amount, false)
                "take" -> plugin.dbManager.updateGuildExp(guildId, -amount, false)
                "set" -> plugin.dbManager.updateGuildExp(guildId, amount, true)
                else -> false
            }

            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    val newExp = if (type == "set") amount else (guild.exp + if (type == "add") amount else -amount)
                    sender.sendMessage("§a成功将公会 §f${guild.name} §a的经验值修改为 §f$newExp")
                } else {
                    sender.sendMessage("§c数据库操作失败，请检查后台。")
                }
            })
        })
    }

    // 辅助方法：确保回调在主线程执行
    private fun syncCallback(callback: (OperationResult) -> Unit, result: OperationResult) {
        plugin.server.scheduler.runTask(plugin, Runnable { callback(result) })
    }
}