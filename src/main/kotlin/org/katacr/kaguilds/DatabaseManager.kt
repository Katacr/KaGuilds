package org.katacr.kaguilds

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.UUID

class DatabaseManager(val plugin: KaGuilds) {
    private var dataSource: HikariDataSource? = null

    fun setup() {
        val config = HikariConfig()
        // 获取配置中的 type 字符串，默认为 SQLite
        val dbType = plugin.config.getString("database.type", "SQLite") ?: "SQLite"

        if (dbType.equals("MySQL", ignoreCase = true)) {
            config.jdbcUrl = "jdbc:mysql://${plugin.config.getString("database.host")}:${plugin.config.getInt("database.port")}/${plugin.config.getString("database.db")}"
            config.username = plugin.config.getString("database.user")
            config.password = plugin.config.getString("database.password")
            // MySQL 建议加上这行来确保驱动正常
            config.driverClassName = "com.mysql.cj.jdbc.Driver"
        } else {
            val file = plugin.dataFolder.resolve("storage.db")
            config.jdbcUrl = "jdbc:sqlite:${file.absolutePath}"
            config.driverClassName = "org.sqlite.JDBC"
        }

        config.maximumPoolSize = 10
        // 如果是 SQLite，需要限制为 1，因为 SQLite 不支持多线程同时写入
        if (!dbType.equals("MySQL", ignoreCase = true)) config.maximumPoolSize = 1

        dataSource = HikariDataSource(config)
        createTables()
    }

    private fun createTables() {
        connection.use { conn ->
            val statement = conn.createStatement()
            // 公会信息表
            statement.execute("""
                CREATE TABLE IF NOT EXISTS guild_data (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(32) NOT NULL UNIQUE,
                    owner_uuid VARCHAR(36) NOT NULL,
                    create_time BIGINT,
                    level INT DEFAULT 1,
                    exp INT DEFAULT 0,
                    balance DOUBLE DEFAULT 0.0,
                    max_members INT DEFAULT 10,
                    announcement TEXT,
                    icon VARCHAR(64)
                )
            """.trimIndent())

            // 公会成员表 (核心优化：不直接在 data 里存列表)
            statement.execute("""
                CREATE TABLE IF NOT EXISTS guild_members (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    guild_id INTEGER,
                    role VARCHAR(16) DEFAULT 'MEMBER'
                )
            """.trimIndent())

            // 在 DatabaseManager.kt 的 createTables 中增加
            statement.execute("""
                CREATE TABLE IF NOT EXISTS guild_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    guild_id INTEGER NOT NULL,
                    request_time BIGINT NOT NULL
                )
            """.trimIndent())
        }
    }

    val connection: Connection
        get() = dataSource?.connection ?: throw IllegalStateException("数据库未初始化")
    fun close() {
        dataSource?.close()
    }
    // 根据玩家 UUID 获取公会 ID
    fun getGuildIdByPlayer(uuid: UUID): Int? {
        connection.use { conn ->
            val ps = conn.prepareStatement("SELECT guild_id FROM guild_members WHERE player_uuid = ?")
            ps.setString(1, uuid.toString())
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getInt("guild_id")
        }
        return null
    }

    // 根据公会 ID 获取公会对象
    fun getGuildById(id: Int): Guild? {
        connection.use { conn ->
            val ps = conn.prepareStatement("SELECT * FROM guild_data WHERE id = ?")
            ps.setInt(1, id)
            val rs = ps.executeQuery()
            if (rs.next()) {
                return Guild(
                    id = rs.getInt("id"),
                    name = rs.getString("name"),
                    owner = UUID.fromString(rs.getString("owner_uuid")),
                    level = rs.getInt("level"),
                    balance = rs.getDouble("balance"),
                    maxMembers = rs.getInt("max_members"),
                    announcement = rs.getString("announcement")
                )
            }
        }
        return null
    }
    fun getMemberCount(guildId: Int, existingConn: java.sql.Connection? = null): Int {
        // 如果有传入的连接，直接用；没有则申请
        val conn = existingConn ?: dataSource?.connection ?: throw IllegalStateException("数据库未初始化")

        val ps = conn.prepareStatement("SELECT COUNT(*) FROM guild_members WHERE guild_id = ?")
        ps.setInt(1, guildId)
        val rs = ps.executeQuery()
        val count = if (rs.next()) rs.getInt(1) else 0

        // 注意：只有在 conn 是新申请的情况下才关闭（即 existingConn 为空时）
        if (existingConn == null) {
            rs.close()
            ps.close()
            conn.close() // 手动关闭，或者改回使用 .use 但逻辑会变复杂
        }

        return count
    }
    /**
     * 获取指定公会的所有成员昵称列表
     */
    fun getMemberNames(guildId: Int): List<String> {
        val names = mutableListOf<String>()
        connection.use { conn ->
            val ps = conn.prepareStatement("SELECT player_uuid FROM guild_members WHERE guild_id = ?")
            ps.setInt(1, guildId)
            val rs = ps.executeQuery()

            while (rs.next()) {
                val uuidString = rs.getString("player_uuid")
                try {
                    val uuid = UUID.fromString(uuidString)
                    // 获取昵称，如果获取不到（从未上过线）则显示“未知”
                    val name = org.bukkit.Bukkit.getOfflinePlayer(uuid).name ?: "未知玩家"
                    names.add(name)
                } catch (e: Exception) {
                    // 防止 UUID 格式错误导致整个列表崩溃
                    continue
                }
            }
        }
        return names
    }
    /**
     * 获取某个公会的所有申请者 UUID 和申请时间
     */
    fun getRequests(guildId: Int): List<Pair<UUID, Long>> {
        val list = mutableListOf<Pair<UUID, Long>>()
        connection.use { conn ->
            val ps = conn.prepareStatement("SELECT player_uuid, request_time FROM guild_requests WHERE guild_id = ?")
            ps.setInt(1, guildId)
            val rs = ps.executeQuery()
            while (rs.next()) {
                list.add(UUID.fromString(rs.getString("player_uuid")) to rs.getLong("request_time"))
            }
        }
        return list
    }

    /**
     * 接受申请：这是一个“原子操作”，涉及两张表的变动
     */
    fun acceptRequest(guildId: Int, targetUuid: UUID): Boolean {
        connection.use { conn ->
            // 开启手动提交，确保两个操作要么同时成功，要么同时失败
            conn.autoCommit = false
            try {
                // 1. 加入成员表
                val psInsert = conn.prepareStatement("INSERT INTO guild_members (player_uuid, guild_id, role) VALUES (?, ?, 'MEMBER')")
                psInsert.setString(1, targetUuid.toString())
                psInsert.setInt(2, guildId)
                psInsert.executeUpdate()

                // 2. 从申请表删除
                val psDelete = conn.prepareStatement("DELETE FROM guild_requests WHERE player_uuid = ? AND guild_id = ?")
                psDelete.setString(1, targetUuid.toString())
                psDelete.setInt(2, guildId)
                psDelete.executeUpdate()

                conn.commit()
                return true
            } catch (e: Exception) {
                conn.rollback() // 出错则回滚
                e.printStackTrace()
                return false
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * 拒绝申请
     */
    fun denyRequest(guildId: Int, targetUuid: UUID) {
        connection.use { conn ->
            val ps = conn.prepareStatement("DELETE FROM guild_requests WHERE player_uuid = ? AND guild_id = ?")
            ps.setString(1, targetUuid.toString())
            ps.setInt(2, guildId)
            ps.executeUpdate()
        }
    }
    /**
     * 检查玩家在特定公会中是否拥有管理权限 (ADMIN 或 OWNER)
     */
    fun isStaff(playerUuid: UUID, guildId: Int): Boolean {
        connection.use { conn ->
            val ps = conn.prepareStatement("SELECT role FROM guild_members WHERE player_uuid = ? AND guild_id = ?")
            ps.setString(1, playerUuid.toString())
            ps.setInt(2, guildId)
            val rs = ps.executeQuery()
            if (rs.next()) {
                val role = rs.getString("role")
                return role == "OWNER" || role == "ADMIN"
            }
        }
        return false
    }

    /**
     * 获取玩家在公会中的具体角色
     */
    fun getPlayerRole(playerUuid: UUID): String? {
        connection.use { conn ->
            val ps = conn.prepareStatement("SELECT role FROM guild_members WHERE player_uuid = ?")
            ps.setString(1, playerUuid.toString())
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getString("role")
        }
        return null
    }
    /**
     * 更新成员角色
     * @return 是否更新成功
     */
    fun updateMemberRole(guildId: Int, targetUuid: UUID, newRole: String): Boolean {
        connection.use { conn ->
            val ps = conn.prepareStatement("UPDATE guild_members SET role = ? WHERE player_uuid = ? AND guild_id = ?")
            ps.setString(1, newRole)
            ps.setString(2, targetUuid.toString())
            ps.setInt(3, guildId)
            return ps.executeUpdate() > 0
        }
    }
    /**
     * 获取特定玩家在特定公会中的角色（更严谨）
     */
    fun getRoleInGuild(guildId: Int, targetUuid: UUID): String? {
        connection.use { conn ->
            val ps = conn.prepareStatement("SELECT role FROM guild_members WHERE player_uuid = ? AND guild_id = ?")
            ps.setString(1, targetUuid.toString())
            ps.setInt(2, guildId)
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getString("role")
        }
        return null
    }
    /**
     * 退出/踢出成员：从 guild_members 表删除记录
     */
    fun removeMember(uuid: UUID): Boolean {
        connection.use { conn ->
            val ps = conn.prepareStatement("DELETE FROM guild_members WHERE player_uuid = ?")
            ps.setString(1, uuid.toString())
            return ps.executeUpdate() > 0
        }
    }

    /**
     * 解散公会：彻底删除公会数据、成员名单、申请列表 (建议使用事务)
     */
    fun deleteGuild(guildId: Int): Boolean {
        connection.use { conn ->
            conn.autoCommit = false
            try {
                // 1. 删除所有申请记录
                val psReq = conn.prepareStatement("DELETE FROM guild_requests WHERE guild_id = ?")
                psReq.setInt(1, guildId)
                psReq.executeUpdate()

                // 2. 删除所有成员记录
                val psMem = conn.prepareStatement("DELETE FROM guild_members WHERE guild_id = ?")
                psMem.setInt(1, guildId)
                psMem.executeUpdate()

                // 3. 删除公会主数据
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
}