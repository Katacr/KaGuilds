package org.katacr.kaguilds

import net.byteflux.libby.BukkitLibraryManager
import net.byteflux.libby.Library
import net.milkbowl.vault.economy.Economy
import org.bstats.bukkit.Metrics
import org.bstats.charts.SingleLineChart
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaguilds.arena.ArenaManager
import org.katacr.kaguilds.arena.PvPManager
import org.katacr.kaguilds.listener.*
import org.katacr.kaguilds.service.GuildService
import java.io.File
import java.util.*

class KaGuilds : JavaPlugin() {

    val inviteCache = mutableMapOf<UUID, Int>()
    var economy: Economy? = null
    val playerGuildCache = mutableMapOf<UUID, Int>()
    lateinit var dbManager: DatabaseManager
    lateinit var langManager: LanguageManager
    var nameRegex: Regex? = null
    lateinit var guildService: GuildService
    lateinit var menuManager: MenuManager
    lateinit var arenaManager: ArenaManager
    lateinit var pvpManager: PvPManager
    lateinit var buffsConfig: FileConfiguration
    lateinit var levelsConfig: FileConfiguration

    /**
     * 在插件加载时优先处理依赖下载
     */
    override fun onLoad() {
        val libraryManager = BukkitLibraryManager(this)

        // 添加 Maven 中央仓库和阿里云镜像（加速国内下载）
        libraryManager.addMavenCentral()
        libraryManager.addRepository("https://maven.aliyun.com/repository/public")

        // 1. Kotlin 标准库
        val kotlinStd = Library.builder()
            .groupId("org{}jetbrains{}kotlin")
            .artifactId("kotlin-stdlib")
            .version("1.9.22")
            .build()

        // 2. HikariCP 连接池
        val hikari = Library.builder()
            .groupId("com{}zaxxer")
            .artifactId("HikariCP")
            .version("5.1.0")
            .build()

        // 3. SQLite JDBC 驱动
        val sqlite = Library.builder()
            .groupId("org{}xerial")
            .artifactId("sqlite-jdbc")
            .version("3.45.1.0")
            .build()

        logger.info("Checking and downloading necessary dependent libraries, please wait...")

        libraryManager.loadLibrary(kotlinStd)
        libraryManager.loadLibrary(hikari)
        libraryManager.loadLibrary(sqlite)
    }

    /**
     * 启用插件
     */
    override fun onEnable() {
        // 1. 基础配置与语言
        saveDefaultConfig()
        langManager = LanguageManager(this)
        langManager.load()

        // 2. 模块初始化
        setupGuiFolder()
        loadBuffsConfig()
        loadLevelsConfig()
        menuManager = MenuManager(this)
        arenaManager = ArenaManager(this)
        pvpManager = PvPManager(this)
        arenaManager.loadKit()

        // 3. 数据库初始化
        try {
            dbManager = DatabaseManager(this)
            dbManager.setup()
        } catch (e: Exception) {
            logger.severe("数据库连接失败! 插件将无法正常工作。")
            server.pluginManager.disablePlugin(this)
            return
        }

        // 4. 服务层与指令注册
        guildService = GuildService(this)
        setupCommands()
        setupListeners()

        // 5. 外部插件对接
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            KaGuildsExpansion(this).register()
        }

        // 6. 统计与消息通道
        val metrics = Metrics(this, 29368)
        metrics.addCustomChart(SingleLineChart("guilds_total") {
            dbManager.getGuildCount()
        })

        server.messenger.registerOutgoingPluginChannel(this, "kaguilds:chat")
        server.messenger.registerIncomingPluginChannel(this, "kaguilds:chat", PluginMessageListener(this))

        // 7. 打印Logo
        sendStartupMessage()
    }

    // 指令注册逻辑
    private fun setupCommands() {
        val cmd = getCommand("kaguilds")
        val executor = GuildCommand(this)
        cmd?.setExecutor(executor)
        cmd?.tabCompleter = executor
        // 兼容别名
        getCommand("guilds")?.setExecutor(executor)
    }

    // 监听器注册逻辑
    private fun setupListeners() {
        val pm = server.pluginManager
        pm.registerEvents(VaultListener(this), this)
        pm.registerEvents(MenuListener(this), this)
        pm.registerEvents(NotifyListener(this), this)
        pm.registerEvents(PvPListener(this), this)
        pm.registerEvents(GuildListener(this), this)
    }

    /**
     * 关闭插件
     */
    override fun onDisable() {
        if (::pvpManager.isInitialized) {
            pvpManager.currentMatch?.let { match ->
                // 如果战斗还没正式开始，强制执行退款
                if (!match.isStarted) {
                    val fee = config.getDouble("balance.pvp", 300.0)
                    if (fee > 0 && ::dbManager.isInitialized) {
                        dbManager.updateGuildBalance(match.redGuildId, fee)
                        logger.info("服务器关闭：已自动为公会 ${match.redGuildId} 退还公会战挑战金。")
                    }
                }
                // 移除 BossBar
                pvpManager.removeBossBar()
            }
        }

        // 2. 关闭数据库连接
        if (::dbManager.isInitialized) {
            dbManager.close()
        }

        logger.info("KaGuilds 已安全关闭。")
    }

    /**
     * 重载插件
     */
    fun reloadPlugin() {
        reloadConfig()
        loadBuffsConfig()
        loadLevelsConfig()
        langManager.load()
        menuManager.reload()
    }

    /**
     * 加载 Buffs 配置文件
     */
    private fun loadBuffsConfig() {
        val buffsFile = File(dataFolder, "buffs.yml")
        if (!buffsFile.exists()) {
            saveResource("buffs.yml", false)
        }
        buffsConfig = YamlConfiguration.loadConfiguration(buffsFile)
    }

    /**
     * 加载 Levels 配置文件
     */
    private fun loadLevelsConfig() {
        val levelsFile = File(dataFolder, "levels.yml")
        if (!levelsFile.exists()) {
            saveResource("levels.yml", false)
        }
        levelsConfig = YamlConfiguration.loadConfiguration(levelsFile)
    }

    /**
     * 加载公会名称正则表达式
     */
    fun loadRegex() {
        val pattern = config.getString("guild.name.regex", "^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")
        nameRegex = try {
            pattern?.let { Regex(it) }
        } catch (e: Exception) {
            logger.warning("配置文件中的正则表达式格式有误，将使用默认规则！")
            Regex("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")
        }
    }

    /**
     * 设置经济系统
     */
    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) return false
        val rsp: RegisteredServiceProvider<Economy> = server.servicesManager.getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return true
    }

    /**
     * 释放 GUI 文件夹
     */
    private fun setupGuiFolder() {
        val guiFolder = File(dataFolder, "gui")
        if (!guiFolder.exists() || guiFolder.listFiles()?.isEmpty() == true) {
            guiFolder.mkdirs()
            val defaultMenus = listOf("main_menu.yml", "guilds_list.yml", "guild_members.yml", "guild_buffs.yml", "guild_vaults.yml", "guild_bank.yml","guild_upgrade.yml")
            defaultMenus.forEach { fileName ->
                val destFile = File(guiFolder, fileName)
                if (!destFile.exists()) {
                    saveResource("gui/$fileName", false)
                }
            }
        }
    }

    /**
     * 打印LOGO
     */
    private fun sendStartupMessage() {
        val console = server.consoleSender
        val version = description.version
        // 获取服务器版本
        val gameVersion = server.version.split("MC: ")[1].removeSuffix(")")

        // 动态判断各种状态
        val vaultStatus = if (setupEconomy()) "§aHooked" else "§cNot found"
        val papiStatus = if (server.pluginManager.getPlugin("PlaceholderAPI") != null) "§aHooked" else "§cNot found"
        val dbType = config.getString("database.type", "SQLite") ?: "SQLite"
        // 使用三引号避免转义字符导致的对齐问题
        val logo = """
            §b________________________________________________________
            §b
            §b  _  __      §3  ____         _     _         §b
            §b | |/ / ____ §3 / ___|_   _(_) | __| | ___    §b
            §b | ' / |    |§3| |  _| | | | | |/ _` |/ __/   §b
            §b | . \ | [] |§3| |_| | |_| | | | (_| |__  \   §b
            §b |_|\_\|_,\_\§3 \____|\__,_|_|_|\__,_|____/   §b
            §b
            §7    Version: §e$version
            §7    Minecraft: §b$gameVersion
            §7    Database: §6$dbType
            §7    Vault: $vaultStatus
            §7    PlaceholderAPI: $papiStatus
            §b________________________________________________________
        """.trimIndent()

        // 考虑到有些控制台不支持一次性发送多行，我们按行拆分发送
        logo.split("\n").forEach { line ->
            console.sendMessage(line)
        }
    }
}