package org.katacr.kaguilds.repository

import org.katacr.kaguilds.DatabaseManager
import org.katacr.kaguilds.model.GuildData
import java.sql.Connection
import java.sql.ResultSet

/**
 * 公会主数据仓库，负责 guild_data 单表查询、分页和基础字段更新。
 */
class GuildRepository(private val db: DatabaseManager) {
    private val plugin = db.plugin

    fun getGuildById(id: Int): GuildData? {
        return findGuild("id = ?") { ps -> ps.setInt(1, id) }
    }

    fun getGuildData(guildId: Int): GuildData? {
        return try {
            getGuildById(guildId)
        } catch (e: Exception) {
            plugin.logger.warning("查询公会数据时出错 (ID: $guildId): ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun isNameExists(name: String): Boolean {
        return findGuildIdByName(name) != null
    }

    fun getGuildIdByName(name: String): Int {
        return findGuildIdByName(name) ?: -1
    }

    fun setGuildLocation(guildId: Int, locationStr: String?): Boolean {
        val sql = "UPDATE guild_data SET teleport_location = ? WHERE id = ?"
        return executeGuildUpdate(sql, "设置公会传送点时出错") { ps ->
            ps.setString(1, locationStr)
            ps.setInt(2, guildId)
        }
    }

    fun renameGuild(guildId: Int, newName: String): Boolean {
        val sql = "UPDATE guild_data SET name = ? WHERE id = ?"
        return executeGuildUpdate(sql, "更新公会名称时出错") { ps ->
            ps.setString(1, newName)
            ps.setInt(2, guildId)
        }
    }

    fun updateGuildIcon(guildId: Int, materialName: String, itemModel: String? = null, customData: Int? = null): Boolean {
        val sql = "UPDATE guild_data SET icon = ?, icon_item_model = ?, icon_custom_data = ? WHERE id = ?"

        return executeGuildUpdate(sql, "更新公会图标时出错") { ps ->
            ps.setString(1, materialName)
            ps.setString(2, itemModel)
            if (customData == null) {
                ps.setNull(3, java.sql.Types.INTEGER)
            } else {
                ps.setInt(3, customData)
            }
            ps.setInt(4, guildId)
        }
    }

    fun updateGuildAnnouncement(guildId: Int, content: String): Boolean {
        val sql = "UPDATE guild_data SET announcement = ? WHERE id = ?"
        return executeGuildUpdate(sql, "更新公会公告时出错") { ps ->
            ps.setString(1, content)
            ps.setInt(2, guildId)
        }
    }

    fun getGuildCount(): Int {
        val sql = "SELECT COUNT(*) FROM guild_data"
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

    fun getGuildsByPage(page: Int, size: Int): List<GuildData> {
        val guilds = mutableListOf<GuildData>()
        val sql = "SELECT * FROM guild_data ORDER BY level DESC, id ASC LIMIT ? OFFSET ?"
        val offset = page * size

        try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, size)
                    ps.setInt(2, offset)
                    val rs = ps.executeQuery()
                    while (rs.next()) {
                        guilds.add(mapGuildData(rs, conn))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return guilds
    }

    fun updateGuildLevel(guildId: Int, newLevel: Int, newMaxMembers: Int): Boolean {
        val sql = "UPDATE guild_data SET level = ?, max_members = ? WHERE id = ?"
        return executeGuildUpdate(sql, "更新公会等级时出错") { ps ->
            ps.setInt(1, newLevel)
            ps.setInt(2, newMaxMembers)
            ps.setInt(3, guildId)
        }
    }

    fun updateGuildExp(guildId: Int, amount: Int, isSet: Boolean = false): Boolean {
        val sql = if (isSet) {
            "UPDATE guild_data SET exp = ? WHERE id = ?"
        } else {
            "UPDATE guild_data SET exp = exp + ? WHERE id = ?"
        }

        return executeGuildUpdate(sql, "更新公会经验时出错") { ps ->
            ps.setInt(1, amount)
            ps.setInt(2, guildId)
        }
    }

    fun getGuildByName(name: String): GuildData? {
        return try {
            findGuild("name = ?") { ps -> ps.setString(1, name) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateLastInterestDate(guildId: Int, timestamp: Long): Boolean {
        val sql = "UPDATE guild_data SET last_interest_date = ? WHERE id = ?"
        return executeGuildUpdate(sql, "更新公会上次计息日期时出错") { ps ->
            ps.setLong(1, timestamp)
            ps.setInt(2, guildId)
        }
    }

    fun batchUpdateLastInterestDate(guildIds: List<Int>, timestamp: Long): Int {
        if (guildIds.isEmpty()) return 0

        var updatedCount = 0
        val sql = "UPDATE guild_data SET last_interest_date = ? WHERE id = ?"

        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    for (guildId in guildIds) {
                        ps.setLong(1, timestamp)
                        ps.setInt(2, guildId)
                        ps.addBatch()
                    }
                    val results = ps.executeBatch()
                    updatedCount = results.count { it > 0 }
                }
            }
            updatedCount
        } catch (e: Exception) {
            plugin.logger.warning("批量更新公会计息日期时出错: ${e.message}")
            e.printStackTrace()
            0
        }
    }

    /**
     * 查询单个公会数据，供按 ID、名称等入口复用。
     */
    private fun findGuild(whereClause: String, bind: (java.sql.PreparedStatement) -> Unit): GuildData? {
        val sql = "SELECT * FROM guild_data WHERE $whereClause LIMIT 1"
        db.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                bind(ps)
                ps.executeQuery().use { rs ->
                    if (rs.next()) return mapGuildData(rs)
                }
            }
        }
        return null
    }

    private fun findGuildIdByName(name: String): Int? {
        val sql = "SELECT id FROM guild_data WHERE name = ? LIMIT 1"
        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, name)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt("id") else null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun executeGuildUpdate(
        sql: String,
        errorContext: String,
        bind: (java.sql.PreparedStatement) -> Unit
    ): Boolean {
        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    bind(ps)
                    ps.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("$errorContext: ${e.message}")
            false
        }
    }

    /**
     * 将当前 ResultSet 行转换为 GuildData，可复用传入连接查询成员数量。
     */
    private fun mapGuildData(rs: ResultSet, conn: Connection? = null): GuildData {
        val guildId = rs.getInt("id")
        return GuildData(
            id = guildId,
            name = rs.getString("name") ?: "Unknown",
            ownerUuid = rs.getString("owner_uuid") ?: "",
            ownerName = rs.getString("owner_name"),
            level = rs.getInt("level"),
            exp = rs.getInt("exp"),
            balance = rs.getDouble("balance"),
            announcement = rs.getString("announcement"),
            maxMembers = rs.getInt("max_members"),
            teleportLocation = rs.getString("teleport_location"),
            createTime = rs.getLong("create_time"),
            memberCount = conn?.let { db.memberRepository.getMemberCount(guildId, it) } ?: 0,
            icon = rs.getString("icon") ?: "SHIELD",
            iconItemModel = try { rs.getString("icon_item_model") } catch (e: Exception) { null },
            iconCustomData = try {
                val value = rs.getInt("icon_custom_data")
                if (rs.wasNull()) null else value
            } catch (e: Exception) { null },
            pvpWins = getIntOrDefault(rs, "pvp_wins", 0),
            pvpLosses = getIntOrDefault(rs, "pvp_losses", 0),
            pvpDraws = getIntOrDefault(rs, "pvp_draws", 0),
            pvpTotal = getIntOrDefault(rs, "pvp_total", 0),
            lastInterestDate = getLongOrDefault(rs, "last_interest_date", 0)
        )
    }

    private fun getIntOrDefault(rs: ResultSet, column: String, defaultValue: Int): Int {
        return try {
            val value = rs.getInt(column)
            if (rs.wasNull()) defaultValue else value
        } catch (_: Exception) {
            defaultValue
        }
    }

    private fun getLongOrDefault(rs: ResultSet, column: String, defaultValue: Long): Long {
        return try {
            val value = rs.getLong(column)
            if (rs.wasNull()) defaultValue else value
        } catch (_: Exception) {
            defaultValue
        }
    }
}
