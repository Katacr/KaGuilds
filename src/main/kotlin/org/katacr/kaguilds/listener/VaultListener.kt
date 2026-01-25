package org.katacr.kaguilds.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.SerializationUtil

class VaultListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onVaultClose(event: InventoryCloseEvent) {

        val holder = event.inventory.holder
        if (holder is VaultHolder) {
            holder.leaseTask?.cancel()
            val guildId = holder.guildId
            val index = holder.vaultIndex

            // 1. 序列化当前内容
            val data = SerializationUtil.itemsToBase64(event.inventory.contents)

            // 2. 异步保存并解锁
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                plugin.dbManager.saveVault(guildId, index, data)
                // releaseVaultLock 内部会处理跨服广播
                plugin.guildService.releaseVaultLock(guildId, index)
            })
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerUuid = event.player.uniqueId
        // 查找该玩家在当前服务器持有的所有锁
        plugin.guildService.clearAllLocksByPlayer(playerUuid)
    }
}