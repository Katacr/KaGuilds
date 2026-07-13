package org.katacr.kaguilds.repository

import org.katacr.kaguilds.DatabaseManager

/**
 * 公会 PvP 数据仓库，负责战绩计数和对战历史写入。
 */
class PvPRepository(private val db: DatabaseManager) {

    enum class StatType(val column: String) {
        WINS("pvp_wins"),
        LOSSES("pvp_losses"),
        DRAWS("pvp_draws"),
        TOTAL("pvp_total")
    }

    fun incrementStat(guildId: Int, type: StatType) {
        val sql = "UPDATE guild_data SET ${type.column} = ${type.column} + 1 WHERE id = ?"
        db.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, guildId)
                ps.executeUpdate()
            }
        }
    }

    fun recordMatch(redGuildId: Int, blueGuildId: Int, winnerGuildId: Int?, startTime: Long, endTime: Long) {
        val sql = """
            INSERT INTO guild_pvp_history
            (red_guild_id, blue_guild_id, winner_guild_id, start_time, end_time)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        db.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, redGuildId)
                ps.setInt(2, blueGuildId)
                if (winnerGuildId != null) {
                    ps.setInt(3, winnerGuildId)
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER)
                }
                ps.setLong(4, startTime)
                ps.setLong(5, endTime)
                ps.executeUpdate()
            }
        }
    }
}
