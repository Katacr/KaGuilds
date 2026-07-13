package org.katacr.kaguilds.repository

import org.bukkit.Bukkit
import org.katacr.kaguilds.DatabaseManager
import org.katacr.kaguilds.model.MemberData
import java.sql.Connection
import java.util.UUID

/**
 * 公会成员数据仓库，负责成员关系、职位、贡献度和玩家名/UUID 映射的 SQL 访问。
 */
class MemberRepository(private val db: DatabaseManager) {
    private val plugin = db.plugin

    fun getGuildIdByPlayer(uuid: UUID): Int? {
        db.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT guild_id FROM guild_members WHERE player_uuid = ? AND guild_id > 0")
            ps.setString(1, uuid.toString())
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getInt("guild_id")
        }
        return null
    }

    fun getMemberCount(guildId: Int, existingConn: Connection? = null): Int {
        val conn = existingConn ?: db.dataSource?.connection ?: throw IllegalStateException(plugin.langManager.get("error-database"))
        val ps = conn.prepareStatement("SELECT COUNT(*) FROM guild_members WHERE guild_id = ?")
        ps.setInt(1, guildId)
        val rs = ps.executeQuery()
        val count = if (rs.next()) rs.getInt(1) else 0

        if (existingConn == null) {
            rs.close()
            ps.close()
            conn.close()
        }

        return count
    }

    fun getMemberNames(guildId: Int): List<String> {
        return getMemberDisplayNames(guildId, resolveFromUuid = true)
    }

    fun getMemberUUIDs(guildId: Int): List<UUID> {
        val uuids = mutableListOf<UUID>()
        db.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT player_uuid FROM guild_members WHERE guild_id = ?")
            ps.setInt(1, guildId)
            val rs = ps.executeQuery()
            while (rs.next()) {
                try {
                    uuids.add(UUID.fromString(rs.getString("player_uuid")))
                } catch (_: Exception) {
                    continue
                }
            }
        }
        return uuids
    }

    fun getTotalMemberCount(): Int {
        val sql = "SELECT COUNT(*) FROM guild_members"
        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    val rs = ps.executeQuery()
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    fun isStaff(playerUuid: UUID, guildId: Int): Boolean {
        val sql = "SELECT role FROM guild_members WHERE player_uuid = ? AND guild_id = ?"
        return try {
            db.dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerUuid.toString())
                    ps.setInt(2, guildId)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        val role = rs.getString("role") ?: "MEMBER"
                        return role == "OWNER" || role == "ADMIN"
                    }
                }
            }
            false
        } catch (e: Exception) {
            plugin.logger.severe("[DEBUG] isStaff check error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun getPlayerRole(uuid: UUID, guildId: Int? = null): String? {
        val sql = if (guildId != null) {
            "SELECT role FROM guild_members WHERE player_uuid = ? AND guild_id = ?"
        } else {
            "SELECT role FROM guild_members WHERE player_uuid = ? AND guild_id > 0"
        }
        return db.connection.use { conn ->
            val ps = conn.prepareStatement(sql)
            ps.setString(1, uuid.toString())
            if (guildId != null) {
                ps.setInt(2, guildId)
            }
            val rs = ps.executeQuery()
            if (rs.next()) rs.getString("role") else null
        }
    }

    fun updateMemberRole(guildId: Int, targetUuid: UUID, newRole: String): Boolean {
        db.connection.use { conn ->
            val ps = conn.prepareStatement("UPDATE guild_members SET role = ? WHERE player_uuid = ? AND guild_id = ?")
            ps.setString(1, newRole)
            ps.setString(2, targetUuid.toString())
            ps.setInt(3, guildId)
            return ps.executeUpdate() > 0
        }
    }

    fun getRoleInGuild(guildId: Int, targetUuid: UUID): String? {
        db.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT role FROM guild_members WHERE player_uuid = ? AND guild_id = ?")
            ps.setString(1, targetUuid.toString())
            ps.setInt(2, guildId)
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getString("role")
        }
        return null
    }

    fun removeMember(guildId: Int, playerUuid: UUID): Boolean {
        val sql = "UPDATE guild_members SET guild_id = -1, role = 'NONE' WHERE guild_id = ? AND player_uuid = ?"
        db.connection.use { conn ->
            val ps = conn.prepareStatement(sql)
            ps.setInt(1, guildId)
            ps.setString(2, playerUuid.toString())
            return ps.executeUpdate() > 0
        }
    }

    fun addMember(guildId: Int, playerUuid: UUID, playerName: String, role: String): Boolean {
        val checkSql = "SELECT id FROM guild_members WHERE player_uuid = ? AND guild_id = -1"
        db.connection.use { conn ->
            conn.prepareStatement(checkSql).use { ps ->
                ps.setString(1, playerUuid.toString())
                val rs = ps.executeQuery()
                if (rs.next()) {
                    val updateSql = "UPDATE guild_members SET guild_id = ?, player_name = ?, role = ?, join_time = ? WHERE player_uuid = ? AND guild_id = -1"
                    conn.prepareStatement(updateSql).use { updatePs ->
                        updatePs.setInt(1, guildId)
                        updatePs.setString(2, playerName)
                        updatePs.setString(3, role)
                        updatePs.setLong(4, System.currentTimeMillis())
                        updatePs.setString(5, playerUuid.toString())
                        return updatePs.executeUpdate() > 0
                    }
                }
            }

            val sql = "INSERT INTO guild_members (guild_id, player_uuid, player_name, role, join_time) VALUES (?, ?, ?, ?, ?)"
            val ps = conn.prepareStatement(sql)
            ps.setInt(1, guildId)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, playerName)
            ps.setString(4, role)
            ps.setLong(5, System.currentTimeMillis())
            return ps.executeUpdate() > 0
        }
    }

    fun ensurePlayerExists(playerUuid: UUID, playerName: String): Boolean {
        return try {
            db.dataSource?.connection?.use { conn ->
                val checkSql = "SELECT id FROM guild_members WHERE player_uuid = ? AND guild_id = -1 LIMIT 1"
                conn.prepareStatement(checkSql).use { ps ->
                    ps.setString(1, playerUuid.toString())
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        val updateSql = "UPDATE guild_members SET player_name = ? WHERE player_uuid = ? AND guild_id = -1"
                        conn.prepareStatement(updateSql).use { updatePs ->
                            updatePs.setString(1, playerName)
                            updatePs.setString(2, playerUuid.toString())
                            updatePs.executeUpdate()
                        }
                        return true
                    }
                }

                val insertSql = "INSERT INTO guild_members (guild_id, player_uuid, player_name, role, join_time, contribution) VALUES (-1, ?, ?, 'NONE', 0, 0)"
                conn.prepareStatement(insertSql).use { ps ->
                    ps.setString(1, playerUuid.toString())
                    ps.setString(2, playerName)
                    ps.executeUpdate() > 0
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getUuidByPlayerName(playerName: String): UUID? {
        return findPlayerUuidByName(playerName, preferOnlinePlayer = false)
    }

    fun getPlayerUuidByName(playerName: String): UUID? {
        return findPlayerUuidByName(playerName, preferOnlinePlayer = true)
    }

    fun getGuildMembers(guildId: Int): List<MemberData> {
        val members = mutableListOf<MemberData>()
        val sql = "SELECT * FROM guild_members WHERE guild_id = ?"
        try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    val rs = ps.executeQuery()
                    while (rs.next()) {
                        members.add(
                            MemberData(
                                uuid = UUID.fromString(rs.getString("player_uuid")),
                                name = rs.getString("player_name"),
                                role = rs.getString("role") ?: "MEMBER",
                                joinTime = rs.getLong("join_time"),
                                contribution = rs.getInt("contribution")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return members
    }

    fun getGuildMemberNames(guildId: Int): List<String> {
        return getMemberDisplayNames(guildId, resolveFromUuid = false)
    }

    fun addContribution(playerUuid: UUID, amount: Int): Boolean {
        return updateContribution(playerUuid, amount, ContributionUpdateMode.ADD)
    }

    fun setContribution(playerUuid: UUID, amount: Int): Boolean {
        return updateContribution(playerUuid, amount, ContributionUpdateMode.SET)
    }

    private fun getMemberDisplayNames(guildId: Int, resolveFromUuid: Boolean): List<String> {
        val names = mutableListOf<String>()
        val selectColumn = if (resolveFromUuid) "player_uuid" else "player_name"
        val sql = "SELECT $selectColumn FROM guild_members WHERE guild_id = ?"

        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val name = if (resolveFromUuid) {
                                resolveOfflineName(rs.getString("player_uuid"))
                            } else {
                                rs.getString("player_name") ?: "未知玩家"
                            }
                            if (name != null) names.add(name)
                        }
                    }
                }
            }
            names
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun resolveOfflineName(uuidString: String?): String? {
        return try {
            if (uuidString == null) return null
            val uuid = UUID.fromString(uuidString)
            Bukkit.getOfflinePlayer(uuid).name ?: "未知玩家"
        } catch (_: Exception) {
            null
        }
    }

    private fun updateContribution(playerUuid: UUID, amount: Int, mode: ContributionUpdateMode): Boolean {
        val sql = when (mode) {
            ContributionUpdateMode.ADD -> "UPDATE guild_members SET contribution = contribution + ? WHERE player_uuid = ?"
            ContributionUpdateMode.SET -> "UPDATE guild_members SET contribution = ? WHERE player_uuid = ?"
        }

        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, amount)
                    ps.setString(2, playerUuid.toString())
                    ps.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private enum class ContributionUpdateMode {
        ADD,
        SET
    }

    fun getPlayerContribution(playerUuid: UUID): Int {
        val sql = "SELECT contribution FROM guild_members WHERE player_uuid = ?"
        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerUuid.toString())
                    val rs = ps.executeQuery()
                    if (rs.next()) rs.getInt("contribution") else 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    fun getPlayerUuid(playerName: String): UUID? {
        return findPlayerUuidByName(playerName, preferOnlinePlayer = true)
    }

    private fun findPlayerUuidByName(playerName: String, preferOnlinePlayer: Boolean): UUID? {
        if (preferOnlinePlayer) {
            val onlinePlayer = plugin.server.getPlayer(playerName)
            if (onlinePlayer != null) return onlinePlayer.uniqueId
        }

        val sql = "SELECT player_uuid FROM guild_members WHERE player_name = ? LIMIT 1"
        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerName)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) UUID.fromString(rs.getString("player_uuid")) else null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("通过玩家名获取UUID失败 ($playerName): ${e.message}")
            null
        }
    }
}
