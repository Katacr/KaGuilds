package org.katacr.kaguilds

import com.google.common.io.ByteStreams
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import kotlin.collections.forEach

class PluginMessageListener(private val plugin: KaGuilds) : PluginMessageListener {
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {

        if (channel != "kaguilds:chat") return

        val `in` = ByteStreams.newDataInput(message)
        val subChannel = `in`.readUTF()
        when (subChannel) {
            /*
             * 处理跨服聊天
             */
            "Chat" -> {
                val targetGuildId = `in`.readInt()
                val senderName = `in`.readUTF()
                val msgContent = `in`.readUTF()

                val formattedMsg = plugin.langManager.get("chat-format",
                    "player" to senderName, "message" to msgContent)

                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                    // 这里的逻辑：如果玩家本地缓存匹配，则发送
                    var cachedId = plugin.playerGuildCache[onlinePlayer.uniqueId]

                    if (cachedId != null && cachedId == targetGuildId) {
                        onlinePlayer.sendMessage(formattedMsg)
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
                    plugin.playerGuildCache.remove(targetUuid)
                    if (gId != -1) {
                        plugin.playerGuildCache[targetUuid] = gId
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("同步缓存数据解析失败: ${e.message}")
                }
            }
            /*
             * 处理跨服公会清除
             */
            "CLEAR_GUILD" -> {
                val targetId = `in`.readInt()
                // 遍历本服缓存，清除所有属于该公会的玩家记录
                val iterator = plugin.playerGuildCache.entries.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().value == targetId) {
                        iterator.remove()
                    }
                }
            }
            /*
             * 处理跨服公会通知
             */
            "NotifyStaff" -> {
                val targetGuildId = `in`.readInt()
                val applicantName = `in`.readUTF()

                val notifyMsg = plugin.langManager.get("request-notify", "player" to applicantName)

                // 找到本服所有属于该公会且具有管理权限的玩家
                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                    val cachedGuildId = plugin.playerGuildCache[onlinePlayer.uniqueId]
                    if (cachedGuildId == targetGuildId) {
                        // 异步检查权限，或者如果你把权限也缓存了会更快
                        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                            if (plugin.dbManager.isStaff(onlinePlayer.uniqueId, targetGuildId)) {
                                onlinePlayer.sendMessage(notifyMsg)
                            }
                        })
                    }
                }
            }
            /*
             * 处理跨服邀请
             */
            "CrossInvite" -> {
                val targetName = `in`.readUTF()
                val guildId = `in`.readInt()
                val guildName = `in`.readUTF()
                val senderName = `in`.readUTF()

                // 寻找目标玩家是否在本服
                val targetPlayer = plugin.server.onlinePlayers.find { it.name.equals(targetName, true) }

                if (targetPlayer != null) {
                    // 异步检查目标玩家是否有公会（防止他在别的服刚加了公会）
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        if (plugin.dbManager.getGuildIdByPlayer(targetPlayer.uniqueId) == null) {

                            // 1. 存入本服的邀请缓存
                            plugin.inviteCache[targetPlayer.uniqueId] = guildId

                            // 2. 发送提示
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                val msg = plugin.langManager.get("invite-received-target",
                                    "player" to senderName, "guild" to guildName)
                                targetPlayer.sendMessage(msg)
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
                val gName = `in`.readUTF()
                val appName = `in`.readUTF()

                val msg = plugin.langManager.get("notify-new-request", "player" to appName, "guild" to gName)

                plugin.server.onlinePlayers.forEach { onlinePlayer ->
                    // 先查缓存确认公会
                    if (plugin.playerGuildCache[onlinePlayer.uniqueId] == targetId) {
                        // 再异步查权限发送
                        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                            val role = plugin.dbManager.getPlayerRole(onlinePlayer.uniqueId)
                            if (role == "OWNER" || role == "ADMIN") {
                                plugin.server.scheduler.runTask(plugin, Runnable {
                                    onlinePlayer.sendMessage(msg)
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
                val pName = `in`.readUTF()

                val msg = plugin.langManager.get("member-join-notify", "player" to pName)
                val welcomeMsg = plugin.langManager.get("request-accepted-notice")

                plugin.server.onlinePlayers.forEach { p ->
                    // 逻辑 A: 发送给公会里的老成员 (通过缓存判断)
                    if (plugin.playerGuildCache[p.uniqueId] == gId) {
                        // 注意：如果新人已经在该服更新了缓存，他也会收到这条“热烈欢迎 XXX”
                        p.sendMessage(msg)
                    }

                    // 逻辑 B: 专门给新人发私信 (通过名字判断，不依赖缓存)
                    // 这样即使缓存同步慢了几毫秒，他也能第一时间看到
                    else if (p.name.equals(pName, ignoreCase = true)) {
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
                val pName = `in`.readUTF()
                val msg = plugin.langManager.get("member-leave-notify", "player" to pName)

                plugin.server.onlinePlayers.forEach { p ->
                    if (plugin.playerGuildCache[p.uniqueId] == gId) {
                        p.sendMessage(msg)
                    }
                }
            }

            /*
             * 处理跨服公会成员变动
             */
            "MemberKick" -> {
                val gId = `in`.readInt()
                val tName = `in`.readUTF()

                val kickMsg = plugin.langManager.get("member-kick-notify", "player" to tName)
                val targetPrivateMsg = plugin.langManager.get("kick-notice-to-target")

                plugin.server.onlinePlayers.forEach { p ->
                    // 通知该公会的在线成员
                    if (plugin.playerGuildCache[p.uniqueId] == gId) {
                        p.sendMessage(kickMsg)
                    }
                    // 寻找被踢的目标玩家并私信
                    if (p.name.equals(tName, ignoreCase = true)) {
                        p.sendMessage(targetPrivateMsg)
                        // 踢出时清理该服可能存在的内存缓存(双重保险)
                        plugin.playerGuildCache.remove(p.uniqueId)
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
                    if (plugin.playerGuildCache[p.uniqueId] == guildId) {
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
                    if (plugin.playerGuildCache[p.uniqueId] == gId) {
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
                        if (plugin.playerGuildCache[p.uniqueId] == gId) {
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
                // 清理本子服所有该公会成员的缓存
                val iterator = plugin.playerGuildCache.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.value == gId) {
                        iterator.remove()
                        // 如果该玩家在线，顺便通知一下
                        plugin.server.getPlayer(entry.key)?.sendMessage(
                            plugin.langManager.get("admin-delete-notify")
                        )
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
                    if (plugin.playerGuildCache[p.uniqueId] == gId) {
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
                    "Unlock" -> plugin.guildService.releaseVaultLock(gId, vIndex, true)
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
                    val cachedId = plugin.playerGuildCache[p.uniqueId]

                    if (cachedId == gId) {
                        // 1. 发送通知
                        if (p.uniqueId == newOwnerUuid) {
                            p.sendMessage("§a[公会] 你已被任命为新会长！")
                        } else {
                            p.sendMessage("§e[公会] 公会会长已变更为 $newOwnerName。")
                        }

                        // 2. 彻底移除旧缓存，强制下次任何操作都重新读库
                        plugin.playerGuildCache.remove(p.uniqueId)

                        // 3. 异步预加载新数据 (可选，但建议延迟 1 秒，等待数据库写入同步)
                        plugin.server.scheduler.runTaskLaterAsynchronously(plugin, Runnable {
                            val newId = plugin.dbManager.getGuildIdByPlayer(p.uniqueId)
                            if (newId != null) {
                                plugin.playerGuildCache[p.uniqueId] = newId
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
                        val realGuildId = plugin.dbManager.getGuildIdByPlayer(p.uniqueId)
                        if (realGuildId == targetGuildId) {
                            // 修正并激活缓存
                            plugin.playerGuildCache[p.uniqueId] = targetGuildId
                            // 此时缓存已回正，Chat 功能立即恢复正常
                        }
                    })
                }
            }
        }
    }
}