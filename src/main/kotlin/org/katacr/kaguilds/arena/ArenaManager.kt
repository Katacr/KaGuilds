package org.katacr.kaguilds.arena

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.SerializationUtil
import java.io.File

class ArenaManager(private val plugin: KaGuilds) {
    private val file = File(plugin.dataFolder, "arena.yml")
    private var config = YamlConfiguration.loadConfiguration(file)
    var redKitContents: Array<ItemStack?>? = null
    var blueKitContents: Array<ItemStack?>? = null
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

    fun saveKit(player: org.bukkit.entity.Player, team: String) {
        val lang = plugin.langManager
        val mainItems = player.inventory.contents
        val armorItems = player.inventory.armorContents
        val extraItems = player.inventory.extraContents
        val allItems = mainItems + armorItems + extraItems

        val base64 = SerializationUtil.itemsToBase64(allItems)
        val file = File(plugin.dataFolder, "arena.yml")
        val config = YamlConfiguration.loadConfiguration(file)

        // 根据团队存储不同的路径
        val path = if (team.lowercase() == "red") "kit_data_red" else "kit_data_blue"
        config.set(path, base64)
        config.save(file)

        // 更新内存缓存
        if (team.lowercase() == "red") redKitContents = allItems else blueKitContents = allItems

        plugin.logger.info(lang.get("arena-kit-saved", "team" to team, "size" to allItems.size.toString()))
    }

    fun loadKit() {
        val lang = plugin.langManager
        val file = File(plugin.dataFolder, "arena.yml")

        if (!file.exists()) return

        try {
            val config = YamlConfiguration.loadConfiguration(file)

            // 1. 加载红队套装数据
            config.getString("kit_data_red")?.let { base64 ->
                if (base64.isNotEmpty()) {
                    this.redKitContents = SerializationUtil.itemStackArrayFromBase64(base64)
                }
            }

            // 2. 加载蓝队套装数据
            config.getString("kit_data_blue")?.let { base64 ->
                if (base64.isNotEmpty()) {
                    this.blueKitContents = SerializationUtil.itemStackArrayFromBase64(base64)
                }
            }

            // 日志后台记录
            // val redSize = redKitContents?.size ?: 0
            // val blueSize = blueKitContents?.size ?: 0
            // plugin.logger.info(lang.get("arena-kit-load", "red" to redSize.toString(), "blue" to blueSize.toString()))

        } catch (e: Exception) {
            // 报错时（如 Base64 损坏）才打印错误
            plugin.logger.severe(lang.get("error-load-arena-kit", "error" to e.message.orEmpty()))
        }
    }
}