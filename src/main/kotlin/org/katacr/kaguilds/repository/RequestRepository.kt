package org.katacr.kaguilds.repository

import org.katacr.kaguilds.DatabaseManager
import java.util.UUID

/**
 * 公会申请数据仓库，负责 guild_requests 表的申请查询、写入和删除。
 */
class RequestRepository(private val db: DatabaseManager) {

    fun getRequests(guildId: Int): List<Pair<UUID, Long>> {
        val list = mutableListOf<Pair<UUID, Long>>()
        db.connection.use { conn ->
            val ps = conn.prepareStatement("SELECT player_uuid, request_time FROM guild_requests WHERE guild_id = ?")
            ps.setInt(1, guildId)
            val rs = ps.executeQuery()
            while (rs.next()) {
                list.add(UUID.fromString(rs.getString("player_uuid")) to rs.getLong("request_time"))
            }
        }
        return list
    }

    fun removeRequest(guildId: Int, playerUuid: UUID): Boolean {
        val sql = "DELETE FROM guild_requests WHERE guild_id = ? AND player_uuid = ?"
        db.connection.use { conn ->
            return conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, guildId)
                ps.setString(2, playerUuid.toString())
                ps.executeUpdate() > 0
            }
        }
    }

    fun addRequest(guildId: Int, playerUuid: UUID, playerName: String): Boolean {
        val sql = "INSERT INTO guild_requests (guild_id, player_uuid, player_name, request_time) VALUES (?, ?, ?, ?)"
        db.connection.use { conn ->
            val ps = conn.prepareStatement(sql)
            ps.setInt(1, guildId)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, playerName)
            ps.setLong(4, System.currentTimeMillis())
            return ps.executeUpdate() > 0
        }
    }
}
