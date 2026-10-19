package org.katacr.kaguilds.repository

import org.katacr.kaguilds.DatabaseManager

/**
 * 负责数据库表结构初始化和轻量迁移。
 */
class SchemaManager(private val db: DatabaseManager) {
    private val plugin = db.plugin

    fun createTables() {
        val dbType = plugin.config.getString("database.type", "sqlite") ?: "sqlite"
        val isMySQL = dbType.equals("mysql", ignoreCase = true)
        val autoIncrement = if (isMySQL) "AUTO_INCREMENT" else ""

        db.connection.use { conn ->
            val statement = conn.createStatement()

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
                icon_item_model VARCHAR(128) DEFAULT NULL,
                icon_custom_data INT DEFAULT 0,
                max_members INT DEFAULT 20,
                teleport_location TEXT DEFAULT NULL,
                create_time BIGINT,
                pvp_wins INT DEFAULT 0,
                pvp_losses INT DEFAULT 0,
                pvp_draws INT DEFAULT 0,
                pvp_total INT DEFAULT 0,
                last_interest_date BIGINT DEFAULT 0
            )
        """)

            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_members (
                id INTEGER PRIMARY KEY $autoIncrement,
                guild_id INT NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16),
                role VARCHAR(16) DEFAULT 'MEMBER',
                join_time BIGINT,
                contribution INT DEFAULT 0,
                CONSTRAINT uk_member_uuid UNIQUE (player_uuid)
            )
        """)

            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_requests (
                id INTEGER PRIMARY KEY $autoIncrement,
                guild_id INT NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16),
                request_time BIGINT,
                CONSTRAINT uk_request_guild_player UNIQUE (guild_id, player_uuid)
            )
        """)

            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_bank_logs (
                id INTEGER PRIMARY KEY $autoIncrement,
                guild_id INT NOT NULL,
                player_name VARCHAR(16),
                type VARCHAR(20),
                amount DOUBLE,
                time BIGINT
            )
        """)

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

            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_pvp_history (
                id INTEGER PRIMARY KEY $autoIncrement,
                red_guild_id INT NOT NULL,
                blue_guild_id INT NOT NULL,
                winner_guild_id INT,
                red_score INT DEFAULT 0,
                blue_score INT DEFAULT 0,
                start_time BIGINT,
                end_time BIGINT
            )
        """)

            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_task_progress (
                id INTEGER PRIMARY KEY $autoIncrement,
                guild_id INT NOT NULL,
                task_key VARCHAR(64) NOT NULL,
                player_uuid VARCHAR(36) DEFAULT NULL,
                progress INT NOT NULL DEFAULT 0,
                target INT NOT NULL DEFAULT 0,
                completed BOOLEAN NOT NULL DEFAULT 0,
                last_date VARCHAR(10) DEFAULT NULL,
                CONSTRAINT uk_guild_task_player UNIQUE(guild_id, task_key, player_uuid)
            )
        """)

            statement.execute("""
            CREATE TABLE IF NOT EXISTS guild_task_reward_claims (
                scope_type VARCHAR(8) NOT NULL,
                scope_id VARCHAR(36) NOT NULL,
                task_key VARCHAR(64) NOT NULL,
                task_date VARCHAR(10) NOT NULL,
                guild_id INT NOT NULL,
                claimed_at BIGINT NOT NULL,
                PRIMARY KEY (scope_type, scope_id, task_key, task_date)
            )
        """)

            if (isMySQL) {
                try {
                    statement.execute("ALTER TABLE guild_task_progress ENGINE = InnoDB")
                    statement.execute("ALTER TABLE guild_task_reward_claims ENGINE = InnoDB")
                } catch (e: Exception) {
                    plugin.logger.fine("设置任务表引擎时出错（可能已存在）: ${e.message}")
                }
            }

            backfillTaskRewardClaims(conn, isMySQL)

            addColumnIfMissing(statement, "guild_data", "icon_item_model VARCHAR(128) DEFAULT NULL")
            addColumnIfMissing(statement, "guild_data", "icon_custom_data INT DEFAULT 0")
            addColumnIfMissing(statement, "guild_data", "total_contribution BIGINT DEFAULT 0")

            // P1-1：为已有库补唯一约束/索引（幂等，安全可重复执行）
            migrateSchemaV1(statement)

            // 记录 schema 版本，便于后续迁移追踪
            try {
                statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INT PRIMARY KEY, applied_at BIGINT)")
                conn.prepareStatement("INSERT IGNORE INTO schema_version (version, applied_at) VALUES (?, ?)").use { ps ->
                    ps.setInt(1, 1)
                    ps.setLong(2, System.currentTimeMillis())
                    ps.executeUpdate()
                }
            } catch (e: Exception) {
                plugin.logger.warning("写入 schema_version 失败: ${e.message}")
            }
        }
    }

    /**
     * P1-1 迁移：补唯一约束与查询索引（幂等，可每次启动安全执行）。
     * 测试阶段不处理历史数据；若旧库存在重复行导致唯一索引创建失败，会打印警告，
     * 清空相关表后重启即可自动补上。
     */
    private fun migrateSchemaV1(statement: java.sql.Statement) {
        addUniqueIndexIfMissing(statement, "guild_members", "uk_member_uuid", "player_uuid")
        addUniqueIndexIfMissing(statement, "guild_requests", "uk_request_guild_player", "guild_id, player_uuid")
        addUniqueIndexIfMissing(statement, "guild_task_progress", "uk_guild_task_player", "guild_id, task_key, player_uuid")

        addIndexIfMissing(statement, "guild_bank_logs", "idx_bank_logs_guild_time", "guild_id, time")
        addIndexIfMissing(statement, "guild_pvp_history", "idx_pvp_history_guild", "red_guild_id, blue_guild_id")
        addIndexIfMissing(statement, "guild_requests", "idx_requests_guild", "guild_id")
    }

    private fun indexExists(table: String, indexName: String): Boolean {
        return try {
            db.connection.use { conn ->
                conn.metaData.getIndexInfo(null, null, table, false, false).use { rs ->
                    var found = false
                    while (rs.next()) {
                        if (indexName.equals(rs.getString("INDEX_NAME"), ignoreCase = true)) {
                            found = true
                            break
                        }
                    }
                    found
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun addUniqueIndexIfMissing(statement: java.sql.Statement, table: String, indexName: String, columns: String) {
        if (indexExists(table, indexName)) return
        try {
            statement.execute("CREATE UNIQUE INDEX $indexName ON $table ($columns)")
        } catch (e: Exception) {
            plugin.logger.warning("创建唯一索引 $indexName 失败（可能仍有重复数据）: ${e.message}")
        }
    }

    private fun addIndexIfMissing(statement: java.sql.Statement, table: String, indexName: String, columns: String) {
        if (indexExists(table, indexName)) return
        try {
            statement.execute("CREATE INDEX $indexName ON $table ($columns)")
        } catch (e: Exception) {
            plugin.logger.fine("创建索引 $indexName 跳过: ${e.message}")
        }
    }

    /**
     * 将已有完成进度登记为已发奖，避免升级后当天的任务被重复领取。
     */
    private fun backfillTaskRewardClaims(conn: java.sql.Connection, isMySQL: Boolean) {
        val insertPrefix = if (isMySQL) "INSERT IGNORE" else "INSERT OR IGNORE"
        val guildIdCast = if (isMySQL) "CAST(guild_id AS CHAR)" else "CAST(guild_id AS TEXT)"
        val sql = """
            $insertPrefix INTO guild_task_reward_claims
                (scope_type, scope_id, task_key, task_date, guild_id, claimed_at)
            SELECT
                CASE WHEN player_uuid IS NULL OR player_uuid = '' THEN 'GUILD' ELSE 'PLAYER' END,
                CASE WHEN player_uuid IS NULL OR player_uuid = '' THEN $guildIdCast ELSE player_uuid END,
                task_key,
                last_date,
                guild_id,
                ?
            FROM guild_task_progress
            WHERE completed = 1 AND last_date IS NOT NULL
        """.trimIndent()

        conn.prepareStatement(sql).use { ps ->
            ps.setLong(1, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    private fun addColumnIfMissing(statement: java.sql.Statement, tableName: String, columnDefinition: String) {
        try {
            statement.execute("ALTER TABLE $tableName ADD COLUMN $columnDefinition")
        } catch (e: Exception) {
            plugin.logger.fine("添加字段 $tableName.$columnDefinition 时出错（可能已存在）: ${e.message}")
        }
    }
}
