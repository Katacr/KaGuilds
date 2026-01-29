package org.katacr.kaguilds.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.SerializationUtil

class VaultListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onVaultClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? VaultHolder ?: return

        // 1. 取消续租任务
        holder.leaseTask?.cancel()

        val guildId = holder.guildId
        val index = holder.vaultIndex
        val player = event.player as Player

        // 2. 序列化并保存
        val data = SerializationUtil.itemsToBase64(event.inventory.contents)

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 保存数据
            plugin.dbManager.saveVault(guildId, index, data)
            // 3. 物理释放锁 (将过期时间设为 0)
            plugin.dbManager.releaseLock(guildId, index, player.uniqueId)

            // 4. 清理内存锁
            plugin.guildService.vaultLocks.remove(Pair(guildId, index))
        })
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerUuid = event.player.uniqueId
        // 查找该玩家在当前服务器持有的所有锁
        plugin.guildService.clearAllLocksByPlayer(playerUuid)
    }
}