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
     * 核心业务：踢出成员
     */
    fun kickMember(sender: Player, targetName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId)
                ?: return@Runnable callback(OperationResult.NotInGuild)

            val myRole = plugin.dbManager.getPlayerRole(sender.uniqueId)
            if (myRole != "OWNER" && myRole != "ADMIN") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            val targetOffline = plugin.server.getOfflinePlayer(targetName)
            val targetRole = plugin.dbManager.getRoleInGuild(guildId, targetOffline.uniqueId)

            when {
                targetRole == null -> callback(OperationResult.Error(plugin.langManager.get("kick-failed")))
                targetRole == "OWNER" -> callback(OperationResult.Error(plugin.langManager.get("kick-cannot-owner")))
                myRole == "ADMIN" && targetRole == "ADMIN" -> callback(OperationResult.Error(plugin.langManager.get("kick-admin-limit")))
                else -> {
                    if (plugin.dbManager.removeMember(guildId,targetOffline.uniqueId)) {
                        plugin.playerGuildCache.remove(targetOffline.uniqueId)
                        callback(OperationResult.Success)
                    }
                }
            }
        })
    }
    /**
     * 邀请玩家 (支持跨服提示)
     */
    fun invitePlayer(sender: Player, targetName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId)
                ?: return@Runnable callback(OperationResult.NotInGuild)

            // 权限检查
            val role = plugin.dbManager.getPlayerRole(sender.uniqueId)
            if (role != "OWNER" && role != "ADMIN") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            val guildData = plugin.dbManager.getGuildData(guildId) ?: return@Runnable

            // 逻辑：向全服/全网络发送邀请
            plugin.server.scheduler.runTask(plugin, Runnable {
                val out = com.google.common.io.ByteStreams.newDataOutput()
                out.writeUTF("CrossInvite")
                out.writeUTF(targetName)
                out.writeInt(guildId)
                out.writeUTF(guildData.name)
                sender.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
                callback(OperationResult.Success)
            })
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

            // 这里不再报错，因为我们在 DBManager 补了方法
            val guildId = plugin.dbManager.getGuildIdByName(guildName)
                ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("join-guild-not-found")))

            val currentRequests = plugin.dbManager.getRequests(guildId)
            if (currentRequests.any { it.first == player.uniqueId }) {
                callback(OperationResult.Error(plugin.langManager.get("join-already-requested")))
                return@Runnable
            }

            if (plugin.dbManager.addRequest(guildId, player.uniqueId, player.name)) {
                callback(OperationResult.Success)
            }
        })
    }

    /**
     * 接受申请逻辑 (含 UUID 转换)
     */
    fun acceptRequest(sender: Player, targetName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable callback(OperationResult.NotInGuild)

            // 关键：在申请列表中通过名字匹配 UUID
            val requests = plugin.dbManager.getRequests(guildId)
            val targetUuid = requests.find { (uuid, _) ->
                plugin.server.getOfflinePlayer(uuid).name?.equals(targetName, true) == true
            }?.first ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("accept-failed")))

            if (plugin.dbManager.addMember(guildId, targetUuid, targetName, "MEMBER")) {
                plugin.dbManager.removeRequest(guildId, targetUuid) // 这里传 UUID，匹配 DB 定义
                plugin.playerGuildCache[targetUuid] = guildId
                callback(OperationResult.Success)
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

            // 从数据库移除
            if (plugin.dbManager.removeMember(guildId, player.uniqueId)) {
                // 清理本服缓存
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
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 先看缓存里有没有公会ID，减少数据库查询
            val guildId = plugin.playerGuildCache[player.uniqueId] ?: return@Runnable

            // 构造插件消息包
            val out = com.google.common.io.ByteStreams.newDataOutput()
            out.writeUTF("Chat")           // 子频道
            out.writeInt(guildId)          // 目标公会ID
            out.writeUTF(player.name)      // 发送者名字
            out.writeUTF(message)          // 消息内容

            // 发送插件消息，PluginMessageListener 会在全服接收并转发
            player.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
        })
    }
}