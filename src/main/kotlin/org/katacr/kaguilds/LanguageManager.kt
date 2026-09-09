package org.katacr.kaguilds

import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStream

/**
 * 语言管理器。
 *
 * 加载 plugins/KaGuilds/lang/<language>.yml，并以内置 zh_CN / en_US 作为回退来源。
 * 当用户语言文件缺少某个键时，会从 jar 内默认语言补齐到用户文件，减少升级后的缺 key 问题。
 */
class LanguageManager(private val plugin: KaGuilds) {

    private val defaultLanguage = "zh_CN"
    private val fallbackLanguage = "en_US"
    private val bundledLanguages = listOf(defaultLanguage, fallbackLanguage)

    private var currentLanguage = defaultLanguage
    private val messages = mutableMapOf<String, String>()
    private val internalMessages = mutableMapOf<String, YamlConfiguration>()

    fun load() {
        currentLanguage = plugin.config.getString("language", defaultLanguage) ?: defaultLanguage

        saveDefaultMessages()
        loadInternalMessages()
        loadMessages(currentLanguage)
    }

    fun reload() {
        currentLanguage = plugin.config.getString("language", defaultLanguage) ?: defaultLanguage
        saveDefaultMessages()
        loadInternalMessages()
        loadMessages(currentLanguage)
    }

    /**
     * 获取语言文本，支持变量替换。
     * 如果键缺失，自动从 jar 内默认语言文件补齐到用户文件。
     */
    fun get(key: String, vararg placeholders: Pair<String, String>): String {
        var message: String = messages[key] ?: run {
            val default = getDefaultMessage(key)
            if (default != null) {
                addMissingKey(key, default)
                default
            } else {
                plugin.logger.warning("未找到语言键: $key")
                return "§cMissing key: [ $key ]"
            }
        }

        placeholders.forEach { (placeholder, value) ->
            message = message.replace("%$placeholder%", value)
        }

        return ChatColor.translateAlternateColorCodes('&', message)
    }

    private fun saveDefaultMessages() {
        val langFolder = File(plugin.dataFolder, "lang")
        if (!langFolder.exists()) {
            langFolder.mkdirs()
        }
        bundledLanguages.forEach { lang ->
            val file = File(langFolder, "${lang}.yml")
            if (!file.exists()) {
                plugin.saveResource("lang/${lang}.yml", false)
            }
        }
    }

    private fun loadInternalMessages() {
        internalMessages.clear()
        bundledLanguages.forEach { lang ->
            val inputStream: InputStream? = plugin.getResource("lang/${lang}.yml")
            if (inputStream != null) {
                try {
                    internalMessages[lang] = YamlConfiguration.loadConfiguration(inputStream.reader())
                } catch (e: Exception) {
                    plugin.logger.warning("加载内部语言文件失败: lang/${lang}.yml - ${e.message}")
                } finally {
                    inputStream.close()
                }
            }
        }
    }

    private fun loadMessages(language: String) {
        val langFolder = File(plugin.dataFolder, "lang")
        val file = File(langFolder, "${language}.yml")
        if (!file.exists()) {
            plugin.logger.warning("语言文件不存在: lang/${language}.yml，使用默认语言")
            if (language != defaultLanguage) {
                currentLanguage = defaultLanguage
                loadMessages(defaultLanguage)
            }
            return
        }

        val config = YamlConfiguration.loadConfiguration(file)
        currentLanguage = language
        messages.clear()
        for (key in config.getKeys(true)) {
            if (config.isString(key)) {
                messages[key] = config.getString(key) ?: continue
            }
        }
    }

    /**
     * 从内部语言文件中获取默认消息。
     * 优先当前语言，回退到 en_US。
     */
    private fun getDefaultMessage(key: String): String? {
        val currentInternal = internalMessages[currentLanguage]
        if (currentInternal != null && currentInternal.isString(key)) {
            return currentInternal.getString(key)
        }
        if (currentLanguage != fallbackLanguage) {
            val fallbackInternal = internalMessages[fallbackLanguage]
            if (fallbackInternal != null && fallbackInternal.isString(key)) {
                return fallbackInternal.getString(key)
            }
        }
        return null
    }

    /**
     * 将缺失的键写入到用户的语言文件中。
     */
    private fun addMissingKey(key: String, value: String) {
        val langFolder = File(plugin.dataFolder, "lang")
        val file = File(langFolder, "${currentLanguage}.yml")
        if (!file.exists()) return

        try {
            val config = YamlConfiguration.loadConfiguration(file)
            if (config.contains(key)) return

            config.set(key, value)
            config.save(file)
            messages[key] = value
        } catch (e: Exception) {
            plugin.logger.warning("写入缺失的语言键失败: ${e.message}")
        }
    }

    fun getCurrentLanguage(): String = currentLanguage
}
