package org.katacr.kaguilds

import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import org.katacr.kaguilds.util.MessageUtil
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.UUID
import kotlin.collections.forEach

class PluginMessageListener(private val plugin: KaGuilds) : PluginMessageListener {
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != org.katacr.kaguilds.util.NetworkCodec.CHANNEL) return
        // Bukkit 插件消息回调在 Netty 线程触发；所有分支会操作在线玩家/BossBar/查库，
        // 统一切回主线程处理，避免异步线程调用 Bukkit API。
        val data = message.copyOf()
        if (plugin.server.isPrimaryThread) {
            handle(data)
        } else {
            plugin.runMain { handle(data) }
        }
    }

    private fun handle(message: ByteArray) {
        val `in` = DataInputStream(ByteArrayInputStream(message))
        val subChannel = try {
            `in`.readUTF()
        } catch (e: Exception) {
            return
        }
        try {
            dispatch(subChannel, `in`)
        } catch (e: Exception) {
            // 畸形/字段错位的包不应逃逸到网络线程
            plugin.logger.warning("处理跨服包 $subChannel 失败: ${e.message}")
        }
    }

    private fun dispatch(subChannel: String, `in`: DataInputStream) {
        when (subChannel) {
            /*
             * 处理在线玩家完整列表（来自Velocity端）
             */
            "OnlinePlayersList" -> {
                val playerCount = `in`.readInt()

                // 收到来自代理的在线列表 → 标记代理通讯正常（用于 proxy 运行时探测）
                plugin.proxyCapability.markProxyReachable()

                // 清空旧列表
                plugin.crossServerOnlinePlayers.clear()
                
                // 读取新列表
                repeat(playerCount) {
                    val playerName = `in`.readUTF()
                    val serverId = `in`.readUTF()
                    plugin.crossServerOnlinePlayers[playerName] = serverId
                }
            }

            /*
             * 处理代理下发的本服真实服务器标识
             * 覆盖本地配置 server-id，避免多服误用默认标识
             */
            "ServerIdentify" -> {
                val realServerId = `in`.readUTF()
                if (realServerId.isNotBlank()) {
                    plugin.effectiveServerId = realServerId
                    plugin.proxyCapability.markProxyReachable()
                    plugin.logger.info("[KaGuilds] 收到 KaProxy 下发的本服标识: $realServerId")
                }
            }

            /*
             * 处理跨服聊天
             */
            "Chat" -> {
                val targetGuildId = `in`.readInt()
                val senderName = `in`.readUTF()
                val msgContent = `in`.readUTF()
                val roleDisplayName = `in`.readUTF()
                val coloredMessage = `in`.readUTF()

                // 直接发送已解析和着色的消息
                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                    val cachedId = plugin.membershipCache.getGuildId(onlinePlayer)
                    if (cachedId == targetGuildId) {
                        onlinePlayer.sendMessage(coloredMessage)
                    }
                }
            }
            /*
             * 处理跨服全局任务完成通知
             */
            "TaskCompleted" -> {
                val targetGuildId = `in`.readInt()
                val taskName = `in`.readUTF()

                val msg = plugin.langManager.get("task-global-completed", "name" to taskName)

                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                    val cachedId = plugin.membershipCache.getGuildId(onlinePlayer.uniqueId)
                    if (cachedId != null && cachedId == targetGuildId) {
                        onlinePlayer.spigot().sendMessage(MessageUtil.createText(msg))
                        onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    }
                }
            }
            /*
             * 处理跨服全局任务 BossBar 进度更新
             */
            "GlobalTaskProgress" -> {
                val targetGuildId = `in`.readInt()
                val taskKey = `in`.readUTF()
                val taskName = `in`.readUTF()
                val progress = `in`.readInt()
                val target = `in`.readInt()

                // 调用 TaskManager 的方法来更新跨服 BossBar
                plugin.taskManager.updateCrossServerGlobalBossBar(targetGuildId, taskKey, taskName, progress, target)
            }
            /*
             * 处理跨服全局任务完成通知
             */
            "GlobalTaskCompleted" -> {
                val targetGuildId = `in`.readInt()
                val taskKey = `in`.readUTF()
                val taskName = `in`.readUTF()

                // 只调用 TaskManager 的方法来移除跨服 BossBar
                // 不发送完成通知，因为 TaskCompleted 消息已经发送过了
                plugin.taskManager.removeCrossServerGlobalBossBar(targetGuildId, taskKey)

                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                    val cachedId = plugin.membershipCache.getGuildId(onlinePlayer.uniqueId)
                    if (cachedId != null && cachedId == targetGuildId) {
                        onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    }
                }
            }
            /*
             * 处理跨服公会缓存
             */
            "SyncCache" -> {
                try {
                    val uuidStr = `in`.readUTF()
                    val gId = `in`.readInt()
                    val targetUuid = UUID.fromString(uuidStr)
                    plugin.membershipCache.removeLocal(targetUuid)
                    if (gId == -1) {
                        plugin.guildChatPlayers.remove(targetUuid)
                    }
                    if (gId != -1) {
                        plugin.membershipCache.updateLocal(targetUuid, gId)
                        plugin.taskManager.activatePlayerTasks(targetUuid, gId)
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("同步缓存数据解析失败: ${e.message}")
                }
            }
            /*
             * 处理跨服邀请
             */
            "CrossInvite" -> {
                // 身份用 UUID（名称仅显示）
                val targetUuid = try {
                    UUID.fromString(`in`.readUTF())
                } catch (e: Exception) {
                    return
                }
                val guildId = `in`.readInt()
                val guildName = `in`.readUTF()
                val senderName = `in`.readUTF()

                // 寻找目标玩家是否在本服（按 UUID）
                val targetPlayer = plugin.server.onlinePlayers.find { it.uniqueId == targetUuid }

                if (targetPlayer != null) {
                    // 异步检查目标玩家是否有公会（防止他在别的服刚加了公会）
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        if (plugin.dbManager.memberRepository.getGuildIdByPlayer(targetPlayer.uniqueId) == null) {

                            // 1. 存入本服的邀请缓存
                            plugin.inviteCache[targetPlayer.uniqueId] = guildId

                            // 2. 发送提示
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                val lang = plugin.langManager
                                val msg = MessageUtil.createText(lang.get("invite-received-target",
                                    "player" to senderName, "guild" to guildName))

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
                                targetPlayer.playSound(targetPlayer.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f)
                            })

                            // 3. 60秒后自动过期
                            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                                plugin.inviteCache.remove(targetPlayer.uniqueId)
                            }, 1200L)
                        }
                    })
                }
            }
            /*
             * 通知玩家有新的申请
             */
            "NotifyRequest" -> {
                val targetId = `in`.readInt()
                `in`.readUTF()  // guildName（显示用，本分支不展示）
                val appName = `in`.readUTF()

                val lang = plugin.langManager

                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                    // 先查缓存确认公会
                    if (plugin.membershipCache.getGuildId(onlinePlayer.uniqueId) == targetId) {
                        // 再异步查权限发送
                        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                            val role = plugin.dbManager.memberRepository.getPlayerRole(onlinePlayer.uniqueId)
                            if (role == "OWNER" || role == "ADMIN") {
                                plugin.server.scheduler.runTask(plugin, Runnable {
                                    val msg = MessageUtil.createText(lang.get("notify-new-request", "player" to appName))
                                    val viewBtn = MessageUtil.createClickableText(
                                        text = lang.get("notify-view-btn"),
                                        hoverText = lang.get("notify-view-btn-hover"),
                                        command = "/kg requests"
                                    )
                                    msg.addExtra(viewBtn)
                                    onlinePlayer.spigot().sendMessage(msg)
                                    onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                                })
                            }
                        })
                    }
                }
            }

            /*
             * 处理跨服公会成员变动
             */
            "MemberJoin" -> {
                val gId = `in`.readInt()
                // 身份用 UUID；名称仅显示
                val newUuid = try { UUID.fromString(`in`.readUTF()) } catch (e: Exception) { null }
                val pName = `in`.readUTF()

                val msg = plugin.langManager.get("member-join-notify", "player" to pName)
                val welcomeMsg = plugin.langManager.get("request-accepted-notice")

                plugin.server.onlinePlayers.forEach { p ->
                    // 逻辑 A: 发送给公会里的老成员 (通过缓存判断)
                    if (plugin.membershipCache.getGuildId(p.uniqueId) == gId) {
                        p.sendMessage(msg)
                    }

                    // 逻辑 B: 专门给新人发私信（按 UUID，不依赖缓存）
                    else if (newUuid != null && p.uniqueId == newUuid) {
                        p.sendMessage(welcomeMsg)
                        p.playSound(p.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    }
                }
            }

            /*
             * 处理跨服公会成员变动
             */
            "MemberLeave" -> {
                val gId = `in`.readInt()
                `in`.readUTF() // playerUuid（本分支仅需公会ID做通知）
                val pName = `in`.readUTF()
                val msg = plugin.langManager.get("member-leave-notify", "player" to pName)

                plugin.server.onlinePlayers.forEach { p ->
                    if (plugin.membershipCache.getGuildId(p.uniqueId) == gId) {
                        p.sendMessage(msg)
                    }
                }
            }

            /*
             * 处理跨服公会成员变动
             */
            "MemberKick" -> {
                val gId = `in`.readInt()
                // 身份用 UUID；名称仅显示
                val targetUuid = try { UUID.fromString(`in`.readUTF()) } catch (e: Exception) { null }
                val tName = `in`.readUTF()

                val kickMsg = plugin.langManager.get("member-kick-notify", "player" to tName)
                val targetPrivateMsg = plugin.langManager.get("kick-notice-to-target")

                plugin.server.onlinePlayers.forEach { p ->
                    // 通知该公会的在线成员
                    if (plugin.membershipCache.getGuildId(p.uniqueId) == gId) {
                        p.sendMessage(kickMsg)
                    }
                    // 寻找被踢的目标玩家并私信（按 UUID 精确匹配）
                    if (targetUuid != null && p.uniqueId == targetUuid) {
                        p.sendMessage(targetPrivateMsg)
                        // 踢出时清理该服可能存在的内存缓存(双重保险)
                        plugin.membershipCache.removeLocal(p.uniqueId)
                        plugin.guildChatPlayers.remove(p.uniqueId)
                    }
                }
            }

            /*
             * 处理跨服名称变动通知
             */
            "RenameSync" -> {
                // 按照发送顺序读取：1. Int (ID)  2. UTF (Name)
                val guildId = `in`.readInt()
                val newGuildName = `in`.readUTF() // 这里定义了局部变量

                // 逻辑处理：例如更新该服务器的缓存或广播通知
                plugin.server.onlinePlayers.forEach { p ->
                    if (plugin.membershipCache.getGuildId(p.uniqueId) == guildId) {
                        p.sendMessage(plugin.langManager.get("rename-success-notify", "name" to newGuildName))
                    }
                }
            }

            /*
             * 处理跨服银行变动通知
             */
            "BankSync" -> {
                val gId = `in`.readInt()
                val pName = `in`.readUTF()
                val type = `in`.readUTF()
                val amount = `in`.readDouble()

                val langKey = if (type == "deposit") "bank-deposit-notify" else "bank-withdraw-notify"
                val msg = plugin.langManager.get(langKey, "player" to pName, "amount" to amount.toString())

                plugin.server.onlinePlayers.forEach { p ->
                    if (plugin.membershipCache.getGuildId(p.uniqueId) == gId) {
                        p.sendMessage(msg)
                    }
                }
            }

            /*
             * 处理跨服buff
             */
            "BuffSync" -> {
                val gId = `in`.readInt()
                val typeName = `in`.readUTF()
                val seconds = `in`.readInt()
                val amplifier = `in`.readInt()
                val buyerName = `in`.readUTF()
                val buffName = `in`.readUTF()

                val type = org.bukkit.potion.PotionEffectType.getByName(typeName) ?: return
                val durationTicks = seconds * 20
                val effect = org.bukkit.potion.PotionEffect(type, durationTicks, amplifier)

                val msg = plugin.langManager.get("buff-received", "player" to buyerName, "buff" to buffName)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.server.onlinePlayers.forEach { p ->
                        if (plugin.membershipCache.getGuildId(p.uniqueId) == gId) {
                            p.addPotionEffect(effect)
                            p.sendMessage(msg)
                        }
                    }
                })
            }

            /*
             * 处理跨服公会删除
             */
            "DeleteSync" -> {
                val gId = `in`.readInt()
                // 新格式：notifyKey + 成员 UUID 列表（按 UUID 精确通知，不依赖可能过期的缓存）；
                // 兼容旧格式：读取 notifyKey 失败时回退默认文案并按缓存判断。
                val notifyKey = try {
                    `in`.readUTF()
                } catch (e: Exception) {
                    "admin-delete-notify"
                }
                val memberUuids = try {
                    val count = `in`.readInt()
                    (0 until count).mapNotNull {
                        try {
                            UUID.fromString(`in`.readUTF())
                        } catch (e: Exception) {
                            null
                        }
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                val notifyMsg = plugin.langManager.get(notifyKey)
                plugin.server.onlinePlayers.forEach { p ->
                    val affected = if (memberUuids.isNotEmpty()) {
                        memberUuids.contains(p.uniqueId)
                    } else {
                        plugin.membershipCache.getGuildId(p) == gId
                    }
                    if (affected) {
                        plugin.membershipCache.removeLocal(p.uniqueId)
                        plugin.guildChatPlayers.remove(p.uniqueId)
                        p.sendMessage(notifyMsg)
                    }
                }
            }

            /*
             * 处理跨服公会改名
             */
            "AdminRenameSync" -> {
                val gId = `in`.readInt()
                val newName = `in`.readUTF()

                val notifyMsg = plugin.langManager.get("admin-rename-notify", "name" to newName)

                // 遍历本服务器玩家，如果是该公会的，则发送通知
                plugin.server.onlinePlayers.forEach { p ->
                    if (plugin.membershipCache.getGuildId(p.uniqueId) == gId) {
                        p.sendMessage(notifyMsg)
                    }
                }
            }

            /*
             * 处理跨服公会库存锁定
             */
            "VaultSync" -> {
                val action = `in`.readUTF() // "Lock" 或 "Unlock"
                val gId = `in`.readInt()
                val vIndex = `in`.readInt()
                val uIdString = `in`.readUTF()
                val uId = UUID.fromString(uIdString)

                when (action) {
                    "Lock" -> plugin.guildService.syncRemoteLock(gId, vIndex, uId)
                    "Unlock" -> plugin.guildService.releaseVaultLock(gId, vIndex, true, uId)
                    "ForceUnlockAll" -> plugin.guildService.vaultLocks.clear() // 强制全清
                }
            }

            /*
             * 处理跨服公会会长变更
             */
            "GuildTransfer" -> {
                val gId = `in`.readInt()
                val newOwnerUuidString = `in`.readUTF()
                val newOwnerName = `in`.readUTF()
                val newOwnerUuid = UUID.fromString(newOwnerUuidString)

                plugin.server.onlinePlayers.forEach { p ->
                    // 关键：直接根据当前内存里的缓存判断
                    val cachedId = plugin.membershipCache.getGuildId(p.uniqueId)

                    if (cachedId == gId) {
                        // 1. 发送通知
                        if (p.uniqueId == newOwnerUuid) {
                            p.sendMessage("§a[公会] 你已被任命为新会长！")
                        } else {
                            p.sendMessage("§e[公会] 公会会长已变更为 $newOwnerName。")
                        }

                        // 2. 彻底移除旧缓存，强制下次任何操作都重新读库
                        plugin.membershipCache.removeLocal(p.uniqueId)

                        // 3. 异步预加载新数据 (可选，但建议延迟 1 秒，等待数据库写入同步)
                        plugin.server.scheduler.runTaskLaterAsynchronously(plugin, Runnable {
                            val newId = plugin.dbManager.memberRepository.getGuildIdByPlayer(p.uniqueId)
                            if (newId != null) {
                                plugin.membershipCache.updateLocal(p.uniqueId, newId)
                            }
                        }, 20L) // 延迟 1 秒（20 ticks）
                    }
                }
            }

            "ForceRefreshMembers" -> {
                val targetGuildId = `in`.readInt()

                // 强制本服所有在线玩家，只要属于该公会，就立即重新读库
                plugin.server.onlinePlayers.forEach { p ->
                    // 我们不判断 cachedId == targetGuildId，因为缓存可能是错的
                    // 直接异步读库校验
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        val realGuildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(p.uniqueId)
                        if (realGuildId == targetGuildId) {
                            // 修正并激活缓存
                            plugin.membershipCache.updateLocal(p.uniqueId, targetGuildId)
                            // 此时缓存已回正，Chat 功能立即恢复正常
                        }
                    })
                }
            }

            /*
             * 处理跨服公会战通知
             */
            "PvPBattleStart" -> {
                val targetGuildId = `in`.readInt()
                val serverId = `in`.readUTF()
                val ownGuildName = `in`.readUTF()
                val enemyGuildName = `in`.readUTF()

                // 获取当前服务器的 ID
                val currentServerId = plugin.effectiveServerId

                // 如果是发起服务器本身,则不处理此消息
                if (serverId == currentServerId) {
                    return
                }

                val lang = plugin.langManager

                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                    // 通知属于该公会的玩家
                    if (plugin.membershipCache.getGuildId(onlinePlayer.uniqueId) == targetGuildId) {
                        val msg = MessageUtil.createText(
                            lang.get("arena-pvp-cross-server-notify",
                                "server" to serverId,
                                "own" to ownGuildName,
                                "enemy" to enemyGuildName)
                        )
                        onlinePlayer.spigot().sendMessage(msg)
                        onlinePlayer.playSound(onlinePlayer.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                    }
                }
            }
        }
    }
}

