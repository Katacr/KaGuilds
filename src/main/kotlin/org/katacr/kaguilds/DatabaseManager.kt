package org.katacr.kaguilds

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.katacr.kaguilds.repository.BankRepository
import org.katacr.kaguilds.repository.GuildLifecycleRepository
import org.katacr.kaguilds.repository.GuildRepository
import org.katacr.kaguilds.repository.MemberRepository
import org.katacr.kaguilds.repository.PvPRepository
import org.katacr.kaguilds.repository.RequestRepository
import org.katacr.kaguilds.repository.SchemaManager
import org.katacr.kaguilds.repository.TaskRepository
import org.katacr.kaguilds.repository.VaultRepository
import java.sql.Connection

class DatabaseManager(val plugin: KaGuilds) {
    var dataSource: HikariDataSource? = null
    val bankRepository = BankRepository(this)
    val guildLifecycleRepository = GuildLifecycleRepository(this)
    val guildRepository = GuildRepository(this)
    val memberRepository = MemberRepository(this)
    val pvpRepository = PvPRepository(this)
    val requestRepository = RequestRepository(this)
    val schemaManager = SchemaManager(this)
    val taskRepository = TaskRepository(this)
    val vaultRepository = VaultRepository(this)
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
        schemaManager.createTables()
    }

    val connection: Connection
        get() = dataSource?.connection ?: throw IllegalStateException(plugin.langManager.get("error-database"))
    fun close() {
        dataSource?.close()
    }

}
