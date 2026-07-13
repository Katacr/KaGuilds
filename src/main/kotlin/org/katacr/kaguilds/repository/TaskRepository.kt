package org.katacr.kaguilds.repository

import org.katacr.kaguilds.DatabaseManager
import org.katacr.kaguilds.model.GuildTaskProgress
import org.katacr.kaguilds.model.TaskProgressUpdate
import java.sql.Connection
import java.sql.Statement
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * 公会任务进度数据仓库，负责 guild_task_progress 表的查询、递增、重置和过期检查。
 */
class TaskRepository(private val db: DatabaseManager) {
    private val plugin = db.plugin

    fun getGuildTaskProgress(guildId: Int, taskKey: String, playerUuid: UUID? = null): GuildTaskProgress? {
        val sql = if (playerUuid != null) {
            "SELECT * FROM guild_task_progress WHERE guild_id = ? AND task_key = ? AND player_uuid = ?"
        } else {
            "SELECT * FROM guild_task_progress WHERE guild_id = ? AND task_key = ? AND (player_uuid IS NULL OR player_uuid = '')"
        }
        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setString(2, taskKey)
                    if (playerUuid != null) {
                        ps.setString(3, playerUuid.toString())
                    }
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        mapTaskProgress(rs)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("获取任务进度时出错: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun getAllGuildTaskProgress(
        guildId: Int
    ): Pair<Map<String, GuildTaskProgress>, List<GuildTaskProgress>> {
        val globalProgressMap = mutableMapOf<String, GuildTaskProgress>()
        val dailyProgressList = mutableListOf<GuildTaskProgress>()
        val sql = "SELECT * FROM guild_task_progress WHERE guild_id = ?"
        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    val rs = ps.executeQuery()
                    while (rs.next()) {
                        val progress = mapTaskProgress(rs)
                        if (progress.playerUuid == null) {
                            globalProgressMap[progress.taskKey] = progress
                        } else {
                            dailyProgressList.add(progress)
                        }
                    }
                }
            }
            Pair(globalProgressMap, dailyProgressList)
        } catch (e: Exception) {
            plugin.logger.severe("获取所有任务进度时出错: ${e.message}")
            e.printStackTrace()
            Pair(emptyMap(), emptyList())
        }
    }

    fun incrementTaskProgress(
        guildId: Int,
        taskKey: String,
        playerUuid: UUID? = null,
        increment: Int = 1,
        target: Int = 0
    ): TaskProgressUpdate? {
        val today = currentTaskDate()
        return try {
            db.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val existingProgress = findTaskProgressForUpdate(conn, guildId, taskKey, playerUuid)

                    if (hasTaskRewardClaim(conn, guildId, taskKey, playerUuid, today)) {
                        val completedProgress = existingProgress?.copy(completed = true, lastDate = today)
                            ?: GuildTaskProgress(
                                id = -1,
                                guildId = guildId,
                                taskKey = taskKey,
                                playerUuid = playerUuid,
                                progress = target,
                                target = target,
                                completed = true,
                                lastDate = today
                            )
                        conn.commit()
                        return TaskProgressUpdate(completedProgress, rewardGranted = false)
                    }

                    if (existingProgress != null) {
                        val isNewDay = existingProgress.lastDate != today

                        if (existingProgress.completed && !isNewDay) {
                            recordTaskRewardClaim(conn, guildId, taskKey, playerUuid, today)
                            conn.commit()
                            return TaskProgressUpdate(existingProgress, rewardGranted = false)
                        }

                        val newProgress = if (isNewDay) {
                            minOf(increment.coerceAtLeast(0), target.coerceAtLeast(0))
                        } else {
                            minOf((existingProgress.progress + increment).coerceAtLeast(0), target.coerceAtLeast(0))
                        }
                        val isCompleted = newProgress >= target

                        val updateSql = "UPDATE guild_task_progress SET progress = ?, target = ?, completed = ?, last_date = ? WHERE id = ?"
                        conn.prepareStatement(updateSql).use { ps ->
                            ps.setInt(1, newProgress)
                            ps.setInt(2, target)
                            ps.setInt(3, if (isCompleted) 1 else 0)
                            ps.setString(4, today)
                            ps.setInt(5, existingProgress.id)
                            ps.executeUpdate()
                        }

                        val rewardGranted = isCompleted && recordTaskRewardClaim(
                            conn, guildId, taskKey, playerUuid, today
                        )
                        conn.commit()
                        return TaskProgressUpdate(
                            existingProgress.copy(
                                progress = newProgress,
                                target = target,
                                completed = isCompleted,
                                lastDate = today
                            ),
                            rewardGranted
                        )
                    } else {
                        val initialProgress = minOf(increment.coerceAtLeast(0), target.coerceAtLeast(0))
                        val isCompleted = initialProgress >= target
                        val insertSql = """
                            INSERT INTO guild_task_progress (guild_id, task_key, player_uuid, progress, target, completed, last_date)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()

                        val insertedId = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                            ps.setInt(1, guildId)
                            ps.setString(2, taskKey)
                            if (playerUuid != null) {
                                ps.setString(3, playerUuid.toString())
                            } else {
                                ps.setString(3, "")
                            }
                            ps.setInt(4, initialProgress)
                            ps.setInt(5, target)
                            ps.setInt(6, if (isCompleted) 1 else 0)
                            ps.setString(7, today)
                            ps.executeUpdate()

                            val generatedKeys = ps.generatedKeys
                            if (generatedKeys.next()) generatedKeys.getInt(1) else -1
                        }

                        val rewardGranted = isCompleted && recordTaskRewardClaim(
                            conn, guildId, taskKey, playerUuid, today
                        )
                        conn.commit()
                        return TaskProgressUpdate(
                            GuildTaskProgress(
                                id = insertedId,
                                guildId = guildId,
                                taskKey = taskKey,
                                playerUuid = playerUuid,
                                progress = initialProgress,
                                target = target,
                                completed = isCompleted,
                                lastDate = today
                            ),
                            rewardGranted
                        )
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("[Task-DB] 数据库操作失败，回滚事务: ${e.message}")
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("[Task-DB] 增加任务进度时出错: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * 在当前事务中锁定任务进度，避免多个子服同时按旧进度计算。
     */
    private fun findTaskProgressForUpdate(
        conn: Connection,
        guildId: Int,
        taskKey: String,
        playerUuid: UUID?
    ): GuildTaskProgress? {
        val playerCondition = if (playerUuid == null) {
            "(player_uuid IS NULL OR player_uuid = '')"
        } else {
            "player_uuid = ?"
        }
        val lockSuffix = if (isMySql()) " FOR UPDATE" else ""
        val sql = """
            SELECT * FROM guild_task_progress
            WHERE guild_id = ? AND task_key = ? AND $playerCondition
            ORDER BY id LIMIT 1$lockSuffix
        """.trimIndent()

        conn.prepareStatement(sql).use { ps ->
            ps.setInt(1, guildId)
            ps.setString(2, taskKey)
            if (playerUuid != null) {
                ps.setString(3, playerUuid.toString())
            }
            ps.executeQuery().use { rs ->
                return if (rs.next()) mapTaskProgress(rs) else null
            }
        }
    }

    /**
     * 查询当天是否已经存在唯一奖励领取凭证。
     */
    private fun hasTaskRewardClaim(
        conn: Connection,
        guildId: Int,
        taskKey: String,
        playerUuid: UUID?,
        taskDate: String
    ): Boolean {
        val scopeType = if (playerUuid == null) "GUILD" else "PLAYER"
        val scopeId = playerUuid?.toString() ?: guildId.toString()
        val sql = """
            SELECT 1 FROM guild_task_reward_claims
            WHERE scope_type = ? AND scope_id = ? AND task_key = ? AND task_date = ?
        """.trimIndent()

        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, scopeType)
            ps.setString(2, scopeId)
            ps.setString(3, taskKey)
            ps.setString(4, taskDate)
            ps.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    /**
     * 原子登记奖励领取凭证，返回当前请求是否首次取得奖励资格。
     */
    private fun recordTaskRewardClaim(
        conn: Connection,
        guildId: Int,
        taskKey: String,
        playerUuid: UUID?,
        taskDate: String
    ): Boolean {
        val insertPrefix = if (isMySql()) "INSERT IGNORE" else "INSERT OR IGNORE"
        val scopeType = if (playerUuid == null) "GUILD" else "PLAYER"
        val scopeId = playerUuid?.toString() ?: guildId.toString()
        val sql = """
            $insertPrefix INTO guild_task_reward_claims
                (scope_type, scope_id, task_key, task_date, guild_id, claimed_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, scopeType)
            ps.setString(2, scopeId)
            ps.setString(3, taskKey)
            ps.setString(4, taskDate)
            ps.setInt(5, guildId)
            ps.setLong(6, System.currentTimeMillis())
            return ps.executeUpdate() == 1
        }
    }

    private fun isMySql(): Boolean {
        return plugin.config.getString("database.type", "sqlite").equals("mysql", ignoreCase = true)
    }

    /**
     * 根据配置的每日重置时间计算当前任务周期日期。
     */
    private fun currentTaskDate(): String {
        val resetTimeConfig = plugin.config.getString("task.reset_time", "00:00:00") ?: "00:00:00"
        val resetTime = try {
            LocalTime.parse(resetTimeConfig)
        } catch (_: Exception) {
            LocalTime.MIDNIGHT
        }
        val now = LocalDateTime.now()
        return if (now.toLocalTime().isBefore(resetTime)) {
            now.toLocalDate().minusDays(1).toString()
        } else {
            now.toLocalDate().toString()
        }
    }

    fun resetTaskProgress(guildId: Int, taskKey: String, playerUuid: UUID? = null): Boolean {
        val today = currentTaskDate()
        val sql = if (playerUuid != null) {
            "UPDATE guild_task_progress SET progress = 0, completed = 0, last_date = ? WHERE guild_id = ? AND task_key = ? AND player_uuid = ?"
        } else {
            "UPDATE guild_task_progress SET progress = 0, completed = 0, last_date = ? WHERE guild_id = ? AND task_key = ? AND (player_uuid IS NULL OR player_uuid = '')"
        }
        return try {
            db.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val progressReset = conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, today)
                        ps.setInt(2, guildId)
                        ps.setString(3, taskKey)
                        if (playerUuid != null) {
                            ps.setString(4, playerUuid.toString())
                        }
                        ps.executeUpdate() > 0
                    }

                    val scopeType = if (playerUuid == null) "GUILD" else "PLAYER"
                    val scopeId = playerUuid?.toString() ?: guildId.toString()
                    val claimReset = conn.prepareStatement(
                        "DELETE FROM guild_task_reward_claims WHERE scope_type = ? AND scope_id = ? AND task_key = ? AND task_date = ?"
                    ).use { ps ->
                        ps.setString(1, scopeType)
                        ps.setString(2, scopeId)
                        ps.setString(3, taskKey)
                        ps.setString(4, today)
                        ps.executeUpdate() > 0
                    }

                    conn.commit()
                    progressReset || claimReset
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("重置任务进度时出错: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun getCompletedTaskKeys(guildId: Int, playerUuid: UUID? = null): Set<String> {
        val today = currentTaskDate()
        val completedKeys = mutableSetOf<String>()

        try {
            db.connection.use { conn ->
                val scopeType = if (playerUuid == null) "GUILD" else "PLAYER"
                val scopeId = playerUuid?.toString() ?: guildId.toString()
                conn.prepareStatement(
                    "SELECT task_key FROM guild_task_reward_claims WHERE scope_type = ? AND scope_id = ? AND task_date = ?"
                ).use { ps ->
                    ps.setString(1, scopeType)
                    ps.setString(2, scopeId)
                    ps.setString(3, today)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            completedKeys.add(rs.getString("task_key"))
                        }
                    }
                }

                val legacySql = if (playerUuid != null) {
                    "SELECT task_key FROM guild_task_progress WHERE player_uuid = ? AND last_date = ? AND completed = 1"
                } else {
                    "SELECT task_key FROM guild_task_progress WHERE guild_id = ? AND (player_uuid IS NULL OR player_uuid = '') AND last_date = ? AND completed = 1"
                }
                conn.prepareStatement(legacySql).use { ps ->
                    if (playerUuid != null) {
                        ps.setString(1, playerUuid.toString())
                        ps.setString(2, today)
                    } else {
                        ps.setInt(1, guildId)
                        ps.setString(2, today)
                    }
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            completedKeys.add(rs.getString("task_key"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("获取已完成任务缓存失败: ${e.message}")
        }
        return completedKeys
    }

    fun checkAndResetDailyTasks(guildId: Int, playerUuid: UUID) {
        val today = currentTaskDate()
        val sql = """
            SELECT task_key FROM guild_task_progress
            WHERE guild_id = ? AND player_uuid = ? AND last_date != ?
        """.trimIndent()

        try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setString(2, playerUuid.toString())
                    ps.setString(3, today)

                    val rs = ps.executeQuery()
                    val expiredTasks = mutableListOf<String>()

                    while (rs.next()) {
                        expiredTasks.add(rs.getString("task_key"))
                    }

                    if (expiredTasks.isNotEmpty()) {
                        val updateSql = """
                            UPDATE guild_task_progress
                            SET progress = 0, completed = 0, last_date = ?
                            WHERE guild_id = ? AND player_uuid = ? AND task_key = ?
                        """.trimIndent()

                        conn.prepareStatement(updateSql).use { updatePs ->
                            for (taskKey in expiredTasks) {
                                updatePs.setString(1, today)
                                updatePs.setInt(2, guildId)
                                updatePs.setString(3, playerUuid.toString())
                                updatePs.setString(4, taskKey)
                                updatePs.executeUpdate()
                            }
                        }

                        plugin.taskManager.dailyDoneCache.remove(playerUuid)
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("检查并重置每日任务时出错: ${e.message}")
        }
    }

    fun checkAndResetGlobalTasks(guildId: Int) {
        val today = currentTaskDate()
        val sql = """
            SELECT task_key FROM guild_task_progress
            WHERE guild_id = ? AND (player_uuid IS NULL OR player_uuid = '') AND last_date != ?
        """.trimIndent()

        try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setString(2, today)

                    val rs = ps.executeQuery()
                    val expiredTasks = mutableListOf<String>()

                    while (rs.next()) {
                        expiredTasks.add(rs.getString("task_key"))
                    }

                    if (expiredTasks.isNotEmpty()) {
                        val updateSql = """
                            UPDATE guild_task_progress
                            SET progress = 0, completed = 0, last_date = ?
                            WHERE guild_id = ? AND (player_uuid IS NULL OR player_uuid = '') AND task_key = ?
                        """.trimIndent()

                        conn.prepareStatement(updateSql).use { updatePs ->
                            for (taskKey in expiredTasks) {
                                updatePs.setString(1, today)
                                updatePs.setInt(2, guildId)
                                updatePs.setString(3, taskKey)
                                updatePs.executeUpdate()
                            }
                        }

                        plugin.taskManager.guildDoneCache.remove(guildId)
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("检查并重置全局任务时出错: ${e.message}")
        }
    }

    private fun mapTaskProgress(rs: java.sql.ResultSet): GuildTaskProgress {
        val playerUuidValue = rs.getString("player_uuid")
        return GuildTaskProgress(
            id = rs.getInt("id"),
            guildId = rs.getInt("guild_id"),
            taskKey = rs.getString("task_key"),
            playerUuid = playerUuidValue?.takeIf { it.isNotBlank() }?.let { UUID.fromString(it) },
            progress = rs.getInt("progress"),
            target = rs.getInt("target"),
            completed = rs.getInt("completed") == 1,
            lastDate = rs.getString("last_date")
        )
    }
}
