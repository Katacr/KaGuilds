package org.katacr.kaguilds

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class LanguageManager(private val plugin: KaGuilds) {

    private var langConfig: YamlConfiguration? = null

    fun load() {
        // 从 config.yml 读取当前设置的语言，默认为 zh_CN
        val langType = plugin.config.getString("language", "zh_CN") ?: "zh_CN"
        val langFile = File(plugin.dataFolder, "lang/$langType.yml")

        // 如果文件不存在，则从 Jar 包内保存默认语言文件
        if (!langFile.exists()) {
            plugin.saveResource("lang/zh_CN.yml", false)
            plugin.saveResource("lang/en_US.yml", false)
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile)
    }

    /**
     * 获取语言文本，支持变量替换
     */
    fun get(
        key: String,
        vararg placeholders: Pair<String, String>,
        withPrefix: Boolean = true
    ): String {
        var message = langConfig?.getString(key) ?: "Missing: $key"

        // 替换占位符
        placeholders.forEach { (k, v) ->
            message = message.replace("%$k%", v)
        }

        val prefix = if (withPrefix) langConfig?.getString("prefix", "") ?: "" else ""
        return prefix + message
    }
}