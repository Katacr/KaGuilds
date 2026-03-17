package org.katacr.kaguilds

import net.byteflux.libby.BukkitLibraryManager
import net.byteflux.libby.Library
import net.milkbowl.vault.economy.Economy
import org.bstats.bukkit.Metrics
import org.bstats.charts.SingleLineChart
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaguilds.arena.ArenaManager
import org.katacr.kaguilds.arena.PvPManager
import org.katacr.kaguilds.listener.*
import org.katacr.kaguilds.service.GuildService
import org.katacr.kaguilds.service.TaskManager
import java.io.File
import java.util.*

class KaGuilds : JavaPlugin() {

    val inviteCache = mutableMapOf<UUID, Int>()
    var economy: Economy? = null
    val playerGuildCache = mutableMapOf<UUID, Int>()
    // 跨服在线玩家缓存：Map<玩家名, 所在服务器ID>
    val crossServerOnlinePlayers = mutableMapOf<String, String>()
    // 公会聊天模式玩家集合
    val guildChatPlayers = mutableSetOf<UUID>()
    lateinit var dbManager: DatabaseManager
    lateinit var langManager: LanguageManager
    var nameRegex: Regex? = null
    lateinit var guildService: GuildService
    lateinit var taskListener: TaskListener
    lateinit var menuListener: MenuListener
    lateinit var taskManager: TaskManager
    lateinit var menuManager: MenuManager
    lateinit var arenaManager: ArenaManager
    lateinit var pvpManager: PvPManager
    lateinit var buffsConfig: FileConfiguration
    lateinit var levelsConfig: FileConfiguration
    lateinit var tasksConfig: FileConfiguration
    val guiMenuFiles = mutableListOf<String>()

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
        loadGuiMenus()
        loadBuffsConfig()
        loadLevelsConfig()
        loadTasksConfig()
        loadEnglishConfig()
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
            logger.severe("错误类型: ${e.javaClass.simpleName}")
            logger.severe("错误信息: ${e.message}")
            logger.severe("详细堆栈:")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
            return
        }

        // 4. 服务层与指令注册
        guildService = GuildService(this)
        taskManager = TaskManager(this)
        taskManager.initialize()
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
        menuListener = MenuListener(this)
        pm.registerEvents(VaultListener(this), this)
        pm.registerEvents(menuListener, this)
        pm.registerEvents(NotifyListener(this), this)
        pm.registerEvents(PvPListener(this), this)
        pm.registerEvents(GuildListener(this), this)
        taskListener = TaskListener(this)
        pm.registerEvents(taskListener, this)
    }

    /**
     * 关闭插件
     */
    override fun onDisable() {
        // 1. 停止任务管理器的定时器
        if (::taskManager.isInitialized) {
            taskManager.stopMidnightCheck()
            taskManager.stopTaskResetCheck()
        }

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

        // 跨服模式：服务器关闭时清空在线玩家缓存
        if (config.getBoolean("proxy", false)) {
            crossServerOnlinePlayers.clear()
        }
        // 清空公会聊天模式玩家集合
        guildChatPlayers.clear()
    }

    /**
     * 重载插件
     */
    fun reloadPlugin(sender: CommandSender) {
        reloadConfig()
        loadGuiMenus()
        loadBuffsConfig()
        loadLevelsConfig()
        loadTasksConfig()
        loadEnglishConfig()
        taskManager.reload()
        langManager.load()
        menuManager.reload()
        sender.sendMessage(langManager.get("reload-success"))
    }

    /**
     * 加载 英文版 配置文件
     */
    private fun loadEnglishConfig() {
        val englishFile = File(dataFolder, "config_en.yml")
        if (!englishFile.exists()) {
            saveResource("config_en.yml", false)
        }
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
     * 加载 Tasks 配置文件
     */
    private fun loadTasksConfig() {
        val tasksFile = File(dataFolder, "task.yml")
        if (!tasksFile.exists()) {
            saveResource("task.yml", false)
        }
        tasksConfig = YamlConfiguration.loadConfiguration(tasksFile)
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

        // 如果文件夹不存在或为空，则创建并释放所有默认 GUI 文件（默认使用英文版）
        if (!guiFolder.exists() || guiFolder.listFiles()?.isEmpty() == true) {
            guiFolder.mkdirs()

            try {
                // 获取 JAR 包中 gui_EN 文件夹下的所有资源
                val resourcePath = "gui_EN/"
                val urls = javaClass.classLoader.getResources(resourcePath)

                while (urls.hasMoreElements()) {
                    val url = urls.nextElement()
                    val protocol = url.protocol

                    if (protocol == "jar") {
                        // JAR 包中的资源
                        val jarPath = url.path.substring(5, url.path.indexOf("!"))
                        val jarFile = java.util.jar.JarFile(jarPath)

                        val entries = jarFile.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.name

                            // 只处理 gui_EN 文件夹下的 .yml 文件
                            if (name.startsWith(resourcePath) && name.endsWith(".yml") && !entry.isDirectory) {
                                val fileName = name.substring(resourcePath.length)
                                val destFile = File(guiFolder, fileName)

                                // 只在目标文件不存在时才释放
                                if (!destFile.exists()) {
                                    try {
                                        // 从 JAR 中读取资源内容并写入到 gui 文件夹
                                        jarFile.getInputStream(entry).use { inputStream ->
                                            destFile.outputStream().use { outputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        logger.warning("Failed to extract file '$fileName': ${e.message}")
                                    }
                                }
                            }
                        }
                        jarFile.close()
                    } else if (protocol == "file") {
                        // 开发环境：从文件系统读取
                        val guiResourceFolder = File(url.toURI())
                        if (guiResourceFolder.exists() && guiResourceFolder.isDirectory) {
                            guiResourceFolder.listFiles()?.filter { it.isFile && it.extension == "yml" }?.forEach { file ->
                                val destFile = File(guiFolder, file.name)
                                if (!destFile.exists()) {
                                    try {
                                        // 复制文件到 gui 文件夹
                                        file.copyTo(destFile, overwrite = false)
                                    } catch (e: Exception) {
                                        logger.warning("Failed to extract file '${file.name}': ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warning("Failed to extract GUI files: ${e.message}")
            }
        }
    }

    /**
     * 加载 GUI 菜单文件列表
     */
    private fun loadGuiMenus() {
        guiMenuFiles.clear()
        val guiFolder = File(dataFolder, "gui")
        if (guiFolder.exists() && guiFolder.isDirectory) {
            guiFolder.listFiles()?.filter { it.isFile && it.extension == "yml" }?.forEach { file ->
                // 去除 .yml 扩展名，只保留文件名部分
                val menuName = file.name.substring(0, file.name.length - 4)
                guiMenuFiles.add(menuName)
            }
        }
    }

    /**
     * 释放 GUI 文件（管理员命令使用）
     * @param langType 语言类型 (CN 或 EN)
     * @return 释放结果（成功状态、文件数量、错误信息）
     */
    fun releaseGuiFiles(langType: String): ReleaseResult {
        val sourceFolder = if (langType == "CN") "gui_CN" else "gui_EN"
        val guiFolder = File(dataFolder, "gui")

        // 确保 gui 文件夹存在
        if (!guiFolder.exists()) {
            guiFolder.mkdirs()
        }

        var successCount = 0
        var error: String? = null

        try {
            val resourcePath = "$sourceFolder/"
            val urls = javaClass.classLoader.getResources(resourcePath)

            if (!urls.hasMoreElements()) {
                error = "Source folder '$sourceFolder' not found in plugin resources"
                return ReleaseResult(false, 0, error)
            }

            while (urls.hasMoreElements()) {
                val url = urls.nextElement()
                val protocol = url.protocol

                if (protocol == "jar") {
                    // JAR 包中的资源
                    val jarPath = url.path.substring(5, url.path.indexOf("!"))
                    val jarFile = java.util.jar.JarFile(jarPath)

                    val entries = jarFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name

                        // 只处理指定语言文件夹下的 .yml 文件
                        if (name.startsWith(resourcePath) && name.endsWith(".yml") && !entry.isDirectory) {
                            val fileName = name.substring(resourcePath.length)
                            val destFile = File(guiFolder, fileName)

                            try {
                                // 从 JAR 中读取资源内容
                                jarFile.getInputStream(entry).use { inputStream ->
                                    destFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                successCount++
                                logger.info("Released menu file: $fileName")
                            } catch (e: Exception) {
                                logger.warning("Failed to release file '$fileName': ${e.message}")
                            }
                        }
                    }
                    jarFile.close()
                } else if (protocol == "file") {
                    // 开发环境：从文件系统读取
                    val guiResourceFolder = File(url.toURI())
                    if (guiResourceFolder.exists() && guiResourceFolder.isDirectory) {
                        guiResourceFolder.listFiles()?.filter { it.isFile && it.extension == "yml" }?.forEach { file ->
                            val destFile = File(guiFolder, file.name)

                            try {
                                // 复制文件，覆盖已有文件
                                file.copyTo(destFile, overwrite = true)
                                successCount++
                                logger.info("Released menu file: ${file.name}")
                            } catch (e: Exception) {
                                logger.warning("Failed to release file '${file.name}': ${e.message}")
                            }
                        }
                    }
                }
            }

            // 重新加载菜单文件
            loadGuiMenus()

        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
            logger.severe("Failed to release GUI files: $error")
            e.printStackTrace()
        }

        return ReleaseResult(successCount > 0, successCount, error)
    }

    /**
     * 释放结果数据类
     */
    data class ReleaseResult(
        val success: Boolean,
        val count: Int,
        val error: String?
    )

    /**
     * 打印LOGO
     */
    private fun sendStartupMessage() {
        val console = server.consoleSender
        val version = description.version
        // 获取服务器版本
        val gameVersion = server.version.split("MC: ")[1].removeSuffix(")")

        // 动态判断各种状态
        val vaultStatus = setupEconomy()
        val papiStatus = server.pluginManager.getPlugin("PlaceholderAPI") != null
        val dbType = config.getString("database.type", "SQLite") ?: "SQLite"

        // 统计加载的资源数量
        val levelsCount = levelsConfig.getConfigurationSection("levels")?.getKeys(false)?.size ?: 0
        val buffCount = buffsConfig.getConfigurationSection("buffs")?.getKeys(false)?.size ?: 0
        val dailyTasksCount = taskManager.taskDefinitions.values.count { it.type == "daily" }
        val globalTasksCount = taskManager.taskDefinitions.values.count { it.type == "global" }
        val allTasksCount = dailyTasksCount + globalTasksCount
        val guiMenuCount = guiMenuFiles.size

        // 统计公会和成员数量
        val guildCount = if (::dbManager.isInitialized) dbManager.getGuildCount() else 0
        val memberCount = if (::dbManager.isInitialized) dbManager.getTotalMemberCount() else 0

        // 准备国际化文本
        val vaultText = langManager.get("info-logo-hook-true").takeIf { vaultStatus } ?: langManager.get("info-logo-hook-false")
        val papiText = langManager.get("info-logo-hook-true").takeIf { papiStatus } ?: langManager.get("info-logo-hook-false")

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
            §7${langManager.get("info-logo-version", "version" to version)}
            §7${langManager.get("info-logo-minecraft", "version" to gameVersion)}
            §7${langManager.get("info-logo-database", "type" to dbType)}
            §7${langManager.get("info-logo-vault", "status" to vaultText)}
            §7${langManager.get("info-logo-placeholderapi", "status" to papiText)}
            §7${langManager.get("info-logo-level", "amount" to levelsCount.toString())}
            §7${langManager.get("info-logo-buff", "amount" to buffCount.toString())}
            §7${langManager.get("info-logo-task", "all_task" to allTasksCount.toString(), "global" to globalTasksCount.toString(), "daily" to dailyTasksCount.toString())}
            §7${langManager.get("info-logo-gui-menu", "amount" to guiMenuCount.toString())}
            §7${langManager.get("info-logo-guild-count", "amount" to guildCount.toString())}
            §7${langManager.get("info-logo-member-count", "amount" to memberCount.toString())}
            §b________________________________________________________
        """.trimIndent()

        // 考虑到有些控制台不支持一次性发送多行，我们按行拆分发送
        logo.split("\n").forEach { line ->
            console.sendMessage(line)
        }
    }
}