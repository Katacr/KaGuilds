package org.katacr.kaguilds.listener
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.configuration.ConfigurationSection

class GuildMenuHolder(
    val title: String,
    val layout: List<String>,
    val buttons: ConfigurationSection?,
    var currentPage: Int = 0,
    val menuName: String
) : InventoryHolder {
    private var inventory: Inventory? = null
    var updateTask: org.bukkit.scheduler.BukkitTask? = null
    override fun getInventory(): Inventory = inventory!!
    fun setInventory(inv: Inventory) { this.inventory = inv }

    // 获取点击位置对应的字符标识
    fun getIconChar(slot: Int): String? {
        val row = slot / 9
        val col = slot % 9
        if (row >= layout.size) return null
        val char = layout[row].getOrNull(col)?.toString()
        return if (char == null || char == " ") null else char
    }
    // 关闭菜单时清理任务
    fun stopUpdate() {
        updateTask?.cancel()
        updateTask = null
    }
}
