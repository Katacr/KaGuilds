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
            "Chat" -> {

            val targetGuildId = `in`.readInt()
            val senderName = `in`.readUTF()
            val msgContent = `in`.readUTF()

            val formattedMsg = plugin.langManager.get("chat-format",
                "player" to senderName, "message" to msgContent, withPrefix = false)

            // 这里的逻辑现在变得非常快
            plugin.server.onlinePlayers.forEach { onlinePlayer ->
                val cachedId = plugin.playerGuildCache[onlinePlayer.uniqueId]
                if (cachedId != null && cachedId == targetGuildId) {
                    onlinePlayer.sendMessage(formattedMsg)
                    }
                }
            }
            // 在 PluginMessageListener.kt 的 when(subChannel) 块中
            "SyncCache" -> {
                val action = `in`.readUTF()
                val uuidStr = `in`.readUTF()

                try {
                    val targetUuid = UUID.fromString(uuidStr)
                    when (action) {
                        "REMOVE" -> {
                            plugin.playerGuildCache.remove(targetUuid)
                        }
                        "UPDATE" -> {
                            val newGuildId = `in`.readInt() // 读取传过来的 guildId
                            // 如果该玩家在我们这台服务器在线，他的聊天功能将立即解锁
                            plugin.playerGuildCache[targetUuid] = newGuildId
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
            "CrossInvite" -> {
                val targetName = `in`.readUTF()
                val guildId = `in`.readInt()
                val guildName = `in`.readUTF()

                // 检查目标玩家是否在该子服在线
                val targetPlayer = plugin.server.getPlayer(targetName)
                if (targetPlayer != null) {
                    // 1. 检查他是否已经有公会
                    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                        if (plugin.dbManager.getGuildIdByPlayer(targetPlayer.uniqueId) == null) {
                            // 2. 存入本服邀请缓存
                            plugin.inviteCache[targetPlayer.uniqueId] = guildId

                            // 3. 发送提示（回到主线程发消息）
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                targetPlayer.sendMessage(plugin.langManager.get("invite-received-target", "guild" to guildName))
                            })

                            // 4. 设置过期
                            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                                plugin.inviteCache.remove(targetPlayer.uniqueId)
                            }, 1200L)
                        }
                    })
                }
            }
        }
    }
}