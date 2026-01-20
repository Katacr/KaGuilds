package org.katacr.kaguilds

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.entity.Player
import org.katacr.kaguilds.service.OperationResult
import java.sql.Connection
import java.sql.Statement
import java.util.UUID
import kotlin.math.ceil

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
        val isMySQL = plugin.config.getString("database.type", "sqlite").equals("mysql", true)
        val autoIncrement = if (isMySQL) "AUTO_INCREMENT" else "AUTOINCREMENT"

        connection.use { conn ->
            val statement = conn.createStatement()

            // 1. 公会主表 (修复了缺失的 exp 和 owner_name)
            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_data (
                id INTEGER PRIMARY KEY $autoIncrement,
                name VARCHAR(32) NOT NULL UNIQUE,
                owner_uuid VARCHAR(36) NOT NULL,
                owner_name VARCHAR(16),
                level INT DEFAULT 1,
                exp INT DEFAULT 0,
                balance DOUBLE DEFAULT 0.0,
                announcement TEXT,
                icon VARCHAR(32) DEFAULT 'SHIELD',
                max_members INT DEFAULT 20,
                create_time BIGINT
            )
        """)

            // 2. 成员表 (建议移除 player_uuid 的 PRIMARY KEY，改为普通索引，增加自增 ID)
            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_members (
                id INTEGER PRIMARY KEY $autoIncrement,
                guild_id INT NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16),
                role VARCHAR(16) DEFAULT 'MEMBER',
                join_time BIGINT
            )
        """)

            // 3. 申请表 (在 createTables 方法中修改)
            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_requests (
                id INTEGER PRIMARY KEY $autoIncrement,
                guild_id INT NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16), 
                request_time BIGINT
            )
        """)

            // 4. 银行日志表 (考虑到你之前提到的 bank log 功能)
            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_bank_logs (
                id INTEGER PRIMARY KEY $autoIncrement,
                guild_id INT NOT NULL,
                player_name VARCHAR(16),
                type VARCHAR(10),
                amount DOUBLE,
                time BIGINT
            )
        """)
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
    fun getGuildById(id: Int): GuildData? {
        val sql = "SELECT * FROM guild_data WHERE id = ?"
        connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        return GuildData(
                            id = rs.getInt("id"),
                            name = rs.getString("name") ?: "Unknown",
                            ownerUuid = rs.getString("owner_uuid") ?: "",
                            ownerName = rs.getString("owner_name") ?: "系统", // 使用 ?: 处理 null
                            level = rs.getInt("level"),
                            exp = rs.getInt("exp"),
                            balance = rs.getDouble("balance"),
                            announcement = rs.getString("announcement") ?: "暂无公告",
                            maxMembers = rs.getInt("max_members"),
                            createTime = rs.getLong("create_time")
                        )
                    }
                }
            }
        }
        return null
    }
    fun getMemberCount(guildId: Int, existingConn: Connection? = null): Int {
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
     * 只获取指定公会的所有成员 UUID 列表
     */
    fun getMemberUUIDs(guildId: Int): List<UUID> {
        val uuids = mutableListOf<UUID>()
        connection.use { conn ->
            val ps = conn.prepareStatement("SELECT player_uuid FROM guild_members WHERE guild_id = ?")
            ps.setInt(1, guildId)
            val rs = ps.executeQuery()
            while (rs.next()) {
                try {
                    uuids.add(UUID.fromString(rs.getString("player_uuid")))
                } catch (e: Exception) { continue }
            }
        }
        return uuids
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
    fun acceptRequest(guildId: Int, playerUuid: UUID): Boolean {
        return try {
            connection.use { conn ->
                conn.autoCommit = false // 开启事务
                try {
                    // 1. 删除申请记录
                    val del = conn.prepareStatement("DELETE FROM guild_requests WHERE guild_id = ? AND player_uuid = ?")
                    del.setInt(1, guildId)
                    del.setString(2, playerUuid.toString())
                    del.executeUpdate()

                    // 2. 添加成员记录
                    val add = conn.prepareStatement("INSERT INTO guild_members (player_uuid, guild_id, role, join_time) VALUES (?, ?, ?, ?)")
                    add.setString(1, playerUuid.toString())
                    add.setInt(2, guildId)
                    add.setString(3, "MEMBER")
                    add.setLong(4, System.currentTimeMillis())
                    add.executeUpdate()

                    conn.commit() // 提交事务
                    true
                } catch (e: Exception) {
                    conn.rollback() // 出错则回滚
                    e.printStackTrace()
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 拒绝申请
     */
    fun deleteRequest(guildId: Int, playerUuid: UUID): Boolean {
        return try {
            connection.use { conn ->
                val ps = conn.prepareStatement("DELETE FROM guild_requests WHERE guild_id = ? AND player_uuid = ?")
                ps.setInt(1, guildId)
                ps.setString(2, playerUuid.toString())
                ps.executeUpdate() > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
     * 获取玩家在公会中的角色
     */
    fun getPlayerRole(uuid: UUID): String? {
        val sql = "SELECT role FROM guild_members WHERE player_uuid = ?"
        return connection.use { conn ->
            val ps = conn.prepareStatement(sql)
            ps.setString(1, uuid.toString())
            val rs = ps.executeQuery()
            if (rs.next()) rs.getString("role") else null
        }
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
    fun removeMember(guildId: Int, playerUuid: UUID): Boolean {
        val sql = "DELETE FROM guild_members WHERE guild_id = ? AND player_uuid = ?"
        connection.use { conn ->
            val ps = conn.prepareStatement(sql)
            ps.setInt(1, guildId)
            ps.setString(2, playerUuid.toString())
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
    /**
     * 根据公会ID获取公会名称
     */
    fun getGuildName(guildId: Int): String? {
        return try {
            connection.use { conn ->
                val ps = conn.prepareStatement("SELECT name FROM guild_data WHERE id = ?")
                ps.setInt(1, guildId)
                val rs = ps.executeQuery()
                if (rs.next()) rs.getString("name") else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 添加公会成员 (增加 playerName 参数)
     */
    fun addMember(guildId: Int, playerUuid: UUID, playerName: String, role: String): Boolean {
        val sql = "INSERT INTO guild_members (guild_id, player_uuid, player_name, role) VALUES (?, ?, ?, ?)"
        connection.use { conn ->
            val ps = conn.prepareStatement(sql)
            ps.setInt(1, guildId)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, playerName)
            ps.setString(4, role)
            return ps.executeUpdate() > 0
        }
    }

    /**
     * 检查是否存在入会申请
     */
    fun hasRequest(guildId: Int, playerUuid: UUID): Boolean {
        return try {
            connection.use { conn ->
                val ps = conn.prepareStatement(
                    "SELECT 1 FROM guild_requests WHERE guild_id = ? AND player_uuid = ?"
                )
                ps.setInt(1, guildId)
                ps.setString(2, playerUuid.toString())
                val rs = ps.executeQuery()
                rs.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    /**
     * 更新公会余额
     * @param guildId 公会ID
     * @param amount 变更金额（正数为存，负数为取）
     * @return 是否操作成功
     */
    fun updateGuildBalance(guildId: Int, amount: Double): Boolean {
        return try {
            connection.use { conn ->
                val ps = conn.prepareStatement("UPDATE guild_data SET balance = balance + ? WHERE id = ?")
                ps.setDouble(1, amount)
                ps.setInt(2, guildId)
                ps.executeUpdate() > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 获取公会当前详细数据（适配最新 GuildData 模型）
     */
    fun getGuildData(guildId: Int): GuildData? {
        return try {
            connection.use { conn ->
                val ps = conn.prepareStatement("SELECT * FROM guild_data WHERE id = ?")
                ps.setInt(1, guildId)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    // 使用具名参数确保类型和顺序 100% 准确
                    return GuildData(
                        id = rs.getInt("id"),
                        name = rs.getString("name") ?: "Unknown",
                        ownerUuid = rs.getString("owner_uuid") ?: "",
                        ownerName = rs.getString("owner_name"),
                        level = rs.getInt("level"),
                        exp = rs.getInt("exp"),
                        balance = rs.getDouble("balance"),
                        announcement = rs.getString("announcement"),
                        maxMembers = rs.getInt("max_members"),
                        createTime = rs.getLong("create_time")
                    )
                }
                null
            }
        } catch (e: Exception) {
            plugin.logger.warning("查询公会数据时出错 (ID: $guildId): ${e.message}")
            e.printStackTrace()
            null
        }
    }
    /**
     * 分页获取银行日志
     * @param page 页码（从1开始）
     */
    fun getBankLogs(guildId: Int, page: Int): List<String> {
        val logs = mutableListOf<String>()
        val offset = (page - 1) * 10 // 每页10条

        try {
            connection.use { conn ->
                val ps = conn.prepareStatement(
                    "SELECT * FROM guild_bank_logs WHERE guild_id = ? ORDER BY time DESC LIMIT 10 OFFSET ?"
                )
                ps.setInt(1, guildId)
                ps.setInt(2, offset)
                val rs = ps.executeQuery()

                val dateFormat = java.text.SimpleDateFormat("MM-dd HH:mm")
                while (rs.next()) {
                    val typeStr = if (rs.getString("type") == "ADD") "§a存入" else "§c提取"
                    val time = dateFormat.format(java.util.Date(rs.getLong("time")))
                    logs.add("§8[$time] §7${rs.getString("player_name")} $typeStr §f${rs.getDouble("amount")} §7金币")
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return logs
    }

    /**
     * 获取日志总页数（用于显示当前是第几页）
     */
    fun getBankLogTotalPages(guildId: Int): Int {
        try {
            connection.use { conn ->
                val ps = conn.prepareStatement("SELECT COUNT(*) FROM guild_bank_logs WHERE guild_id = ?")
                ps.setInt(1, guildId)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    val total = rs.getInt(1)
                    return ceil(total.toDouble() / 10.0).toInt().coerceAtLeast(1)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return 1
    }
    /**
     * 记录银行操作日志
     */
    fun logBankTransaction(guildId: Int, playerName: String, type: String, amount: Double) {
        try {
            connection.use { conn ->
                val ps = conn.prepareStatement(
                    "INSERT INTO guild_bank_logs (guild_id, player_name, type, amount, time) VALUES (?, ?, ?, ?, ?)"
                )
                ps.setInt(1, guildId)
                ps.setString(2, playerName)
                ps.setString(3, type)
                ps.setDouble(4, amount)
                ps.setLong(5, System.currentTimeMillis())
                ps.executeUpdate()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    /**
     * 检查公会名称是否已存在（不区分大小写）
     */
    fun isNameExists(name: String): Boolean {
        val sql = "SELECT id FROM guild_data WHERE name = ? LIMIT 1"
        connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, name)
                ps.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }

    fun createGuild(name: String, ownerUuid: UUID, ownerName: String): Int {
        val sqlGuild = "INSERT INTO guild_data (name, owner_uuid, owner_name, level, balance, exp) VALUES (?, ?, ?, 1, 0, 0);"

        connection.use { conn ->
            conn.autoCommit = false
            try {
                // 插入公会数据
                val guildId = conn.prepareStatement(sqlGuild, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, name)
                    ps.setString(2, ownerUuid.toString())
                    ps.setString(3, ownerName)
                    ps.executeUpdate()

                    val rs = ps.generatedKeys
                    if (rs.next()) rs.getInt(1) else throw Exception("Failed to get ID")
                }
                // 插入公会成员
                val sqlMember = "INSERT INTO guild_members (guild_id, player_uuid, player_name, role) VALUES (?, ?, ?, 'OWNER')"
                conn.prepareStatement(sqlMember).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setString(2, ownerUuid.toString())
                    ps.setString(3, ownerName)
                    ps.executeUpdate()
                }
                // 提交事务
                conn.commit()
                return guildId
            } catch (e: Exception) {
                conn.rollback()
                e.printStackTrace()
                return -1
            } finally {
                conn.autoCommit = true
            }
        }
    }

    fun removeRequest(guildId: Int, playerUuid: UUID): Boolean {
        val sql = "DELETE FROM guild_requests WHERE guild_id = ? AND player_uuid = ?"
        connection.use { conn ->
            return conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, guildId)
                ps.setString(2, playerUuid.toString())
                ps.executeUpdate() > 0
            }
        }
    }
    /**
     * 申请加入公会
     */
    fun requestJoin(player: Player, guildName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查自己是否已有公会
            if (plugin.dbManager.getGuildIdByPlayer(player.uniqueId) != null) {
                callback(OperationResult.AlreadyInGuild)
                return@Runnable
            }

            // 2. 检查公会是否存在
            val guildId = plugin.dbManager.getGuildIdByName(guildName)
            if (guildId == null) {
                callback(OperationResult.Error("该公会不存在"))
                return@Runnable
            }

            // 3. 检查是否已经申请过 (通过刚才上传的 DB 代码里的 getRequests)
            val currentRequests = plugin.dbManager.getRequests(guildId)
            if (currentRequests.any { it.first == player.uniqueId }) {
                callback(OperationResult.Error("你已经申请过该公会了，请等待审核"))
                return@Runnable
            }

            // 4. 写入申请表
            if (plugin.dbManager.addRequest(guildId, player.uniqueId, player.name)) {
                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error("申请提交失败，请联系管理员"))
            }
        })
    }
    /**
     * 根据公会名称获取 ID
     */
    fun getGuildIdByName(name: String): Int? {
        val sql = "SELECT id FROM guild_data WHERE name = ?"
        connection.use { conn ->
            val ps = conn.prepareStatement(sql)
            ps.setString(1, name)
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getInt("id")
        }
        return null
    }

    /**
     * 写入申请记录
     */
    fun addRequest(guildId: Int, playerUuid: UUID, playerName: String): Boolean {
        val sql = "INSERT INTO guild_requests (guild_id, player_uuid, player_name, request_time) VALUES (?, ?, ?, ?)"
        connection.use { conn ->
            val ps = conn.prepareStatement(sql)
            ps.setInt(1, guildId)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, playerName)
            ps.setLong(4, System.currentTimeMillis())
            return ps.executeUpdate() > 0
        }
    }

    /**
     * 接受申请
     */
    fun acceptRequest(sender: Player, targetName: String, callback: (OperationResult) -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 检查操作者权限
            val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable callback(OperationResult.NotInGuild)
            val role = plugin.dbManager.getPlayerRole(sender.uniqueId)
            if (role != "OWNER" && role != "ADMIN") {
                callback(OperationResult.NoPermission)
                return@Runnable
            }

            // 2. 在申请列表中通过“名字”找到该玩家的 UUID
            val requests = plugin.dbManager.getRequests(guildId)
            val targetPair = requests.find { (uuid, _) ->
                // 匹配名字 (忽略大小写)
                plugin.server.getOfflinePlayer(uuid).name?.equals(targetName, ignoreCase = true) == true
            }

            if (targetPair == null) {
                callback(OperationResult.Error("未找到该玩家的申请记录"))
                return@Runnable
            }

            val targetUuid = targetPair.first

            // 3. 检查目标玩家是否已经加入了别的公会
            if (plugin.dbManager.getGuildIdByPlayer(targetUuid) != null) {
                plugin.dbManager.removeRequest(guildId, targetUuid) // 清理掉无效申请
                callback(OperationResult.Error("该玩家已加入其他公会"))
                return@Runnable
            }

            // 4. 执行添加成员逻辑 (使用 DBManager 已有的 addMember)
            if (plugin.dbManager.addMember(guildId, targetUuid, targetName, "MEMBER")) {
                // 5. 成功后删除申请记录
                plugin.dbManager.removeRequest(guildId, targetUuid)

                // 6. 如果目标在线，立即刷新他的公会缓存，让他能立刻用公会频道
                plugin.playerGuildCache[targetUuid] = guildId

                callback(OperationResult.Success)
            } else {
                callback(OperationResult.Error("加入失败：数据库写入错误"))
            }
        })
    }

    // 公会数据模型
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
        val createTime: Long
    )
}