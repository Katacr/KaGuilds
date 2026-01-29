package org.katacr.kaguilds.listener

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.scheduler.BukkitTask

/**
 * 金库持有者
 */
class VaultHolder(val guildId: Int, val vaultIndex: Int) : InventoryHolder {
    private var inv: Inventory? = null
    var leaseTask: BukkitTask? = null // 存放续租任务

    override fun getInventory(): Inventory = inv!!
    fun setInventory(inventory: Inventory) { this.inv = inventory }
}