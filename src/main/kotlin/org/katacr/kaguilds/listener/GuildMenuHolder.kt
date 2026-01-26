package org.katacr.kaguilds.listener
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.configuration.ConfigurationSection

class GuildMenuHolder(
    val title: String,
    val layout: List<String>,
    val buttons: ConfigurationSection?,
    var currentPage: Int = 0
) : InventoryHolder {
    private var inventory: Inventory? = null

    override fun getInventory(): Inventory = inventory!!
    fun setInventory(inv: Inventory) { this.inventory = inv }

    // 获取点击位置对应的字符标识 (例如第 19 格对应 '1')
    fun getIconChar(slot: Int): String? {
        val row = slot / 9
        val col = slot % 9
        if (row >= layout.size) return null
        val char = layout[row].getOrNull(col)?.toString()
        return if (char == null || char == " ") null else char
    }
}
