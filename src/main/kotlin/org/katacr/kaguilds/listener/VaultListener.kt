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

        // 2. 在主线程完成序列化（避免异步操作 Inventory 导致的线程竞争问题）
        val data = SerializationUtil.itemsToBase64(event.inventory.contents)

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 3. 尝试保存数据
            val saveSuccess = plugin.dbManager.saveVault(guildId, index, data)

            if (saveSuccess) {
                // 4. 只有保存成功，才释放物理锁
                plugin.dbManager.releaseLock(guildId, index, player.uniqueId)

                // 5. 释放内存锁
                plugin.guildService.vaultLocks.remove(Pair(guildId, index))
            } else {
                // 6. 异常处理：保存失败时不释放锁，直到租约过期。这能最大限度保护玩家物品不被覆盖。
                plugin.logger.severe("【紧急】公会 $guildId 的仓库 $index 保存失败！")
                plugin.logger.severe("玩家 ${player.name} 的修改未生效，为保护数据，物理锁未立即释放。")

                // 依然需要清理本地内存锁，允许本服稍后重新尝试开启
                plugin.guildService.vaultLocks.remove(Pair(guildId, index))

                player.sendMessage("§c[!] 仓库数据保存异常，请联系管理员。")
            }
        })
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerUuid = event.player.uniqueId
        // 查找该玩家在当前服务器持有的所有锁
        plugin.guildService.clearAllLocksByPlayer(playerUuid)
    }
}