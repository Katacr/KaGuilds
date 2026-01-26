package org.katacr.kaguilds

import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.RegisteredServiceProvider
import org.katacr.kaguilds.listener.MenuListener
import org.katacr.kaguilds.listener.VaultListener
import org.katacr.kaguilds.service.GuildService
import java.io.File

class KaGuilds : JavaPlugin() {

    val inviteCache = mutableMapOf<UUID, Int>()
    var economy: Economy? = null
    val playerGuildCache = mutableMapOf<UUID, Int>()
    lateinit var dbManager: DatabaseManager
    lateinit var langManager: LanguageManager
    var nameRegex: Regex? = null
    lateinit var guildService: GuildService
    lateinit var menuManager: MenuManager

    /**
     * 启用插件
     */
    override fun onEnable() {
        // 0. 释放并加载配置文件
        saveDefaultConfig()
        // 1. 初始化语言管理器
        langManager = LanguageManager(this)
        langManager.load()

        // 2. 初始化菜单管理器
        setupGuiFolder()
        menuManager = MenuManager(this)

        // 3. 初始化数据库管理器
        dbManager = DatabaseManager(this)
        dbManager.setup()
        // 4. 初始化 Service 层 (传入 this 以便 Service 访问插件资源)
        guildService = GuildService(this)
        // 5. 注册指令
        getCommand("guilds")?.setExecutor(GuildCommand(this))
        // 6. 注册事件监听器
        server.pluginManager.registerEvents(VaultListener(this), this)
        server.pluginManager.registerEvents(MenuListener(this), this) // 菜单监听器

        logger.info("KaGuilds 已启用！")
        val cmd = getCommand("kaguilds")
        cmd?.setExecutor(GuildCommand(this))
        cmd?.tabCompleter = GuildCommand(this)
        if (!setupEconomy()) {
            logger.severe("未找到 Vault 经济插件！经济功能将无法使用。")
        }
        // 检查服务器是否安装了 PlaceholderAPI
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            KaGuildsExpansion(this).register()
            logger.info("已成功对接 PlaceholderAPI 变量。")
        }
        // 注册自定义插件消息通道
        this.server.messenger.registerOutgoingPluginChannel(this, "kaguilds:chat")
        this.server.messenger.registerIncomingPluginChannel(this, "kaguilds:chat", PluginMessageListener(this))
        // 存储当前服务器在线玩家的 UUID 到 公会ID 的映射
        mutableMapOf<UUID, Int>()
        server.pluginManager.registerEvents(GuildListener(this), this)
    }

    /**
     * 关闭插件
     */
    override fun onDisable() {// 使用 Kotlin 的属性初始化检查，防止在启动失败时调用报错
        if (::dbManager.isInitialized) {
            dbManager.close()
        }
        logger.info("KaGuilds 已安全关闭。")
    }
    fun reloadPlugin() {
        reloadConfig()
        langManager.load()
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
            val defaultMenus = listOf("main_menu.yml", "guilds_list.yml")
            defaultMenus.forEach { fileName ->
                val destFile = File(guiFolder, fileName)
                if (!destFile.exists()) {
                    saveResource("gui/$fileName", false)
                }
            }
        }
    }
}