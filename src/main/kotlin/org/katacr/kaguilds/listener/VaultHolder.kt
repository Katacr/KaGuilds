package org.katacr.kaguilds.listener

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.scheduler.BukkitTask

// 这个类就像一个书签，插在 Inventory 对象里，帮我们记住 guildId 和 index
class VaultHolder(val guildId: Int, val vaultIndex: Int) : InventoryHolder {
    private var inv: Inventory? = null
    var leaseTask: BukkitTask? = null // 存放续租任务

    override fun getInventory(): Inventory = inv!!
    fun setInventory(inventory: Inventory) { this.inv = inventory }
}