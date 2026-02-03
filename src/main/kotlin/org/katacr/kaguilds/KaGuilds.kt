package org.katacr.kaguilds

import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.RegisteredServiceProvider
import org.katacr.kaguilds.arena.ArenaManager
import org.katacr.kaguilds.arena.PvPManager
import org.katacr.kaguilds.listener.GuildListener
import org.katacr.kaguilds.listener.MenuListener
import org.katacr.kaguilds.listener.NotifyListener
import org.katacr.kaguilds.listener.PvPListener
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
    lateinit var arenaManager: ArenaManager
    lateinit var pvpManager: PvPManager

    /**
     * 启用插件
     */
    override fun onEnable() {
        //  释放并加载配置文件
        saveDefaultConfig()
        //  初始化语言管理器
        langManager = LanguageManager(this)
        langManager.load()

        // 初始化菜单管理器
        setupGuiFolder()
        menuManager = MenuManager(this)
        // 初始化竞技场管理器
        arenaManager = ArenaManager(this)
        pvpManager = PvPManager(this)
        arenaManager.loadKit()
        // 初始化数据库管理器
        dbManager = DatabaseManager(this)
        dbManager.setup()
        // 初始化 Service 层 (传入 this 以便 Service 访问插件资源)
        guildService = GuildService(this)
        // 注册指令
        getCommand("guilds")?.setExecutor(GuildCommand(this))
        // 注册事件监听器
        server.pluginManager.registerEvents(VaultListener(this), this) // 经济监听器
        server.pluginManager.registerEvents(MenuListener(this), this) // 菜单监听器
        server.pluginManager.registerEvents(NotifyListener(this), this) // 通知监听器
        server.pluginManager.registerEvents(PvPListener(this), this) // 公会战监听器

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
        server.pluginManager.registerEvents(GuildListener(this), this) // 公会监听器
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
            val defaultMenus = listOf("main_menu.yml", "guilds_list.yml", "guild_members.yml", "guild_buffs.yml", "guild_vaults.yml", "guild_bank.yml","guild_upgrade.yml")
            defaultMenus.forEach { fileName ->
                val destFile = File(guiFolder, fileName)
                if (!destFile.exists()) {
                    saveResource("gui/$fileName", false)
                }
            }
        }
    }
}