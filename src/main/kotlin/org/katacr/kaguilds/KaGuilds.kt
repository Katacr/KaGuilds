package org.katacr.kaguilds

import org.bukkit.plugin.java.JavaPlugin

class KaGuilds : JavaPlugin() {
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

        // 检查服务器是否安装了 PlaceholderAPI
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            KaGuildsExpansion(this).register()
            logger.info("已成功对接 PlaceholderAPI 变量。")
        }
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
}