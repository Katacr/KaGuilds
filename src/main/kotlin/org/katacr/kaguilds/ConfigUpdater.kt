package org.katacr.kaguilds

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 配置文件更新管理器。
 *
 * 当 `config-version` 落后于当前资源文件时，会备份旧配置、释放新版 config.yml，
 * 再把用户已有的同名键写回新文件。这样能新增配置项，同时尽量保留用户设置。
 *
 * 注意：只迁移新版配置中仍存在的键；已废弃键不会写回。
 */
object ConfigUpdater {

    private const val CURRENT_CONFIG_VERSION = 1
    private const val CONFIG_VERSION_KEY = "config-version"

    /**
     * 检查并更新配置文件。
     * 流程：记录用户配置 → 备份 → 覆盖新配置 → 写入用户值。
     *
     * @return 是否进行了更新
     */
    fun checkAndUpdateConfig(plugin: JavaPlugin, configFile: File): Boolean {
        val userConfigValues = extractUserConfigValues(configFile)
        val configVersion = userConfigValues[CONFIG_VERSION_KEY]?.toString()?.toIntOrNull() ?: 0

        if (configVersion >= CURRENT_CONFIG_VERSION) {
            return false
        }

        plugin.logger.info("检测到旧版本配置文件 (v$configVersion)，正在更新到 v$CURRENT_CONFIG_VERSION ...")

        backupConfig(plugin, configFile, configVersion)

        if (!copyDefaultConfig(plugin, configFile)) {
            return false
        }

        writeUserValues(plugin, configFile, userConfigValues)

        plugin.logger.info("配置文件更新成功！旧版本: v$configVersion -> 新版本: v$CURRENT_CONFIG_VERSION")
        return true
    }

    private fun extractUserConfigValues(configFile: File): Map<String, Any?> {
        val config = YamlConfiguration.loadConfiguration(configFile)
        val userValues = mutableMapOf<String, Any?>()
        config.getKeys(true).forEach { key ->
            if (config.isString(key) || config.isBoolean(key) || config.isInt(key) || config.isDouble(key) || config.isList(key)) {
                userValues[key] = config.get(key)
            }
        }
        return userValues
    }

    private fun copyDefaultConfig(plugin: JavaPlugin, configFile: File): Boolean {
        return try {
            if (configFile.exists()) {
                Files.delete(configFile.toPath())
            }
            plugin.saveResource("config.yml", false)
            true
        } catch (e: IOException) {
            plugin.logger.severe("保存默认配置文件失败: ${e.message}")
            false
        }
    }

    private fun writeUserValues(plugin: JavaPlugin, configFile: File, userValues: Map<String, Any?>) {
        try {
            val config = YamlConfiguration.loadConfiguration(configFile)
            var preservedCount = 0
            userValues.forEach { (key, value) ->
                if (key == CONFIG_VERSION_KEY) return@forEach
                if (config.contains(key)) {
                    config.set(key, value)
                    preservedCount++
                }
            }
            config.save(configFile)
            plugin.logger.info("已保留 $preservedCount 项用户自定义配置")
        } catch (e: IOException) {
            plugin.logger.warning("保存配置文件失败: ${e.message}")
        }
    }

    private fun backupConfig(plugin: JavaPlugin, configFile: File, configVersion: Int) {
        val timestamp = System.currentTimeMillis()
        val backupFile = File(configFile.parent, "config_v${configVersion}_backup_${timestamp}.yml")
        try {
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            plugin.logger.info("旧配置已备份到: ${backupFile.name}")
        } catch (e: IOException) {
            plugin.logger.warning("备份配置文件失败: ${e.message}")
        }
    }

    fun getConfigVersion(config: FileConfiguration): Int {
        return config.getInt(CONFIG_VERSION_KEY, 0)
    }
}
