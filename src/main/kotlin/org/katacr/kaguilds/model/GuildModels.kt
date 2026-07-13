package org.katacr.kaguilds.model

import java.util.UUID

/**
 * 公会基础数据快照，用于菜单展示、服务层计算和 Placeholder 输出。
 */
data class GuildData(
    val id: Int,
    val name: String,
    val ownerUuid: String,
    val ownerName: String?,
    val level: Int,
    val exp: Int,
    val balance: Double,
    val announcement: String?,
    val maxMembers: Int,
    val teleportLocation: String?,
    val createTime: Long,
    val memberCount: Int = 0,
    val icon: String? = null,
    val iconItemModel: String? = null,
    val iconCustomData: Int? = null,
    val pvpWins: Int = 0,
    val pvpLosses: Int = 0,
    val pvpDraws: Int = 0,
    val pvpTotal: Int = 0,
    val lastInterestDate: Long = 0
)

/**
 * 公会成员数据快照，用于成员列表、权限展示和贡献度管理。
 */
data class MemberData(
    val uuid: UUID,
    val name: String?,
    val role: String,
    val joinTime: Long,
    val contribution: Int = 0
)

/**
 * 公会任务进度数据快照，用于每日任务和全局任务进度展示与更新。
 */
data class GuildTaskProgress(
    val id: Int,
    val guildId: Int,
    val taskKey: String,
    val playerUuid: UUID?,
    val progress: Int,
    val target: Int,
    val completed: Boolean,
    val lastDate: String?
)

/**
 * 任务进度更新结果，区分任务完成状态与本次请求是否取得唯一奖励资格。
 */
data class TaskProgressUpdate(
    val taskProgress: GuildTaskProgress,
    val rewardGranted: Boolean
) {
    val progress: Int get() = taskProgress.progress
    val target: Int get() = taskProgress.target
    val completed: Boolean get() = taskProgress.completed
}
