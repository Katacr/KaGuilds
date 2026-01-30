package org.katacr.kaguilds

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.Statement
import java.util.UUID
import kotlin.math.ceil

class DatabaseManager(val plugin: KaGuilds) {
    var dataSource: HikariDataSource? = null
    /**
     * 初始化数据库
     */
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
    /**
     * 创建数据库表
     */
    private fun createTables() {
        val isMySQL = plugin.config.getString("database.type", "sqlite").equals("mysql", true)
        val autoIncrement = if (isMySQL) "AUTO_INCREMENT" else "AUTOINCREMENT"

        connection.use { conn ->
            val statement = conn.createStatement()

            // 1. 公会主表
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
                teleport_location TEXT DEFAULT NULL,
                create_time BIGINT
            )
        """)

            // 2. 成员表
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

            // 3. 申请表
            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_requests (
                id INTEGER PRIMARY KEY $autoIncrement,
                guild_id INT NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16), 
                request_time BIGINT
            )
        """)

            // 4. 银行日志表
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

            // 5. 公会仓库表
            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_vaults (
                guild_id INT NOT NULL,
                vault_index INT NOT NULL,
                items_data TEXT,
                last_editor VARCHAR(36) DEFAULT NULL,  
                lock_expire BIGINT DEFAULT 0,          
                PRIMARY KEY (guild_id, vault_index)
            )
        """)
        }
    }

    val connection: Connection
        get() = dataSource?.connection ?: throw IllegalStateException(plugin.langManager.get("error-database"))
    fun close() {
        dataSource?.close()
    }

    /**
     * 根据玩家 UUID 获取公会 ID
     */
    fun getGuildIdByPlayer(uuid: UUID): Int? {
        connection.use { conn ->
            val ps = conn.prepareStatement("SELECT guild_id FROM guild_members WHERE player_uuid = ?")
            ps.setString(1, uuid.toString())
            val rs = ps.executeQuery()
            if (rs.next()) return rs.getInt("guild_id")
        }
        return null
    }

    /**
     * 根据公会 ID 获取公会对象
     */
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
                            teleportLocation = rs.getString("teleport_location"), // <-- 读取新增字段
                            createTime = rs.getLong("create_time")
                        )
                    }
                }
            }
        }
        return null
    }
    /**
     * 获取指定公会的成员数量
     */
    fun getMemberCount(guildId: Int, existingConn: Connection? = null): Int {
        // 如果有传入的连接，直接用；没有则申请
        val conn = existingConn ?: dataSource?.connection ?: throw IllegalStateException(plugin.langManager.get("error-database"))

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
     * 添加公会成员 (增加 playerName 参数)
     */
    fun addMember(guildId: Int, playerUuid: UUID, playerName: String, role: String): Boolean {
        val sql = "INSERT INTO guild_members (guild_id, player_uuid, player_name, role, join_time) VALUES (?, ?, ?, ?, ?)"
        connection.use { conn ->
            val ps = conn.prepareStatement(sql)
            ps.setInt(1, guildId)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, playerName)
            ps.setString(4, role)
            ps.setLong(5, System.currentTimeMillis())
            return ps.executeUpdate() > 0
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
     * 获取公会当前详细数据
     */
    fun getGuildData(guildId: Int): GuildData? {
        return try {
            connection.use { conn ->
                val ps = conn.prepareStatement("SELECT * FROM guild_data WHERE id = ?")
                ps.setInt(1, guildId)
                val rs = ps.executeQuery()
                if (rs.next()) {
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
                        teleportLocation = rs.getString("teleport_location"), // <-- 读取新增字段
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
        val offset = (page - 1) * 10
        val sql = "SELECT * FROM guild_bank_logs WHERE guild_id = ? ORDER BY time DESC LIMIT 10 OFFSET ?"

        try {
            // 关键修复：从 dataSource 获取连接，确保不会关死物理连接
            dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setInt(2, offset)
                    val rs = ps.executeQuery()

                    val dateFormat = java.text.SimpleDateFormat(plugin.config.get("date-format") as String?)
                    while (rs.next()) {
                        val typeRaw = rs.getString("type")
                        // 兼容管理员的操作类型
                        val typeStr = when(typeRaw) {
                            "ADD" -> plugin.langManager.get("bank-text-add")  //"§a存入"
                            "REMOVE" -> plugin.langManager.get("bank-text-remove") //"§c强行扣除"
                            "SET" -> plugin.langManager.get("bank-text-set") //"§b强行重置""SET_TP" -> "§6设置传送点"
                            "SET_ICON" -> plugin.langManager.get("bank-text-seticon") //"§6修改图标"
                            "SET_MOTD" -> plugin.langManager.get("bank-text-setmotd") //"§6修改公告"
                            "RENAME" -> plugin.langManager.get("bank-text-rename") //"§6公会改名"
                            "BUY_BUFF" -> plugin.langManager.get("bank-text-buybuff") //"§6购买增益"
                            else -> plugin.langManager.get("bank-text-get") //"§c取出"
                        }
                        val time = dateFormat.format(java.util.Date(rs.getLong("time")))
                        logs.add("§7[$time] §7${rs.getString("player_name")} $typeStr §f${rs.getDouble("amount")} §7${plugin.langManager.get("balance-name")}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        // 移除 SQL 中的 reason 字段
        val sql = "INSERT INTO guild_bank_logs (guild_id, player_name, type, amount, time) VALUES (?, ?, ?, ?, ?)"
        try {
            dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setString(2, playerName)
                    ps.setString(3, type)
                    ps.setDouble(4, amount)
                    ps.setLong(5, System.currentTimeMillis())
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    /**
     * 创建公会
     * @param name 公会名称
     * @param ownerUuid 会长 UUID
     * @param ownerName 会长名称
     * @return 公会ID
     */
    fun createGuild(name: String, ownerUuid: UUID, ownerName: String): Int {
        // 1. 更新 SQL 语句，包含新增的字段
        val config = plugin.config
        val sqlGuild = """
        INSERT INTO guild_data 
        (name, owner_uuid, owner_name, level, balance, exp, icon, create_time, announcement, max_members) 
        VALUES (?, ?, ?, 1, 0, 0, ?, ?, ?, ?);
        """.trimIndent()

        return dataSource?.connection?.use { conn ->
            conn.autoCommit = false
            try {
                // 2. 插入公会基础数据
                val guildId = conn.prepareStatement(sqlGuild, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    ps.setString(1, name)
                    ps.setString(2, ownerUuid.toString())
                    ps.setString(3, ownerName)

                    // --- 新增初始化数据 ---
                    ps.setString(4, config.get("guild.icon") as String? ?: "SHIELD") // 默认图标 (material)
                    ps.setLong(5, System.currentTimeMillis())
                    ps.setString(6, config.get("guild.motd", "name" to name) as String? ?: "welcome to guilds") // 初始公告
                    ps.setInt(7, config.get("level.1.max-members", 10) as Int? ?: 10)

                    ps.executeUpdate()

                    val rs = ps.generatedKeys
                    if (rs.next()) rs.getInt(1) else throw Exception("Failed to get generated guild ID")
                }

                // 3. 插入公会成员 (会长)
                val sqlMember = "INSERT INTO guild_members (guild_id, player_uuid, player_name, role, join_time) VALUES (?, ?, ?, 'OWNER', ?)"
                conn.prepareStatement(sqlMember).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setString(2, ownerUuid.toString())
                    ps.setString(3, ownerName)
                    ps.setLong(4, System.currentTimeMillis())
                    ps.executeUpdate()
                }

                // 4. 提交事务
                conn.commit()

                // 更新缓存（如果你的插件有缓存机制）
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
    /**
     * 删除申请记录
     */
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
     * 根据公会名称获取 ID
     */
    fun getGuildIdByName(name: String): Int {
        val sql = "SELECT id FROM guild_data WHERE name = ?"
        return try {
            dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, name)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        rs.getInt("id")
                    } else {
                        -1
                    }
                }
            } ?: -1
        } catch (e: Exception) {
            -1
        }
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
     * 通过玩家名获取其 UUID
     */
    fun getUuidByPlayerName(playerName: String): UUID? {
        val sql = "SELECT player_uuid FROM guild_members WHERE player_name = ? LIMIT 1"
        dataSource?.connection?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, playerName)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    // 如果数据库里存的是 String，这里转回 UUID 对象
                    return UUID.fromString(rs.getString("player_uuid"))
                }
            }
        }
        return null
    }

    /**
     * 设置公会传送点
     */
    fun setGuildLocation(guildId: Int, locationStr: String?): Boolean {
        // 确保这里也是 guild_data
        val sql = "UPDATE guild_data SET teleport_location = ? WHERE id = ?"
        return dataSource?.connection?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, locationStr)
                ps.setInt(2, guildId)
                ps.executeUpdate() > 0
            }
        } ?: false
    }

    /**
     * 更新公会名称
     */
    fun renameGuild(guildId: Int, newName: String): Boolean {
        val sql = "UPDATE guild_data SET name = ? WHERE id = ?"
        // 关键：从 dataSource 获取连接，而不是直接用单例 connection
        return try {
            dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, newName)
                    ps.setInt(2, guildId)
                    ps.executeUpdate() > 0
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 转让公会所有权
     */
    fun transferGuildOwnership(guildId: Int, oldOwnerUuid: UUID, newOwnerUuid: UUID, newOwnerName: String): Boolean {
        // 同时更新 UUID 和 Name
        val updateOwner = "UPDATE guild_data SET owner_uuid = ?, owner_name = ? WHERE id = ?"
        val updateRole = "UPDATE guild_members SET role = ? WHERE player_uuid = ?"

        return try {
            dataSource?.connection?.use { conn ->
                conn.autoCommit = false
                try {
                    // 1. 更新公会主表的所有者信息
                    conn.prepareStatement(updateOwner).use { ps ->
                        ps.setString(1, newOwnerUuid.toString())
                        ps.setString(2, newOwnerName)
                        ps.setInt(3, guildId)
                        ps.executeUpdate()
                    }

                    // 2. 将新会长职位设为 OWNER
                    conn.prepareStatement(updateRole).use { ps ->
                        ps.setString(1, "OWNER")
                        ps.setString(2, newOwnerUuid.toString())
                        ps.executeUpdate()
                    }

                    // 3. 将原会长职位降为 MEMBER
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

    /**
     * 根据玩家名获取 UUID (从数据库或 Bukkit 获取)
     */
    fun getPlayerUuidByName(playerName: String): UUID? {
        // 优先尝试从在线玩家获取
        val onlinePlayer = plugin.server.getPlayer(playerName)
        if (onlinePlayer != null) return onlinePlayer.uniqueId

        // 如果玩家不在线，从数据库的 guild_members 表中查询 (假设你存了名字)
        // 或者通过 Bukkit.getOfflinePlayer (注意：这在某些版本上可能是耗时操作)
        return try {
            dataSource?.connection?.use { conn ->
                val sql = "SELECT player_uuid FROM guild_members WHERE player_name = ? LIMIT 1"
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerName)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        UUID.fromString(rs.getString("player_uuid"))
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveVault(guildId: Int, index: Int, data: String): Boolean {
        // REPLACE INTO 是 MySQL 和 SQLite 通用的原子操作：有则覆盖，无则插入
        // 显式将 lock_expire 设为 0，确保保存后锁是释放状态（或者保持为0）
        val sql = "REPLACE INTO guild_vaults (guild_id, vault_index, items_data, lock_expire) VALUES (?, ?, ?, 0)"

        if (dataSource == null) return false

        return try {
            dataSource!!.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setInt(2, index)
                    ps.setString(3, data)
                    // 执行成功会返回 1 (插入) 或 2 (MySQL 替换)
                    ps.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("无法保存公会 $guildId 的仓库 $index: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    /**
     * 从数据库获取仓库内容
     * @return 返回 Base64 字符串，如果数据库中没有记录则返回 null
     */
    fun getVaultData(guildId: Int, index: Int): String? {
        val sql = "SELECT items_data FROM guild_vaults WHERE guild_id = ? AND vault_index = ?"

        try {
            return dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setInt(2, index)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        rs.getString("items_data")
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe(
                plugin.langManager.get(
                    "error-load-vault",
                    "id" to guildId.toString(),
                    "index" to index.toString(),
                    "message" to e.message.toString()
                )
            )
            e.printStackTrace()
            return null
        }
    }

    /**
     * 尝试抢占仓库锁 (支持跨服)
     * @return true 代表抢占成功，false 代表已被他人锁定
     */
    fun tryGrabLock(guildId: Int, index: Int, playerUuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        val expireAt = now + 30000 // 30秒租约

        return try {
            dataSource?.connection?.use { conn ->
                // 判断当前是 MySQL 还是 SQLite
                val isMySQL = dataSource?.jdbcUrl?.contains("mysql", ignoreCase = true) == true

                // SQLite 使用 "INSERT OR IGNORE"，MySQL 使用 "INSERT IGNORE"
                val initSql = if (isMySQL) {
                    "INSERT IGNORE INTO guild_vaults (guild_id, vault_index, lock_expire) VALUES (?, ?, 0)"
                } else {
                    "INSERT OR IGNORE INTO guild_vaults (guild_id, vault_index, lock_expire) VALUES (?, ?, 0)"
                }

                conn.prepareStatement(initSql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setInt(2, index)
                    ps.executeUpdate()
                }

                // 步骤 2: 执行原子抢锁 (这部分 SQL 是通用的)
                val updateSql = """
                UPDATE guild_vaults 
                SET last_editor = ?, lock_expire = ? 
                WHERE guild_id = ? AND vault_index = ? 
                AND (lock_expire < ? OR last_editor = ?)
            """.trimIndent()

                conn.prepareStatement(updateSql).use { ps ->
                    ps.setString(1, playerUuid.toString())
                    ps.setLong(2, expireAt)
                    ps.setInt(3, guildId)
                    ps.setInt(4, index)
                    ps.setLong(5, now) // 当前时间
                    ps.setString(6, playerUuid.toString())

                    val affected = ps.executeUpdate()
                    affected > 0 // 如果更新成功，代表抢锁成功
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 释放锁
     */
    fun releaseLock(guildId: Int, index: Int, playerUuid: UUID) {
        // 增加 WHERE 条件：只有当当前锁定者确实是该玩家时，才允许释放
        // 这防止了“锁已过期并被他人接管”的情况下，原持有者误删新持有者的锁
        val sql = "UPDATE guild_vaults SET lock_expire = 0 WHERE guild_id = ? AND vault_index = ? AND last_editor = ?"

        try {
            dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setInt(2, index)
                    ps.setString(3, playerUuid.toString())

                    val affected = ps.executeUpdate()
                    if (affected == 0) {
                        // 说明锁可能已经过期并被别人抢走了，或者已经释放了
                        // 这种情况通常不需要报错，但也证明了 WHERE 检查的必要性
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("释放仓库锁时发生数据库异常: ${e.message}")
            e.printStackTrace()
        }
    }
    /**
     * 检查仓库是否被锁定
     * @return true 代表仓库被锁定
     */
    fun isVaultLocked(guildId: Int, vaultIndex: Int): Boolean {
        val sql = "SELECT lock_expire FROM guild_vaults WHERE guild_id = ? AND vault_index = ?"
        return dataSource?.connection?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, guildId)
                ps.setInt(2, vaultIndex)
                val rs = ps.executeQuery()
                rs.next() && rs.getLong("lock_expire") > System.currentTimeMillis()
            }
        } ?: false
    }

    /**
     * 更新公会图标
     */
    fun updateGuildIcon(guildId: Int, materialName: String): Boolean {
        val sql = "UPDATE guild_data SET icon = ? WHERE id = ?"
        return dataSource?.connection?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, materialName)
                ps.setInt(2, guildId)
                ps.executeUpdate() > 0
            }
        } ?: false
    }

    /**
     * 更新公会公告 (MOTD)
     */
    fun updateGuildAnnouncement(guildId: Int, content: String): Boolean {
        val sql = "UPDATE guild_data SET announcement = ? WHERE id = ?"
        return dataSource?.connection?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, content)
                ps.setInt(2, guildId)
                ps.executeUpdate() > 0
            }
        } ?: false
    }

    /**
     * 获取公会总数
     */
    fun getGuildCount(): Int {
        val sql = "SELECT COUNT(*) FROM guild_data" // 确保表名是 guild_data
        return try {
            connection.use { conn ->
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

    /**
     * 分页获取公会数据列表
     * @param page 当前页码 (从 0 开始)
     * @param size 每页显示数量
     */
    fun getGuildsByPage(page: Int, size: Int): List<GuildData> {
        val guilds = mutableListOf<GuildData>()
        // 保持与 getAllGuilds 一致的排序逻辑：按等级倒序，ID 正序
        val sql = "SELECT * FROM guild_data ORDER BY level DESC, id ASC LIMIT ? OFFSET ?"
        val offset = page * size

        try {
            connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, size)
                    ps.setInt(2, offset)
                    val rs = ps.executeQuery()
                    while (rs.next()) {
                        // 调用下方的私有映射方法
                        guilds.add(mapGuildData(rs, conn))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return guilds
    }
    /**
     * 内部辅助方法：将 ResultSet 的当前行转换为 GuildData 对象
     * 修复：通过传入 conn 确保在循环调用中复用连接，提高性能
     */
    private fun mapGuildData(rs: java.sql.ResultSet, conn: Connection): GuildData {
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
            // 复用传入的连接来查询成员数量
            memberCount = getMemberCount(guildId, conn),
            icon = rs.getString("icon") ?: "SHIELD"
        )
    }

    /**
     * 获取公会所有成员的详细数据
     */
    fun getGuildMembers(guildId: Int): List<MemberData> {
        val members = mutableListOf<MemberData>()
        val sql = "SELECT * FROM guild_members WHERE guild_id = ?"
        try {
            connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    val rs = ps.executeQuery()
                    while (rs.next()) {
                        members.add(MemberData(
                            uuid = UUID.fromString(rs.getString("player_uuid")),
                            name = rs.getString("player_name"),
                            role = rs.getString("role") ?: "MEMBER",
                            joinTime = rs.getLong("join_time")
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return members
    }
    // 公会数据模型
    data class GuildData(
        val id: Int,
        val name: String,
        val ownerUuid: String, // 或者是 UUID 类型，取决于你之前的定义
        val ownerName: String?,
        val level: Int,
        val exp: Int,
        val balance: Double,
        val announcement: String?,
        val maxMembers: Int,
        val teleportLocation: String?, // <-- 新增字段
        val createTime: Long,
        val memberCount: Int = 0,
        val icon: String? = null
    )

    // 成员数据模型
    data class MemberData(
        val uuid: UUID,
        val name: String?,
        val role: String,
        val joinTime: Long
    )
}