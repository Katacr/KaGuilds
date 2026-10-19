package org.katacr.kaguilds.service

import org.bukkit.command.CommandSender
import org.katacr.kaguilds.KaGuilds
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.katacr.kaguilds.model.GuildData
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import org.katacr.kaguilds.util.MessageUtil
import java.io.DataOutputStream

class GuildService(private val plugin: KaGuilds) {
    data class GuildInfo(
        val data: GuildData,
        val memberNames: List<String>,
        val onlineCount: Int
    )
    // 定义待处理动作类型
    sealed class PendingAction {
        data class Create(val guildName: String) : PendingAction()
        data class Transfer(val targetName: String) : PendingAction()
        data class Rename(val newName: String) : PendingAction()
        data class Kick(val targetName: String) : PendingAction()
        object Delete : PendingAction()
        object Leave : PendingAction()
    }

    // 记录玩家 UUID 和对应的 BukkitTask
    private val teleportTasks = java.util.concurrent.ConcurrentHashMap<UUID, BukkitTask>()
    // 缓存：UUID -> 动作
    private val pendingConfirmations = java.util.concurrent.ConcurrentHashMap<UUID, PendingAction>()
    // 锁：Pair(公会ID, 仓库编号) -> 使用者的 UUID
    val vaultLocks: MutableMap<Pair<Int, Int>, UUID>
        get() = plugin.vaultService.vaultLocks
    // 缓存：公会ID -> 成员玩家名列表（用于 Tab 补全）
    private val guildMemberNamesCache = java.util.concurrent.ConcurrentHashMap<Int, List<String>>()

    /**
     * 获取公会详细展示信息
     */
    fun getDetailedInfo(player: Player, callback: (OperationResult, GuildInfo?) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 获取玩家所属公会 ID
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)
            if (guildId == null) {
                callback(OperationResult.NotInGuild, null)
                return@Runnable
            }

            // 2. 获取公会基础数据
            val data = plugin.dbManager.guildRepository.getGuildData(guildId)
            if (data == null) {
                callback(OperationResult.Error(plugin.langManager.get("error-database")), null)
                return@Runnable
            }

            // 3. 获取成员名称列表
            val uuids = plugin.dbManager.memberRepository.getMemberUUIDs(guildId)
            var online = 0
            val isProxy = plugin.isProxyEnabled()

            val names = uuids.map { uuid ->
                if (isProxy) {
                    // 代理模式：使用跨服在线玩家缓存判断
                    val playerName = plugin.server.getOfflinePlayer(uuid).name ?: "未知"
                    if (plugin.crossServerOnlinePlayers.containsKey(playerName)) {
                        online++
                        "§a${playerName}" // 在线显示绿色
                    } else {
                        // 即使不在跨服缓存中，也检查本地服务器
                        val p = plugin.server.getPlayer(uuid)
                        if (p != null && p.isOnline) {
                            online++
                            "§a${p.name}"
                        } else {
                            "§7${playerName}" // 离线显示灰色
                        }
                    }
                } else {
                    // 单服模式：只检查本地服务器
                    val p = plugin.server.getPlayer(uuid)
                    if (p != null && p.isOnline) {
                        online++
                        "§a${p.name}" // 在线显示绿色
                    } else {
                        "§7${plugin.server.getOfflinePlayer(uuid).name ?: "未知"}" // 离线显示灰色
                    }
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
    fun createGuild(player: Player, guildName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        // 1. 获取配置
        val cost = plugin.config.getDouble("balance.create", 10000.0)

        // 2. 异步处理数据库检查
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 检查玩家状态
            if (plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId) != null) {
                callback(OperationResult.AlreadyInGuild)
                return@Runnable
            }

            // 检查公会名是否存在
            if (plugin.dbManager.guildRepository.isNameExists(guildName)) {
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
                    val guildId = plugin.dbManager.guildLifecycleRepository.createGuild(guildName, player.uniqueId, player.name)
                    if (guildId != -1) {
                        plugin.playerGuildCache[player.uniqueId] = guildId
                        plugin.taskManager.activatePlayerTasks(player.uniqueId, guildId)

                        callback(OperationResult.Success)
                    } else {
                        // 物理失败：退款（检查结果，失败则记录严重日志便于人工补偿）
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            val refund = econ.depositPlayer(player, cost)
                            if (!refund.transactionSuccess()) {
                                plugin.logger.severe("[退款失败] 玩家 ${player.name} 创建公会扣款 $cost 未成功建会且退款失败，请人工补偿。")
                            }
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
    fun deleteGuild(player: Player, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)
                ?: return@Runnable callback(OperationResult.NotInGuild)

            val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)
            if (role != "OWNER") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            // 先取成员 UUID（删除前），用于跨服精确通知其他服的成员
            val memberUuids = plugin.dbManager.memberRepository.getMemberUUIDs(guildId)

            if (plugin.dbManager.guildLifecycleRepository.deleteGuild(guildId)) {
                // 清理公会成员名称缓存
                clearGuildMemberNamesCache(guildId)

                // 本服先处理：通知其他在线成员（发起人由命令层提示 delete-success），再清理缓存/退出聊天
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val notifyMsg = plugin.langManager.get("delete-notify")
                    plugin.server.onlinePlayers.forEach { p ->
                        if (p.uniqueId != player.uniqueId && memberUuids.contains(p.uniqueId)) {
                            p.sendMessage(notifyMsg)
                        }
                    }
                    clearLocalGuildState(guildId)
                })

                // 跨服额外广播：携带成员 UUID，其他服按 UUID 精确通知并清缓存
                if (plugin.isProxyEnabled()) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        broadcastGuildDeleted(guildId, memberUuids, "delete-notify")
                    })
                }

                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 广播公会解散给其他子服（携带成员 UUID，接收端按 UUID 精确通知）。
     * @param notifyKey 通知文案的 lang 键（玩家解散用 delete-notify，管理员解散用 admin-delete-notify）
     */
    private fun broadcastGuildDeleted(guildId: Int, memberUuids: List<UUID>, notifyKey: String = "delete-notify") {
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeDeleteSync(guildId, notifyKey, memberUuids))
    }

    /**
     * 踢出公会成员
     */
    fun kickMember(admin: Player, targetName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.playerGuildCache[admin.uniqueId] ?: return@Runnable callback(OperationResult.NotInGuild)

            // 1. 权限检查
            val role = plugin.dbManager.memberRepository.getPlayerRole(admin.uniqueId)
            if (role != "OWNER" && role != "ADMIN") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            // 2. 获取目标玩家的 UUID 和资料
            val targetUuid = plugin.dbManager.memberRepository.getUuidByPlayerName(targetName)
                ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("kick-failed")))

            // 3. 检查目标是否在该公会
            if (plugin.dbManager.memberRepository.getGuildIdByPlayer(targetUuid) != guildId) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("kick-not-in-your-guild")))
            }

            // 4. 检查职位（管理员不能踢会长，管理员不能踢管理员）
            val targetRole = plugin.dbManager.memberRepository.getPlayerRole(targetUuid)
            if (targetRole == "OWNER" || (role == "ADMIN" && targetRole == "ADMIN")) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("kick-role-limit")))
            }

            // 5. 执行踢出
            if (plugin.dbManager.memberRepository.removeMember(guildId, targetUuid)) {

                // --- 核心逻辑：跨服同步与通知 ---
                // A. 更新公会成员名称缓存
                updateGuildMemberNamesCache(guildId)
                clearLocalPlayerGuildState(targetUuid)

                // B. 同步全服缓存 (让所有子服知道该玩家已没公会)
                syncPlayerCache(targetUuid, -1)

                // C. 发送踢出通知（身份用 UUID，名称仅显示）
                dispatchKickNotification(guildId, targetUuid, targetName)

                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 内部工具：分发踢出通知
     */
    private fun dispatchKickNotification(guildId: Int, targetUuid: UUID, targetName: String) {
        // 本地先处理（单服/跨服都必须），再在 proxy 可用时额外广播给其他服
        plugin.server.scheduler.runTask(plugin, Runnable {
            sendLocalKickMessage(guildId, targetUuid, targetName)
        })
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeMemberKick(guildId, targetUuid, targetName))
    }

    /**
     * 单服逻辑：发送踢出通知
     */
    private fun sendLocalKickMessage(guildId: Int, targetUuid: UUID, targetName: String) {
        val kickMsg = plugin.langManager.get("member-kick-notify", "player" to targetName)
        val targetPrivateMsg = plugin.langManager.get("kick-notice-to-target")

        plugin.server.onlinePlayers.forEach { p ->
            // 1. 通知公会内成员
            if (plugin.membershipCache.getGuildId(p) == guildId) {
                p.sendMessage(kickMsg)
            }
            // 2. 专门给被踢的玩家发私信（按 UUID 精确匹配，名称仅显示）
            if (p.uniqueId == targetUuid) {
                p.sendMessage(targetPrivateMsg)
                p.playSound(p.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            }
        }
    }
    /**
     * 邀请玩家加入公会
     */
    fun invitePlayer(sender: Player, targetName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 权限与状态预检 (本地校验)
            val guildId = plugin.playerGuildCache[sender.uniqueId]
                ?: return@Runnable callback(OperationResult.NotInGuild)

            val role = plugin.dbManager.memberRepository.getPlayerRole(sender.uniqueId)
            if (role != "OWNER" && role != "ADMIN") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            // 2. 不能邀请自己
            if (sender.name.equals(targetName, ignoreCase = true)) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("invite-self-limit")))
            }

            val guildData = plugin.dbManager.guildRepository.getGuildData(guildId) ?: return@Runnable

            // 1. 本地优先：目标玩家若在本服，直接处理（单服/跨服都走这条，保证本服一定可用）
            val targetPlayer = plugin.server.getPlayer(targetName)
            if (targetPlayer != null) {
                // 检查目标是否有公会
                if (plugin.dbManager.memberRepository.getGuildIdByPlayer(targetPlayer.uniqueId) != null) {
                    callback(OperationResult.Error(plugin.langManager.get("join-already-in-guild")))
                    return@Runnable
                }
                // 存入邀请缓存并通知
                plugin.inviteCache[targetPlayer.uniqueId] = guildId
                val lang = plugin.langManager
                val msg = MessageUtil.createText(lang.get("invite-received-target",
                    "player" to sender.name, "guild" to guildData.name))
                val acceptBtn = MessageUtil.createClickableText(
                    text = lang.get("invite-accept-btn"),
                    hoverText = lang.get("invite-accept-btn-hover"),
                    command = "/kg yes"
                )
                val space = MessageUtil.createText(" ")
                val denyBtn = MessageUtil.createClickableText(
                    text = lang.get("invite-deny-btn"),
                    hoverText = lang.get("invite-deny-btn-hover"),
                    command = "/kg no"
                )
                msg.addExtra(acceptBtn)
                msg.addExtra(space)
                msg.addExtra(denyBtn)
                targetPlayer.spigot().sendMessage(msg)
                callback(OperationResult.Success)
                return@Runnable
            }

            // 2. 目标不在本服：跨服模式发包给其他服处理；单服模式则目标必定在线，已在上处理
            if (plugin.isProxyEnabled()) {
                val targetUuid = plugin.dbManager.memberRepository.getUuidByPlayerName(targetName)
                if (targetUuid != null && plugin.dbManager.memberRepository.getGuildIdByPlayer(targetUuid) != null) {
                    callback(OperationResult.Error(plugin.langManager.get("join-already-in-guild")))
                    return@Runnable
                }
                // 身份用 UUID（目标可能从未进过本服，名称仅作显示）；
                // 若无法解析 UUID（目标从未进过任何服），跨服邀请无意义，直接提示。
                if (targetUuid == null) {
                    callback(OperationResult.Error(plugin.langManager.get("player-not-online")))
                    return@Runnable
                }
                plugin.networkBus.send(
                    org.katacr.kaguilds.util.NetworkCodec.encodeCrossInvite(
                        targetUuid, guildId, guildData.name, sender.name
                    )
                )
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("player-not-online")))
            }
        })
    }

    /**
     * 申请加入公会 (支持名称或 #ID)
     */
    fun requestJoin(player: Player, input: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查是否已在公会
            if (plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId) != null) {
                callback(OperationResult.AlreadyInGuild)
                return@Runnable
            }

            // 2. 解析 ID 或 名称
            val guildId: Int
            val guildName: String

            if (input.startsWith("#")) {
                val id = input.substring(1).toIntOrNull() ?: -1
                val data = plugin.dbManager.guildRepository.getGuildById(id)
                if (data == null) {
                    callback(OperationResult.Error(plugin.langManager.get("join-guild-not-found")))
                    return@Runnable
                }
                guildId = data.id
                guildName = data.name
            } else {
                guildId = plugin.dbManager.guildRepository.getGuildIdByName(input)
                guildName = input
            }

            if (guildId == -1) {
                callback(OperationResult.Error(plugin.langManager.get("join-guild-not-found")))
                return@Runnable
            }

            // 3. 检查重复申请
            val currentRequests = plugin.dbManager.requestRepository.getRequests(guildId)
            if (currentRequests.any { it.first == player.uniqueId }) {
                callback(OperationResult.Error(plugin.langManager.get("join-already-requested")))
                return@Runnable
            }

            // 4. 添加申请并通知
            if (plugin.dbManager.requestRepository.addRequest(guildId, player.uniqueId, player.name)) {
                dispatchNotifyRequest(guildId, guildName, player.name)
                callback(OperationResult.Success)
            }
        })
    }

    /**
     * 内部工具：发送公会通知（单服/跨服统一）。
     *
     * 设计：永远先在本服处理（本地公会成员立即收到），再在 proxy 可用时
     * 额外广播给其他服。这样无论单服还是跨服，本服功能都 100% 正常，
     * 不会因为代理未转发/字段顺序而静默失效。
     */
    /**
     * 分发公会聊天：本地先按已解析的着色消息发送，再广播给其他服。
     */
    private fun dispatchChat(targetGuildId: Int, senderName: String, msgContent: String, roleDisplayName: String, coloredMessage: String) {
        // 本地先处理：发送端已解析并着色，直接发给本服成员
        sendToLocalGuildMembers(targetGuildId) { p -> p.sendMessage(coloredMessage) }
        // 再广播给其他服
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeChat(targetGuildId, senderName, msgContent, roleDisplayName, coloredMessage))
    }

    /**
     * 分发入会申请通知：本地先通知管理员，再广播给其他服。
     */
    private fun dispatchNotifyRequest(targetGuildId: Int, guildName: String, applicantName: String) {
        sendNotifyRequestLocal(targetGuildId, applicantName)
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeNotifyRequest(targetGuildId, guildName, applicantName))
    }

    /**
     * 向本服属于指定公会的在线玩家执行动作。使用 membershipCache 判断身份（带查库兜底）。
     */
    private fun sendToLocalGuildMembers(guildId: Int, action: (org.bukkit.entity.Player) -> Unit) {
        plugin.server.onlinePlayers.forEach { p ->
            val cached = plugin.membershipCache.getGuildId(p)
            if (cached == guildId) action(p)
        }
    }

    /**
     * 本服发送入会申请通知给管理员（OWNER/ADMIN）。
     */
    private fun sendNotifyRequestLocal(guildId: Int, applicantName: String) {
        val lang = plugin.langManager
        plugin.server.onlinePlayers.forEach { p ->
            val cached = plugin.membershipCache.getGuildId(p)
            if (cached == guildId) {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val role = plugin.dbManager.memberRepository.getPlayerRole(p.uniqueId)
                    if (role == "OWNER" || role == "ADMIN") {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            val msg = MessageUtil.createText(lang.get("notify-new-request", "player" to applicantName))
                            val viewBtn = MessageUtil.createClickableText(
                                text = lang.get("notify-view-btn"),
                                hoverText = lang.get("notify-view-btn-hover"),
                                command = "/kg requests"
                            )
                            msg.addExtra(viewBtn)
                            p.spigot().sendMessage(msg)
                            p.playSound(p.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                        })
                    }
                })
            }
        }
    }

    /**
     * 本服按聊天格式解析并发送给公会成员。
     */
    private fun sendLocalChat(guildId: Int, senderName: String, msgContent: String, roleDisplayName: String) {
        var format = plugin.config.getString("guild.chat-format", "&7[&e公会聊天&7] &f{player}&f: {message}") ?: ""
        format = format.replace("{player}", senderName)
            .replace("{message}", msgContent)
            .replace("{role}", roleDisplayName)

        plugin.server.scheduler.runTask(plugin, Runnable {
            val papiPlugin = plugin.server.pluginManager.getPlugin("PlaceholderAPI")
            val formattedMessage = if (papiPlugin != null) {
                val sender = plugin.server.getPlayer(senderName)
                me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, format)
            } else {
                format
            }
            val coloredMessage = org.bukkit.ChatColor.translateAlternateColorCodes('&', formattedMessage)
            sendToLocalGuildMembers(guildId) { p -> p.sendMessage(coloredMessage) }
        })
    }
    /**
     * 接受公会申请 (由管理员执行 /kg accept <玩家>)
     */
    fun acceptRequest(admin: Player, targetName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.playerGuildCache[admin.uniqueId] ?: return@Runnable callback(OperationResult.NotInGuild)

            // 1. 权限检查
            val role = plugin.dbManager.memberRepository.getPlayerRole(admin.uniqueId)
            if (role != "OWNER" && role != "ADMIN") {
                return@Runnable callback(OperationResult.NoPermission)
            }

            // 2. 获取申请列表并匹配目标名字
            val requests = plugin.dbManager.requestRepository.getRequests(guildId)
            val targetPair = requests.find { (uuid, _) ->
                plugin.server.getOfflinePlayer(uuid).name?.equals(targetName, ignoreCase = true) == true
            }

            if (targetPair == null) {
                callback(OperationResult.Error(plugin.langManager.get("request-not-found")))
                return@Runnable
            }

            val targetUuid = targetPair.first

            // 3. 检查目标是否已加入其他公会
            if (plugin.dbManager.memberRepository.getGuildIdByPlayer(targetUuid) != null) {
                plugin.dbManager.requestRepository.removeRequest(guildId, targetUuid)
                callback(OperationResult.Error(plugin.langManager.get("join-already-in-other")))
                return@Runnable
            }

            // 4. 执行添加成员
            if (plugin.dbManager.memberRepository.addMember(guildId, targetUuid, targetName, "MEMBER")) {
                plugin.dbManager.requestRepository.removeRequest(guildId, targetUuid)

                plugin.playerGuildCache[targetUuid] = guildId
                plugin.taskManager.activatePlayerTasks(targetUuid, guildId)

                val isProxy = plugin.isProxyEnabled()
                if (isProxy) {
                    syncPlayerCache(targetUuid, guildId) // 告诉其他子服同步缓存
                    broadcastMemberStatus(guildId, targetUuid, targetName, "JOIN") // 告诉其他子服发欢迎消息
                } else {
                    // 单服模式：直接在这里处理通知逻辑
                    val targetPlayer = plugin.server.getPlayer(targetUuid)

                    // 1. 给新人发私信提示 (解决没收到通知问题)
                    targetPlayer?.let {
                        it.sendMessage(plugin.langManager.get("request-accepted-notice"))
                        it.playSound(it.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    }

                    // 2. 调用单服广播给公会成员 (显示热烈欢迎)
                    broadcastMemberStatus(guildId, targetUuid, targetName, "JOIN")
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
    fun denyRequest(sender: Player, targetName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable

            val requests = plugin.dbManager.requestRepository.getRequests(guildId)
            val targetUuid = requests.find { (uuid, _) ->
                plugin.server.getOfflinePlayer(uuid).name?.equals(targetName, true) == true
            }?.first

            if (targetUuid != null) {
                plugin.dbManager.requestRepository.removeRequest(guildId, targetUuid)
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("accept-failed")))
            }
        })
    }
    /**
     * 玩家退出公会逻辑
     */
    fun leaveGuild(player: Player, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)
                ?: return@Runnable callback(OperationResult.NotInGuild)

            // 核心逻辑：会长不能直接退出公会
            val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)
            if (role == "OWNER") {
                callback(OperationResult.Error(plugin.langManager.get("leave-owner-limit")))
                return@Runnable
            }


            if (plugin.dbManager.memberRepository.removeMember(guildId, player.uniqueId)) {

                val playerName = player.name

                // 更新公会成员名称缓存
                updateGuildMemberNamesCache(guildId)

                syncPlayerCache(player.uniqueId, -1)

                broadcastMemberStatus(guildId, player.uniqueId, playerName, "LEAVE")

                clearLocalPlayerGuildState(player.uniqueId)
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
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)
            if (guildId == null) {
                // 同步回主线程发消息
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(plugin.langManager.get("not-in-guild"))
                })
                // 同时清理本地错误的缓存
                clearLocalPlayerGuildState(player.uniqueId)
                return@Runnable
            }

            // 2. 更新缓存
            plugin.membershipCache.set(player.uniqueId, guildId)

            // 3. 获取玩家角色
            val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)
            val roleDisplayName = when (role) {
                "OWNER" -> plugin.langManager.get("papi-role-owner")
                "ADMIN" -> plugin.langManager.get("papi-role-admin")
                else -> plugin.langManager.get("papi-role-member")
            }

            // 4. 构建聊天格式（支持 PAPI 变量）
            var format = plugin.config.getString("guild.chat-format", "&7[&e公会聊天&7] &f{player}&f: {message}") ?: ""
            format = format.replace("{player}", player.name)
                .replace("{message}", message)
                .replace("{role}", roleDisplayName)

            // 5. 解析 PlaceholderAPI 变量（在主线程执行）
            plugin.server.scheduler.runTask(plugin, Runnable {
                val papiPlugin = plugin.server.pluginManager.getPlugin("PlaceholderAPI")
                val formattedMessage = if (papiPlugin != null) {
                    me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, format)
                } else {
                    format
                }

                // 6. 转换颜色代码
                val coloredMessage = org.bukkit.ChatColor.translateAlternateColorCodes('&', formattedMessage)

                // 7. 发送消息：本服先处理，再在 proxy 可用时额外广播给其他服
                dispatchChat(guildId, player.name, message, roleDisplayName, coloredMessage)
            })
        })
    }
    /**
     * 接受公会邀请 (由被邀请人执行 /kg yes)
     */
    fun acceptInvite(player: Player, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val guildId = plugin.inviteCache[player.uniqueId]
        if (guildId == null) {
            callback(OperationResult.Error(plugin.langManager.get("invite-none")))
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查自己是否已经在公会中 (双重检查)
            if (plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId) != null) {
                plugin.inviteCache.remove(player.uniqueId)
                callback(OperationResult.AlreadyInGuild)
                return@Runnable
            }

            // 2. 写入数据库
            if (plugin.dbManager.memberRepository.addMember(guildId, player.uniqueId, player.name, "MEMBER")) {
                // 3. 成功后移除邀请缓存
                plugin.inviteCache.remove(player.uniqueId)

                // 4. 更新本地公会缓存
                plugin.playerGuildCache[player.uniqueId] = guildId
                plugin.taskManager.activatePlayerTasks(player.uniqueId, guildId)

                // 5. 更新公会成员名称缓存
                updateGuildMemberNamesCache(guildId)

                // 6. 跨服同步：通知其他服务器该玩家入会了
                syncPlayerCache(player.uniqueId, guildId)

                // 7. 通知其他服务器该玩家入会了
                broadcastMemberStatus(guildId, player.uniqueId, player.name, "JOIN")

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
    fun syncPlayerCache(uuid: UUID, guildId: Int) {
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeSyncCache(uuid, guildId))
    }
    /**
     * 拒绝公会邀请 (由被邀请人执行 /kg no)
     */
    fun declineInvite(player: Player) {
        plugin.inviteCache.remove(player.uniqueId)
    }

    /**
     * 获取公会成员玩家名列表（带缓存）
     */
    fun getGuildMemberNames(guildId: Int): List<String> {
        // 如果缓存存在，直接返回
        return guildMemberNamesCache.getOrPut(guildId) {
            // 缓存不存在，从数据库加载
            plugin.dbManager.memberRepository.getGuildMemberNames(guildId)
        }
    }

    /**
     * 更新公会成员缓存（在成员变更时调用）
     */
    fun updateGuildMemberNamesCache(guildId: Int) {
        // 从数据库重新加载成员列表
        guildMemberNamesCache[guildId] = plugin.dbManager.memberRepository.getGuildMemberNames(guildId)
    }

    /**
     * 清除公会成员缓存（在公会解散时调用）
     */
    fun clearGuildMemberNamesCache(guildId: Int) {
        guildMemberNamesCache.remove(guildId)
    }

    /**
     * 统一分发成员变动通知 (加入/退出/踢出)。
     * 本服先处理，proxy 可用时额外广播给其他服。
     */
    fun broadcastMemberStatus(guildId: Int, playerUuid: UUID, playerName: String, type: String) {
        // 1. 本服处理
        sendLocalMemberStatus(guildId, playerName, type)

        // 2. 跨服额外广播（身份用 UUID，名称仅显示）
        val out = if (type == "JOIN") {
            org.katacr.kaguilds.util.NetworkCodec.encodeMemberJoin(guildId, playerUuid, playerName)
        } else {
            org.katacr.kaguilds.util.NetworkCodec.encodeMemberLeave(guildId, playerUuid, playerName)
        }
        plugin.networkBus.send(out)
    }

    /**
     * 向本地公会成员发送成员变动通知
     */
    private fun sendLocalMemberStatus(guildId: Int, playerName: String, type: String) {
        val langKey = if (type == "JOIN") "member-join-notify" else "member-leave-notify"
        val msg = plugin.langManager.get(langKey, "player" to playerName)

        sendToLocalGuildMembers(guildId) { p -> p.sendMessage(msg) }
    }

    /**
     * 设置公会传送点 (消耗公会资金版)
     */
    fun setGuildTP(player: Player, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查 (会长或副会长)
        val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)
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
            val guild = plugin.dbManager.guildRepository.getGuildById(guildId)
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
            val serverName = plugin.effectiveServerId
            val loc = player.location
            val locStr = "$serverName:${loc.world?.name},${loc.x},${loc.y},${loc.z},${loc.yaw},${loc.pitch}"

            // 6. 先原子扣费（条件更新，防止并发透支），成功后再写传送点
            if (!plugin.dbManager.bankRepository.withdrawGuildBalanceIfEnough(guildId, cost)) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-balance-not-enough", "cost" to cost.toString(), "balance" to (guild?.balance ?: 0.0).toString())))
                })
                return@Runnable
            }

            if (plugin.dbManager.guildRepository.setGuildLocation(guildId, locStr)) {
                plugin.dbManager.bankRepository.logBankTransaction(guildId, player.name, "SET_TP", cost)

                // 7. 回到主线程执行成功回调
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Success)
                })
            } else {
                // 写传送点失败：退还已扣费用，避免玩家损失
                plugin.dbManager.bankRepository.updateGuildBalance(guildId, cost)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-database")))
                })
            }
        })
    }
    /**
     * 传送到公会点
     */
    fun teleportToGuild(player: Player, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val uuid = player.uniqueId
        val guildId = plugin.playerGuildCache[uuid] ?: return callback(OperationResult.NotInGuild)

        // 如果已经在传送中，不要重复开启
        if (isTeleporting(uuid)) return

        plugin.runAsync {
            val guildData = plugin.dbManager.guildRepository.getGuildData(guildId) ?: return@runAsync
            val locStr = guildData.teleportLocation

            // 1. 检查传送点是否存在
            if (locStr.isNullOrEmpty()) {
                return@runAsync syncCallback(callback, OperationResult.Error(plugin.langManager.get("tp-not-set")))
            }

            // 2. 跨服判断
            val mainParts = locStr.split(":")
            val targetServer = mainParts[0]
            val currentServer = plugin.effectiveServerId

            if (plugin.isProxyEnabled() && targetServer != currentServer) {
                return@runAsync syncCallback(callback, OperationResult.Error(
                    plugin.langManager.get("tp-wrong-server", "server" to targetServer)
                ))
            }

            // 3. 获取等级费用与倒计时设置
            val level = guildData.level
            val cost = plugin.levelsConfig.getDouble("levels.$level.tp-money", 500.0)
            val cooldown = plugin.config.getInt("guild.teleport.cooldown", 3)

            // 4. 回到主线程检查经济并安排传送，Vault 与 Bukkit API 都不能在异步线程调用。
            plugin.runMain {
                if (plugin.economy?.has(player, cost) == false) {
                    callback(OperationResult.Error(plugin.langManager.get("tp-insufficient-money", "cost" to cost.toString())))
                    return@runMain
                }

                // 5. 核心逻辑：判断是否需要等待
                if (cooldown <= 0) {
                    // --- 瞬间传送分支 ---
                    executeFinalTeleport(player, locStr, cost)
                    callback(OperationResult.Success)
                } else {
                    // --- 延迟传送分支 ---
                    player.sendMessage(plugin.langManager.get("tp-starting", "time" to cooldown.toString()))

                    val task = plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        executeFinalTeleport(player, locStr, cost)
                        teleportTasks.remove(uuid)
                        callback(OperationResult.Success)
                    }, cooldown * 20L)

                    teleportTasks[uuid] = task
                }
            }
        }
    }

    /**
     * 内部私有方法：执行扣费和物理传送
     */
    private fun executeFinalTeleport(player: Player, locStr: String, cost: Double) {
        // 扣费：检查结果，失败则中止传送（避免免费传送）
        val economy = plugin.economy
        if (economy != null) {
            val response = economy.withdrawPlayer(player, cost)
            if (!response.transactionSuccess()) {
                player.sendMessage(plugin.langManager.get("tp-insufficient-money", "cost" to cost.toString()))
                return
            }
        }

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
    fun renameGuild(player: Player, newName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查 (仅限会长)
        val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)
        if (role != "OWNER") {
            return callback(OperationResult.Error(plugin.langManager.get("rename-only-owner")))
        }

        // 2. 名字合法性检查
        if (newName.length > 16 || newName.isEmpty()) {
            return callback(OperationResult.Error(plugin.langManager.get("create-invalid-name")))
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 3. 检查名字是否已存在
            val existingId = plugin.dbManager.guildRepository.getGuildIdByName(newName)
            if (existingId != -1 && existingId != guildId) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("create-name-exists")))
                })
                return@Runnable
            }

            // 4. 获取公会数据并检查公会银行经济
            val guild = plugin.dbManager.guildRepository.getGuildById(guildId)
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

            // 5. 先原子扣费（条件更新，防止并发透支），成功后再改名
            if (!plugin.dbManager.bankRepository.withdrawGuildBalanceIfEnough(guildId, cost)) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-balance-not-enough", "cost" to cost.toString(), "balance" to (guild?.balance ?: 0.0).toString())))
                })
                return@Runnable
            }

            if (plugin.dbManager.guildRepository.renameGuild(guildId, newName)) {

                plugin.dbManager.bankRepository.logBankTransaction(guildId, player.name, "RENAME", cost)

                // 本地先处理，再在 proxy 可用时额外广播
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val renameMsg = plugin.langManager.get("rename-success-notify", "name" to newName)
                    sendToLocalGuildMembers(guildId) { p -> p.sendMessage(renameMsg) }
                })
                syncRenameProxy(guildId, newName)

                callback(OperationResult.Success)
            } else {
                // 改名失败：退还已扣费用
                plugin.dbManager.bankRepository.updateGuildBalance(guildId, cost)
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
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeRenameSync(guildId, newName))
    }

    /**
     * 分发金库变动通知
     * @param type "deposit" 或 "withdraw"
     */
    internal fun dispatchBankNotification(guildId: Int, playerName: String, type: String, amount: Double) {
        // 本地先处理（单服/跨服都必须），再在 proxy 可用时额外广播给其他服
        val langKey = if (type == "deposit") "bank-deposit-notify" else "bank-withdraw-notify"
        val msg = plugin.langManager.get(langKey, "player" to playerName, "amount" to amount.toString())
        plugin.server.scheduler.runTask(plugin, Runnable {
            sendToLocalGuildMembers(guildId) { p -> p.sendMessage(msg) }
        })
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeBankSync(guildId, playerName, type, amount))
    }

    /**
     * 购买公会 Buff
     */
    fun buyBuff(player: Player, buffKey: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查 (建议仅限会长或副会长，防止普通成员耗尽公会资金)
        // val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)
        // if (role != "OWNER" && role != "ADMIN") {
        //     return callback(OperationResult.NoPermission)
        // }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 2. 获取公会数据 (包含等级和余额)
            val guildData = plugin.dbManager.guildRepository.getGuildData(guildId)
                ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("error-database")))

            val level = guildData.level
            val balance = guildData.balance

            // 3. 等级限制检查
            val allowedBuffs = plugin.levelsConfig.getStringList("levels.$level.use-buff")
            if (!allowedBuffs.contains(buffKey)) {
                val errorMsg = plugin.langManager.get("buff-level-low", "level" to level.toString())
                return@Runnable callback(OperationResult.Error(errorMsg))
            }

            // 4. 读取 Buff 配置与价格
            val path = "buffs.$buffKey"
            if (!plugin.buffsConfig.contains(path)) {
                return@Runnable callback(OperationResult.Error(plugin.langManager.get("buff-not-exist")))
            }

            val price = plugin.buffsConfig.getDouble("$path.price")
            val typeStr = plugin.buffsConfig.getString("$path.type")
            val amplifier = plugin.buffsConfig.getInt("$path.amplifier", 0)
            val buffName = plugin.buffsConfig.getString("$path.name", buffKey) ?: buffKey
            val durationSeconds = plugin.buffsConfig.getInt("$path.time", 90)

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

            // 6. 原子扣费（条件更新，防止并发透支），成功后再分发
            if (plugin.dbManager.bankRepository.withdrawGuildBalanceIfEnough(guildId, price)) {
                plugin.dbManager.bankRepository.logBankTransaction(guildId, player.name, "BUY_BUFF", price)

                // 7. 切回主线程进行分发 (PotionEffect 必须在主线程操作)
                plugin.server.scheduler.runTask(plugin, Runnable {
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
    fun adminRenameGuild(guildId: Int, newName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查名称是否被【其他】公会占用
            val existingId = plugin.dbManager.guildRepository.getGuildIdByName(newName)
            if (existingId != -1 && existingId != guildId) {
                callback(OperationResult.Error(plugin.langManager.get("create-name-exists")))
                return@Runnable
            }

            // 2. 执行改名
            if (plugin.dbManager.guildRepository.renameGuild(guildId, newName)) {
                // 本地先处理，再在 proxy 可用时额外广播（其他服走 AdminRenameSync，本服直接通知）
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val notifyMsg = plugin.langManager.get("admin-rename-notify", "name" to newName)
                    sendToLocalGuildMembers(guildId) { p -> p.sendMessage(notifyMsg) }
                })
                plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeAdminRenameSync(guildId, newName))

                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 管理员强行解散公会
     */
    fun adminDeleteGuild(guildId: Int, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查公会是否存在
            val data = plugin.dbManager.guildRepository.getGuildData(guildId)
            if (data == null) {
                callback(OperationResult.Error(plugin.langManager.get("error-guild-not-found")))
                return@Runnable
            }

            // 先取成员 UUID（删除前），用于跨服精确通知其他服的成员
            val memberUuids = plugin.dbManager.memberRepository.getMemberUUIDs(guildId)

            // 2. 执行数据库删除操作
            // 请确保 dbManager.guildLifecycleRepository.deleteGuild(guildId) 内部会清理成员表和申请表
            if (plugin.dbManager.guildLifecycleRepository.deleteGuild(guildId)) {

                // 3. 本地清理：通知在线成员并移除缓存
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val notifyMsg = plugin.langManager.get("admin-delete-notify")
                    plugin.server.onlinePlayers.forEach { p ->
                        if (memberUuids.contains(p.uniqueId)) {
                            plugin.membershipCache.removeLocal(p.uniqueId)
                            plugin.guildChatPlayers.remove(p.uniqueId)
                            p.sendMessage(notifyMsg)
                        }
                    }
                })

                // 4. 跨服同步清理（携带成员 UUID）
                if (plugin.isProxyEnabled()) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        broadcastGuildDeleted(guildId, memberUuids, "admin-delete-notify")
                    })
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
            val data = plugin.dbManager.guildRepository.getGuildData(guildId)
            if (data == null) {
                callback(OperationResult.Error(plugin.langManager.get("error-guild-not-found", "id" to guildId.toString())), null)
                return@Runnable
            }

            // 2. 获取成员列表
            val uuids = plugin.dbManager.memberRepository.getMemberUUIDs(guildId)
            var online = 0
            val isProxy = plugin.isProxyEnabled()

            // 3. 获取成员名称列表并标记在线状态
            val members = uuids.map { uuid ->
                if (isProxy) {
                    // 代理模式：使用跨服在线玩家缓存判断
                    val playerName = plugin.server.getOfflinePlayer(uuid).name ?: "未知"
                    if (plugin.crossServerOnlinePlayers.containsKey(playerName)) {
                        online++
                        "§a${playerName}" // 在线显示绿色
                    } else {
                        // 即使不在跨服缓存中，也检查本地服务器
                        val p = plugin.server.getPlayer(uuid)
                        if (p != null && p.isOnline) {
                            online++
                            "§a${p.name}"
                        } else {
                            "§7${playerName}" // 离线显示灰色
                        }
                    }
                } else {
                    // 单服模式：只检查本地服务器
                    val p = plugin.server.getPlayer(uuid)
                    if (p != null && p.isOnline) {
                        online++
                        "§a${p.name}" // 在线显示绿色
                    } else {
                        "§7${plugin.server.getOfflinePlayer(uuid).name ?: "未知"}" // 离线显示灰色
                    }
                }
            }

            val info = GuildInfo(data, members, online)
            callback(OperationResult.Success, info)
        })
    }

    /**
     * 管理员强行操作公会金库 (适配增量 SQL 版本)
     */
    fun adminManageBank(guildId: Int, action: String, amount: Double, callback: (OperationResult, Double) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 先获取当前数据（仅用于计算逻辑和日志）
            val guildData = plugin.dbManager.guildRepository.getGuildData(guildId)
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
            if (plugin.dbManager.bankRepository.updateGuildBalance(guildId, delta)) {
                // 记录日志 (记录的是变动金额 amount)
                plugin.dbManager.bankRepository.logBankTransaction(guildId, "ADMIN", action.uppercase(), amount)

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
            val logs = plugin.dbManager.bankRepository.getBankLogs(guildId, page)
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
        // 本地先处理（单服/跨服都必须），再在 proxy 可用时额外广播给其他服
        val durationTicks = seconds * 20
        val effect = PotionEffect(type, durationTicks, amplifier)
        val buffMsg = plugin.langManager.get("buff-received", "player" to buyerName, "buff" to buffName)
        plugin.server.scheduler.runTask(plugin, Runnable {
            sendToLocalGuildMembers(guildId) { p ->
                p.addPotionEffect(effect)
                p.sendMessage(buffMsg)
            }
        })
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeBuffSync(guildId, type.name, seconds, amplifier, buyerName, buffName))
    }

    /**
     * 转让公会所有权 (经过逻辑清理)
     */
    fun adminTransferGuild(guildId: Int, newOwnerName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 获取公会当前数据
            val data = plugin.dbManager.guildRepository.getGuildData(guildId) ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("error-guild-not-found", "id" to guildId.toString())))

            val oldOwnerUuid = UUID.fromString(data.ownerUuid)
            val newOwnerUuid = plugin.dbManager.memberRepository.getPlayerUuidByName(newOwnerName)
                ?: return@Runnable callback(OperationResult.Error(plugin.langManager.get("error-not-in-guild", "player" to newOwnerName)))

            // 1. 执行数据库更新 (事务处理)
            if (plugin.dbManager.guildLifecycleRepository.transferGuildOwnership(guildId, oldOwnerUuid, newOwnerUuid, newOwnerName)) {

                // 2. 刷新本地所有成员缓存
                // 这个方法内部已经包含了遍历在线玩家并纠正 ID 的逻辑，不需要再手动循环 remove
                refreshLocalMembersCache(guildId)

                // 3. 处理跨服同步
                if (plugin.isProxyEnabled()) {
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
                val realId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)
                if (realId == guildId) {
                    plugin.playerGuildCache[player.uniqueId] = guildId
                } else {
                    // 如果发现该玩家其实不属于这个公会了，清理掉旧缓存
                    if (plugin.playerGuildCache[player.uniqueId] == guildId) {
                        clearLocalPlayerGuildState(player.uniqueId)
                    }
                }
            })
        }
    }
    /**
     * 发送强制刷新包
     */
    private fun sendForceRefreshPacket(guildId: Int) {
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeForceRefreshMembers(guildId))
    }
    /**
     * 发送会长转让的跨服封包
     */
    private fun sendTransferSyncPacket(guildId: Int, newOwnerUuid: UUID, newOwnerName: String) {
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeGuildTransfer(guildId, newOwnerUuid, newOwnerName))
    }

    /**
     * 管理员强制踢人
     */
    fun adminKickMember(guildId: Int, targetName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val lang = plugin.langManager
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val targetUuid = plugin.dbManager.memberRepository.getPlayerUuidByName(targetName)
                ?: return@Runnable callback(OperationResult.Error(lang.get("error-not-has-player", "player" to targetName)))

            // 1. 检查是否在指定的公会中
            val currentGuildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(targetUuid)
            if (currentGuildId != guildId) {
                return@Runnable callback(OperationResult.Error(lang.get("error-not-in-guild", "player" to targetName)))
            }

            // 2. 安全检查：禁止踢出会长
            val data = plugin.dbManager.guildRepository.getGuildData(guildId)
            if (data?.ownerUuid == targetUuid.toString()) {
                return@Runnable callback(OperationResult.Error(lang.get("error-player-is-master")))
            }

            // 3. 调用你现有的 removeMember(Int, UUID)
            if (plugin.dbManager.memberRepository.removeMember(guildId, targetUuid)) {
                // 更新公会成员名称缓存
                updateGuildMemberNamesCache(guildId)
                clearLocalPlayerGuildState(targetUuid)
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error(lang.get("error-database")))
            }
        })
    }

    /**
     * 管理员强制加人
     */
    fun adminJoinMember(guildId: Int, targetPlayer: Player, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val lang = plugin.langManager
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查玩家是否已有公会
            if (plugin.dbManager.memberRepository.getGuildIdByPlayer(targetPlayer.uniqueId) != null) {
                return@Runnable callback(OperationResult.Error(lang.get("invite-already-member")))
            }

            // 2. 调用现有的 addMember(Int, UUID, String, String)
            if (plugin.dbManager.memberRepository.addMember(guildId, targetPlayer.uniqueId, targetPlayer.name, "MEMBER")) {
                // 更新公会成员名称缓存
                updateGuildMemberNamesCache(guildId)
                plugin.playerGuildCache[targetPlayer.uniqueId] = guildId // 同步缓存
                plugin.taskManager.activatePlayerTasks(targetPlayer.uniqueId, guildId)
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
        plugin.vaultService.openVault(player, vaultIndex)
    }
    /**
     * 尝试锁定仓库
     * @return 如果锁定成功返回 true，如果已被占用返回 false
     */
    fun tryLockVault(guildId: Int, vaultIndex: Int, player: Player, isFromNetwork: Boolean = false): Boolean {
        return plugin.vaultService.tryLockVault(guildId, vaultIndex, player, isFromNetwork)
    }

    /**
     * 玩家退出时，清理其持有的所有锁并同步到跨服
     */
    fun clearAllLocksByPlayer(playerUuid: UUID) {
        plugin.vaultService.clearAllLocksByPlayer(playerUuid)
    }

    /**
     * 强制重置所有锁
     */
    fun forceResetAllLocks() {
        plugin.vaultService.forceResetAllLocks()
    }

    /**
     * 释放仓库锁
     */
    fun releaseVaultLock(guildId: Int, vaultIndex: Int, isFromNetwork: Boolean = false, occupant: UUID? = null) {
        plugin.vaultService.releaseVaultLock(guildId, vaultIndex, isFromNetwork, occupant)
    }
    /**
     * 同步远程仓库锁
     */
    fun syncRemoteLock(guildId: Int, index: Int, uuid: UUID) {
        plugin.vaultService.syncRemoteLock(guildId, index, uuid)
    }
    /**
     * 管理员打开仓库
     */
    fun adminOpenVault(admin: Player, guildId: Int, vaultIndex: Int) {
        plugin.vaultService.adminOpenVault(admin, guildId, vaultIndex)
    }
    /**
     * 开启续租任务
     * @return 返回 BukkitTask 对象以便后续手动取消
     */
    fun startVaultLeaseTask(player: Player, guildId: Int, index: Int): BukkitTask {
        return plugin.vaultService.startVaultLeaseTask(player, guildId, index)
    }

    /**
     * 设置公会图标 (消耗公会资金版)
     */
    fun setGuildIcon(player: Player, materialName: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查 (会长或副会长)
        val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)
        if (role != "OWNER" && role != "ADMIN") {
            return callback(OperationResult.NoPermission)
        }

        // 2. 获取手持物品的 item_model 和 custom_data
        val item = player.inventory.itemInMainHand
        val itemModel = getItemModel(item)
        val customData = getCustomModelData(item) // 如果没有设置 custom_data，返回 null

        // 3. 异步处理
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 4. 获取费用并检查公会银行
            val cost = plugin.config.getDouble("balance.seticon", 1000.0)
            val guild = plugin.dbManager.guildRepository.getGuildById(guildId)

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

            // 5. 先原子扣费（条件更新，防止并发透支），成功后再写图标
            if (!plugin.dbManager.bankRepository.withdrawGuildBalanceIfEnough(guildId, cost)) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-balance-not-enough", "cost" to cost.toString(), "balance" to (guild?.balance ?: 0.0).toString())))
                })
                return@Runnable
            }

            if (plugin.dbManager.guildRepository.updateGuildIcon(guildId, materialName, itemModel, customData)) {
                plugin.dbManager.bankRepository.logBankTransaction(guildId, player.name, "SET_ICON", cost)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Success)
                })
            } else {
                // 写图标失败：退还已扣费用
                plugin.dbManager.bankRepository.updateGuildBalance(guildId, cost)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-database")))
                })
            }
        })
    }

    /**
     * 获取物品的 ItemModel（仅 1.21.4+）
     */
    private fun getItemModel(item: ItemStack): String? {
        try {
            val meta = item.itemMeta ?: return null
            val itemMetaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta")

            // 尝试获取 ItemModel
            val getItemModelMethod = itemMetaClass.getMethod("getItemModel")
            val model = getItemModelMethod.invoke(meta) ?: return null

            // NamespacedKey.toString() 返回 "namespace:key" 格式
            return model.toString()
        } catch (e: ClassNotFoundException) {
            // 旧版本不支持 ItemModel API
            return null
        } catch (e: NoSuchMethodException) {
            // 旧版本不支持 ItemModel API
            return null
        } catch (e: Exception) {
            // 其他错误
            plugin.logger.warning("Error getting ItemModel: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * 获取物品的 CustomModelData
     * @return 如果物品没有设置 custom_model_data，返回 null
     */
    private fun getCustomModelData(item: ItemStack): Int? {
        val meta = item.itemMeta ?: return null
        // customModelData 为 0 时表示没有设置
        return if (meta.hasCustomModelData()) {
            meta.customModelData
        } else {
            null
        }
    }

    /**
     * 修改公会公告 (消耗公会资金版)
     */
    fun setGuildMotd(player: Player, content: String, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查 (会长或副会长)
        val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)
        if (role != "OWNER" && role != "ADMIN") {
            return callback(OperationResult.NoPermission)
        }

        // 2. 异步处理
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 3. 获取费用并检查公会银行
            val cost = plugin.config.getDouble("balance.setmotd", 100.0)
            val guild = plugin.dbManager.guildRepository.getGuildById(guildId)

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

            // 4. 先原子扣费（条件更新，防止并发透支），成功后再写公告
            if (!plugin.dbManager.bankRepository.withdrawGuildBalanceIfEnough(guildId, cost)) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-balance-not-enough", "cost" to cost.toString(), "balance" to (guild?.balance ?: 0.0).toString())))
                })
                return@Runnable
            }

            if (plugin.dbManager.guildRepository.updateGuildAnnouncement(guildId, content)) {
                plugin.dbManager.bankRepository.logBankTransaction(guildId, player.name, "SET_MOTD", cost)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Success)
                })
            } else {
                // 写公告失败：退还已扣费用
                plugin.dbManager.bankRepository.updateGuildBalance(guildId, cost)
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
            if (!plugin.dbManager.memberRepository.isStaff(player.uniqueId, guildId)) return@Runnable

            // 2. 获取该公会的申请列表
            val requests = plugin.dbManager.requestRepository.getRequests(guildId)
            if (requests.isEmpty()) return@Runnable

            val num = requests.size

            // 3. 回到主线程发送消息
            plugin.server.scheduler.runTask(plugin, Runnable {
                val lang = plugin.langManager
                val msg = MessageUtil.createText(lang.get("notify-pending-requests", "num" to num.toString()))

                val viewBtn = MessageUtil.createClickableText(
                    text = lang.get("notify-view-btn"),
                    hoverText = lang.get("notify-view-btn-hover"),
                    command = "/kg requests"
                )

                msg.addExtra(viewBtn)
                player.spigot().sendMessage(msg)
            })
        })
    }

    /**
     * 升级公会
     * @param player 玩家对象
     * @param callback 回调函数，用于处理结果
     */
    fun upgradeGuild(player: Player, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val lang = plugin.langManager
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.Error(lang.get("error-not-has-guild")))

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 权限检查
            if (!plugin.dbManager.memberRepository.isStaff(player.uniqueId, guildId)) {
                return@Runnable syncCallback(callback, OperationResult.Error(lang.get("error-no-permission")))
            }

            val guildData = plugin.dbManager.guildRepository.getGuildById(guildId) ?: return@Runnable
            val nextLevel = guildData.level + 1

            // 2. 检查配置中是否存在下一级
            val levelSection =
                plugin.levelsConfig.getConfigurationSection("levels.$nextLevel") ?: return@Runnable syncCallback(
                    callback,
                    OperationResult.Error(lang.get("menu-upgrade-max-level"))
                )

            // 3. 检查经验值
            val needExp = levelSection.getInt("need-exp")
            if (guildData.exp < needExp) {
                return@Runnable syncCallback(callback, OperationResult.Error(lang.get("menu-upgrade-not-enough-exp", "need" to needExp.toString(), "exp" to guildData.exp.toString())))
            }

            // 4. 获取新等级的属性
            val maxMembers = levelSection.getInt("max-members")

            // 5. 执行更新
            if (plugin.dbManager.guildRepository.updateGuildLevel(guildId, nextLevel, maxMembers)) {
                plugin.runMain {
                    callback(OperationResult.Success)
                    // 全服广播或公会消息
                    plugin.server.broadcastMessage(lang.get("menu-upgrade-level-up-broadcast", "name" to guildData.name, "level" to nextLevel.toString())) //"§8[§b公会§8] §f${guildData.name} §7成功升级到了等级 §f$nextLevel§7！"
                }
            } else {
                syncCallback(callback, OperationResult.Error(plugin.langManager.get("error-database")))
            }
        })
    }

    /**
     * 修改公会经验值
     * @param sender 命令发送者
     * @param guildId 公会 ID
     * @param type 类型（add/remove/set）
     * @param amount 数量
     * @param silent 是否静默成功输出，错误仍会提示
     */
    fun adminModifyExp(sender: CommandSender, guildId: Int, type: String, amount: Int, silent: Boolean = false) {
        val lang = plugin.langManager
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guild = plugin.dbManager.guildRepository.getGuildById(guildId)
            if (guild == null) {
                sender.sendMessage(lang.get("error-guild-not-found", "id" to guildId.toString()))
                return@Runnable
            }

            val success = when (type.lowercase()) {
                "add" -> plugin.dbManager.guildRepository.updateGuildExp(guildId, amount, false)
                "remove" -> plugin.dbManager.guildRepository.updateGuildExp(guildId, -amount, false)
                "set" -> plugin.dbManager.guildRepository.updateGuildExp(guildId, amount, true)
                else -> false
            }

            plugin.server.scheduler.runTask(plugin, Runnable {
                if (success) {
                    if (silent) return@Runnable

                    val newExp = if (type == "set") amount else (guild.exp + if (type == "add") amount else -amount)
                    sender.sendMessage(lang.get("admin-success-modify-exp", "name" to guild.name,"exp" to newExp.toString()))
                } else {
                    sender.sendMessage(lang.get("error-database"))
                }
            })
        })
    }
    /**
     * 发起公会战申请 (消耗公会资金)
     */
    fun startPvPChallenge(player: Player, targetGuild: GuildData, rawCallback: (OperationResult) -> Unit) {
        // 统一：回调回到主线程执行（Bukkit API 线程安全）
        val callback: (OperationResult) -> Unit = { result -> plugin.runMain { rawCallback(result) } }

        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return callback(OperationResult.NotInGuild)

        // 1. 权限检查
        val role = plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)
        if (role != "OWNER" && role != "ADMIN") {
            return callback(OperationResult.NoPermission)
        }

        // 2. 异步处理
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val cost = plugin.config.getDouble("balance.pvp", 300.0)
            val guild = plugin.dbManager.guildRepository.getGuildById(guildId)

            // 3. 余额检查
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

            // 4. 原子扣费（条件更新，防止并发透支）
            if (plugin.dbManager.bankRepository.withdrawGuildBalanceIfEnough(guildId, cost)) {
                // 记录银行日志
                plugin.dbManager.bankRepository.logBankTransaction(guildId, player.name, "PVP_CHALLENGE", cost)

                // 回到主线程执行 PvP 逻辑 (PvPManager 通常涉及在线玩家操作)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.pvpManager.sendChallenge(guildId, targetGuild.id)
                    callback(OperationResult.Success)
                })
            } else {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    callback(OperationResult.Error(plugin.langManager.get("error-database")))
                })
            }
        })
    }

    // 辅助方法：确保回调在主线程执行
    private fun syncCallback(callback: (OperationResult) -> Unit, result: OperationResult) {
        plugin.server.scheduler.runTask(plugin, Runnable { callback(result) })
    }

    private fun clearLocalPlayerGuildState(playerUuid: UUID) {
        plugin.playerGuildCache.remove(playerUuid)
        plugin.guildChatPlayers.remove(playerUuid)
    }

    private fun clearLocalGuildState(guildId: Int) {
        // 先按在线玩家判断身份并清理聊天模式（此时缓存尚未失效）
        plugin.server.onlinePlayers.forEach { p ->
            if (plugin.membershipCache.getGuildId(p) == guildId) {
                plugin.membershipCache.removeLocal(p.uniqueId)
                plugin.guildChatPlayers.remove(p.uniqueId)
            }
        }
        // 再兜底清除本地缓存中所有该公会条目（含离线玩家残留）
        plugin.membershipCache.invalidateGuildLocal(guildId)
    }
}

