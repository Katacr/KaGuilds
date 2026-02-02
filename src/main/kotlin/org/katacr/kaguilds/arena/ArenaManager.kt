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
        // 获取玩家包括装备槽在内的所有物品
        val contents = player.inventory.contents
        val base64 = SerializationUtil.itemsToBase64(contents)

        val file = File(plugin.dataFolder, "arena.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("kit_data", base64)
        config.save(file)

        this.kitContents = contents
        plugin.logger.info("公会战套装已更新！")
    }

    fun loadKit() {
        val file = File(plugin.dataFolder, "arena.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        val base64 = config.getString("kit_data")
        if (base64 != null) {
            this.kitContents = SerializationUtil.itemStackArrayFromBase64(base64)
        }
    }
}