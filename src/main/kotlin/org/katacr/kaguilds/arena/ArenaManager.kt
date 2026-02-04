package org.katacr.kaguilds.arena

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.SerializationUtil
import java.io.File

class ArenaManager(private val plugin: KaGuilds) {
    private val file = File(plugin.dataFolder, "arena.yml")
    private var config = YamlConfiguration.loadConfiguration(file)
    var kitContents: Array<ItemStack?>? = null
    val arena = ArenaData()

    init {
        loadArena()
    }

    fun loadArena() {
        arena.pos1 = SerializationUtil.deserializeLocation(config.getString("pos1"))
        arena.pos2 = SerializationUtil.deserializeLocation(config.getString("pos2"))
        arena.redSpawn = SerializationUtil.deserializeLocation(config.getString("redSpawn"))
        arena.blueSpawn = SerializationUtil.deserializeLocation(config.getString("blueSpawn"))
    }

    fun saveArena() {
        config.set("pos1", SerializationUtil.serializeLocation(arena.pos1))
        config.set("pos2", SerializationUtil.serializeLocation(arena.pos2))
        config.set("redSpawn", SerializationUtil.serializeLocation(arena.redSpawn))
        config.set("blueSpawn", SerializationUtil.serializeLocation(arena.blueSpawn))
        config.save(file)
    }

    fun saveKit(player: org.bukkit.entity.Player) {
        val lang = plugin.langManager
        // 1. 分别获取主背包、盔甲、副手物品
        val mainItems = player.inventory.contents // 0-35 主要是背包空间
        val armorItems = player.inventory.armorContents // 盔甲栏
        val extraItems = player.inventory.extraContents // 副手栏

        // 2. 合并为一个大数组
        val allItems = mainItems + armorItems + extraItems

        val base64 = SerializationUtil.itemsToBase64(allItems)

        val file = File(plugin.dataFolder, "arena.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("kit_data", base64)
        config.save(file)

        this.kitContents = allItems // 更新缓存
        plugin.logger.info(lang.get("arena-kit-saved", "size" to allItems.size.toString()))
    }

    fun loadKit() {
        val lang = plugin.langManager
        val file = File(plugin.dataFolder, "arena.yml")
        if (!file.exists()) {
            plugin.logger.warning(lang.get("error-not-arena-yml"))
            return
        }

        try {
            val config = YamlConfiguration.loadConfiguration(file)
            val base64 = config.getString("kit_data")

            if (!base64.isNullOrEmpty()) {
                this.kitContents = SerializationUtil.itemStackArrayFromBase64(base64)
            }
        } catch (e: Exception) {
            plugin.logger.severe(lang.get("error-load-arena-kit", "error" to e.message.orEmpty()))
        }
    }
}