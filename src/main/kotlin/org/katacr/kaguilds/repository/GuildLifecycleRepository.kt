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
                val psReq = conn.prepareStatement("DELETE FROM guild_requests WHERE guild_id = ?")
                psReq.setInt(1, guildId)
                psReq.executeUpdate()

                val psMem = conn.prepareStatement("UPDATE guild_members SET guild_id = -1, role = 'NONE' WHERE guild_id = ?")
                psMem.setInt(1, guildId)
                psMem.executeUpdate()

                val psData = conn.prepareStatement("DELETE FROM guild_data WHERE id = ?")
                psData.setInt(1, guildId)
                psData.executeUpdate()

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
        (name, owner_uuid, owner_name, level, balance, exp, icon, icon_item_model, icon_custom_data, create_time, announcement, max_members)
        VALUES (?, ?, ?, 1, 0, 0, ?, ?, ?, ?, ?, ?);
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

                val sqlMember = "INSERT INTO guild_members (guild_id, player_uuid, player_name, role, join_time) VALUES (?, ?, ?, 'OWNER', ?)"
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
                e.printStackTrace()
                -1
            } finally {
                conn.autoCommit = true
            }
        } ?: -1
    }

    fun transferGuildOwnership(guildId: Int, oldOwnerUuid: UUID, newOwnerUuid: UUID, newOwnerName: String): Boolean {
        val updateOwner = "UPDATE guild_data SET owner_uuid = ?, owner_name = ? WHERE id = ?"
        val updateRole = "UPDATE guild_members SET role = ? WHERE player_uuid = ?"

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
                        ps.executeUpdate()
                    }

                    conn.prepareStatement(updateRole).use { ps ->
                        ps.setString(1, "MEMBER")
                        ps.setString(2, oldOwnerUuid.toString())
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
