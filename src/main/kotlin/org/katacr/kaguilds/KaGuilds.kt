package org.katacr.kaguilds

import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.RegisteredServiceProvider

class KaGuilds : JavaPlugin() {
    val inviteCache = mutableMapOf<UUID, Int>()
    var economy: Economy? = null
    val playerGuildCache = mutableMapOf<UUID, Int>()
    lateinit var dbManager: DatabaseManager
    lateinit var langManager: LanguageManager

    override fun onEnable() {
        // 1. 释放并加载配置文件
        saveDefaultConfig()
        // 初始化语言管理器
        langManager = LanguageManager(this)
        langManager.load()
        // 2. 初始化数据库管理器
        dbManager = DatabaseManager(this)
        dbManager.setup()

        // 3. 注册指令
        getCommand("guilds")?.setExecutor(GuildCommand(this))

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
    override fun onDisable() {// 使用 Kotlin 的属性初始化检查，防止在启动失败时调用报错
        if (::dbManager.isInitialized) {
            dbManager.close()
        }
        logger.info("KaGuilds 已安全关闭。")
    }
    // 在 reload 逻辑中也要加上这一行
    fun reloadPlugin() {
        reloadConfig()
        langManager.load()
    }
    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) return false
        val rsp: RegisteredServiceProvider<Economy> = server.servicesManager.getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return true
    }
}