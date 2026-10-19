package org.katacr.kaguilds.repository

import org.katacr.kaguilds.DatabaseManager
import java.sql.Statement
import java.util.UUID

/**
 * 公会生命周期仓库，负责创建、解散和会长转让这类跨表事务。
 */
class GuildLifecycleRepository(private val db: DatabaseManager) {
    private val plugin = db.plugin

    fun deleteGuild(guildId: Int): Boolean {
        db.connection.use { conn ->
            conn.autoCommit = false
            try {
                // 1. 成员软删除（保留每玩家一行的玩家档案，置为无公会）
                conn.prepareStatement("UPDATE guild_members SET guild_id = -1, role = 'NONE', contribution = 0 WHERE guild_id = ?").use { ps ->
                    ps.setInt(1, guildId)
                    ps.executeUpdate()
                }
                // 2. 清理全部关联表，避免孤儿数据与自增 ID 复用串数据
                listOf(
                    "DELETE FROM guild_requests WHERE guild_id = ?",
                    "DELETE FROM guild_vaults WHERE guild_id = ?",
                    "DELETE FROM guild_bank_logs WHERE guild_id = ?",
                    "DELETE FROM guild_task_progress WHERE guild_id = ?",
                    "DELETE FROM guild_task_reward_claims WHERE guild_id = ?",
                    "DELETE FROM guild_pvp_history WHERE red_guild_id = ? OR blue_guild_id = ?"
                ).forEach { sql ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setInt(1, guildId)
                        if (sql.count { it == '?' } == 2) ps.setInt(2, guildId)
                        ps.executeUpdate()
                    }
                }
                // 3. 删除公会主记录
                conn.prepareStatement("DELETE FROM guild_data WHERE id = ?").use { ps ->
                    ps.setInt(1, guildId)
                    ps.executeUpdate()
                }

                conn.commit()
                return true
            } catch (e: Exception) {
                conn.rollback()
                e.printStackTrace()
                return false
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun createGuild(name: String, ownerUuid: UUID, ownerName: String): Int {
        val config = plugin.config
        val sqlGuild = """
        INSERT INTO guild_data
        (name, owner_uuid, owner_name, level, balance, exp, icon, icon_item_model, icon_custom_data, create_time, announcement, max_members, total_contribution)
        VALUES (?, ?, ?, 1, 0, 0, ?, ?, ?, ?, ?, ?, 0);
        """.trimIndent()

        return db.dataSource?.connection?.use { conn ->
            conn.autoCommit = false
            try {
                val guildId = conn.prepareStatement(sqlGuild, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, name)
                    ps.setString(2, ownerUuid.toString())
                    ps.setString(3, ownerName)

                    val iconConfig = config.getConfigurationSection("guild.icon")
                    ps.setString(4, iconConfig?.getString("material") ?: "SHIELD")
                    ps.setString(5, iconConfig?.getString("item_model"))
                    ps.setInt(6, iconConfig?.getInt("custom_data") ?: 0)
                    ps.setLong(7, System.currentTimeMillis())
                    ps.setString(8, config.get("guild.motd", "name" to name) as String? ?: "welcome to guilds")
                    ps.setInt(9, config.get("level.1.max-members", 10) as Int? ?: 10)

                    ps.executeUpdate()

                    val rs = ps.generatedKeys
                    if (rs.next()) rs.getInt(1) else throw Exception("Failed to get generated guild ID")
                }

                // 冲突安全：玩家上线时 ensurePlayerExists 可能已建 guild_id=-1 的档案行，
                // 唯一键为 player_uuid，故用 upsert 复用该行，避免 Duplicate entry。
                val sqlMember = if (db.isMySQL) {
                    """
                    INSERT INTO guild_members (guild_id, player_uuid, player_name, role, join_time)
                    VALUES (?, ?, ?, 'OWNER', ?)
                    ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id), player_name = VALUES(player_name),
                        role = 'OWNER', join_time = VALUES(join_time)
                    """.trimIndent()
                } else {
                    """
                    INSERT INTO guild_members (guild_id, player_uuid, player_name, role, join_time)
                    VALUES (?, ?, ?, 'OWNER', ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET guild_id = excluded.guild_id,
                        player_name = excluded.player_name, role = 'OWNER', join_time = excluded.join_time
                    """.trimIndent()
                }
                conn.prepareStatement(sqlMember).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setString(2, ownerUuid.toString())
                    ps.setString(3, ownerName)
                    ps.setLong(4, System.currentTimeMillis())
                    ps.executeUpdate()
                }

                conn.commit()
                plugin.playerGuildCache[ownerUuid] = guildId

                guildId
            } catch (e: Exception) {
                conn.rollback()
                plugin.logger.warning("[Guild] 创建公会失败（已回滚，将触发退款）: ${e.javaClass.simpleName}: ${e.message}")
                -1
            } finally {
                conn.autoCommit = true
            }
        } ?: -1
    }

    fun transferGuildOwnership(guildId: Int, oldOwnerUuid: UUID, newOwnerUuid: UUID, newOwnerName: String): Boolean {
        val updateOwner = "UPDATE guild_data SET owner_uuid = ?, owner_name = ? WHERE id = ?"
        val updateRole = "UPDATE guild_members SET role = ? WHERE player_uuid = ? AND guild_id = ?"

        return try {
            db.dataSource?.connection?.use { conn ->
                conn.autoCommit = false
                try {
                    conn.prepareStatement(updateOwner).use { ps ->
                        ps.setString(1, newOwnerUuid.toString())
                        ps.setString(2, newOwnerName)
                        ps.setInt(3, guildId)
                        ps.executeUpdate()
                    }

                    conn.prepareStatement(updateRole).use { ps ->
                        ps.setString(1, "OWNER")
                        ps.setString(2, newOwnerUuid.toString())
                        ps.setInt(3, guildId)
                        ps.executeUpdate()
                    }

                    conn.prepareStatement(updateRole).use { ps ->
                        ps.setString(1, "MEMBER")
                        ps.setString(2, oldOwnerUuid.toString())
                        ps.setInt(3, guildId)
                        ps.executeUpdate()
                    }

                    conn.commit()
                    true
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
