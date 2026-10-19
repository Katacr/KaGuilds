package org.katacr.kaguilds.util

import java.util.UUID

/**
 * 跨服消息子频道与字段集中定义。
 *
 * 约定（重要）：
 * - **身份一律使用 UUID**；玩家名/公会名等仅作为显示用途，不用于身份判断。
 * - 每个子频道的字段顺序在此集中维护，发送端与接收端都引用这里的常量与编码函数，
 *   避免散落的 writeUTF/writeInt 与索引魔法值导致错位。
 * - 通道名与字段顺序属 KaProxy 透传契约（代理对非 ServerHello 包纯透传），
 *   修改需全服后端同步升级；KaProxy 本身无需改动。
 */
object NetworkCodec {

    /** 业务通道名（KaProxy 透传）。 */
    const val CHANNEL = "kaguilds:chat"

    // ---- 子频道名 ----
    const val ONLINE_PLAYERS_LIST = "OnlinePlayersList"   // 代理→后端
    const val SERVER_IDENTIFY = "ServerIdentify"          // 代理→后端
    const val SERVER_HELLO = "ServerHello"                // 后端→代理（探针）
    const val CHAT = "Chat"
    const val TASK_COMPLETED = "TaskCompleted"
    const val GLOBAL_TASK_PROGRESS = "GlobalTaskProgress"
    const val GLOBAL_TASK_COMPLETED = "GlobalTaskCompleted"
    const val SYNC_CACHE = "SyncCache"
    const val NOTIFY_REQUEST = "NotifyRequest"
    const val CROSS_INVITE = "CrossInvite"
    const val MEMBER_JOIN = "MemberJoin"
    const val MEMBER_LEAVE = "MemberLeave"
    const val MEMBER_KICK = "MemberKick"
    const val RENAME_SYNC = "RenameSync"
    const val BANK_SYNC = "BankSync"
    const val BUFF_SYNC = "BuffSync"
    const val DELETE_SYNC = "DeleteSync"
    const val ADMIN_RENAME_SYNC = "AdminRenameSync"
    const val VAULT_SYNC = "VaultSync"
    const val GUILD_TRANSFER = "GuildTransfer"
    const val FORCE_REFRESH_MEMBERS = "ForceRefreshMembers"
    const val PVP_BATTLE_START = "PvPBattleStart"

    // ---- 编码辅助 ----

    fun start(subChannel: String): NetworkOutput = NetworkOutput.of(subChannel)

    /** Chat: targetGuildId(Int), senderName, msgContent, roleDisplayName, coloredMessage */
    fun encodeChat(targetGuildId: Int, senderName: String, msgContent: String, roleDisplayName: String, coloredMessage: String): NetworkOutput =
        start(CHAT).also {
            it.stream.writeInt(targetGuildId)
            it.stream.writeUTF(senderName)
            it.stream.writeUTF(msgContent)
            it.stream.writeUTF(roleDisplayName)
            it.stream.writeUTF(coloredMessage)
        }

    /** NotifyRequest: targetGuildId(Int), guildName, applicantName */
    fun encodeNotifyRequest(targetGuildId: Int, guildName: String, applicantName: String): NetworkOutput =
        start(NOTIFY_REQUEST).also {
            it.stream.writeInt(targetGuildId)
            it.stream.writeUTF(guildName)
            it.stream.writeUTF(applicantName)
        }

    /** MemberJoin: guildId(Int), playerUuid, playerName（名称仅显示） */
    fun encodeMemberJoin(guildId: Int, playerUuid: UUID, playerName: String): NetworkOutput =
        start(MEMBER_JOIN).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(playerUuid.toString())
            it.stream.writeUTF(playerName)
        }

    /** MemberLeave: guildId(Int), playerUuid, playerName */
    fun encodeMemberLeave(guildId: Int, playerUuid: UUID, playerName: String): NetworkOutput =
        start(MEMBER_LEAVE).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(playerUuid.toString())
            it.stream.writeUTF(playerName)
        }

    /** MemberKick: guildId(Int), targetUuid, targetName（名称仅显示） */
    fun encodeMemberKick(guildId: Int, targetUuid: UUID, targetName: String): NetworkOutput =
        start(MEMBER_KICK).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(targetUuid.toString())
            it.stream.writeUTF(targetName)
        }

    /** CrossInvite: targetUuid, guildId(Int), guildName, senderName（名称仅显示） */
    fun encodeCrossInvite(targetUuid: UUID, guildId: Int, guildName: String, senderName: String): NetworkOutput =
        start(CROSS_INVITE).also {
            it.stream.writeUTF(targetUuid.toString())
            it.stream.writeInt(guildId)
            it.stream.writeUTF(guildName)
            it.stream.writeUTF(senderName)
        }

    /** RenameSync: guildId(Int), newName */
    fun encodeRenameSync(guildId: Int, newName: String): NetworkOutput =
        start(RENAME_SYNC).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(newName)
        }

    /** AdminRenameSync: guildId(Int), newName */
    fun encodeAdminRenameSync(guildId: Int, newName: String): NetworkOutput =
        start(ADMIN_RENAME_SYNC).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(newName)
        }

    /** BankSync: guildId(Int), playerName, type, amount(Double) */
    fun encodeBankSync(guildId: Int, playerName: String, type: String, amount: Double): NetworkOutput =
        start(BANK_SYNC).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(playerName)
            it.stream.writeUTF(type)
            it.stream.writeDouble(amount)
        }

    /** BuffSync: guildId(Int), typeName, seconds(Int), amplifier(Int), buyerName, buffName */
    fun encodeBuffSync(guildId: Int, typeName: String, seconds: Int, amplifier: Int, buyerName: String, buffName: String): NetworkOutput =
        start(BUFF_SYNC).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(typeName)
            it.stream.writeInt(seconds)
            it.stream.writeInt(amplifier)
            it.stream.writeUTF(buyerName)
            it.stream.writeUTF(buffName)
        }

    /** SyncCache: playerUuid, guildId(Int, -1 表示无公会) */
    fun encodeSyncCache(playerUuid: UUID, guildId: Int): NetworkOutput =
        start(SYNC_CACHE).also {
            it.stream.writeUTF(playerUuid.toString())
            it.stream.writeInt(guildId)
        }

    /** DeleteSync: guildId(Int), notifyKey, count(Int), count×uuid */
    fun encodeDeleteSync(guildId: Int, notifyKey: String, memberUuids: List<UUID>): NetworkOutput =
        start(DELETE_SYNC).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(notifyKey)
            it.stream.writeInt(memberUuids.size)
            memberUuids.forEach { u -> it.stream.writeUTF(u.toString()) }
        }

    /** VaultSync: action, guildId(Int), vaultIndex(Int), operatorUuid */
    fun encodeVaultSync(action: String, guildId: Int, vaultIndex: Int, operatorUuid: UUID): NetworkOutput =
        start(VAULT_SYNC).also {
            it.stream.writeUTF(action)
            it.stream.writeInt(guildId)
            it.stream.writeInt(vaultIndex)
            it.stream.writeUTF(operatorUuid.toString())
        }

    /** GuildTransfer: guildId(Int), newOwnerUuid, newOwnerName */
    fun encodeGuildTransfer(guildId: Int, newOwnerUuid: UUID, newOwnerName: String): NetworkOutput =
        start(GUILD_TRANSFER).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(newOwnerUuid.toString())
            it.stream.writeUTF(newOwnerName)
        }

    /** ForceRefreshMembers: guildId(Int) */
    fun encodeForceRefreshMembers(guildId: Int): NetworkOutput =
        start(FORCE_REFRESH_MEMBERS).also { it.stream.writeInt(guildId) }

    /** TaskCompleted: guildId(Int), taskName */
    fun encodeTaskCompleted(guildId: Int, taskName: String): NetworkOutput =
        start(TASK_COMPLETED).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(taskName)
        }

    /** GlobalTaskProgress: guildId(Int), taskKey, taskName, progress(Int), target(Int) */
    fun encodeGlobalTaskProgress(guildId: Int, taskKey: String, taskName: String, progress: Int, target: Int): NetworkOutput =
        start(GLOBAL_TASK_PROGRESS).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(taskKey)
            it.stream.writeUTF(taskName)
            it.stream.writeInt(progress)
            it.stream.writeInt(target)
        }

    /** GlobalTaskCompleted: guildId(Int), taskKey, taskName */
    fun encodeGlobalTaskCompleted(guildId: Int, taskKey: String, taskName: String): NetworkOutput =
        start(GLOBAL_TASK_COMPLETED).also {
            it.stream.writeInt(guildId)
            it.stream.writeUTF(taskKey)
            it.stream.writeUTF(taskName)
        }

    /** PvPBattleStart: targetGuildId(Int), serverId, ownGuildName, enemyGuildName */
    fun encodePvPBattleStart(targetGuildId: Int, serverId: String, ownGuildName: String, enemyGuildName: String): NetworkOutput =
        start(PVP_BATTLE_START).also {
            it.stream.writeInt(targetGuildId)
            it.stream.writeUTF(serverId)
            it.stream.writeUTF(ownGuildName)
            it.stream.writeUTF(enemyGuildName)
        }
}
