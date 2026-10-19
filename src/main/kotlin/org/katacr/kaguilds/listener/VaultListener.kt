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
        val lang = plugin.langManager
        val holder = event.inventory.holder as? VaultHolder ?: return

        // 1. 取消续租任务
        holder.leaseTask?.cancel()

        val guildId = holder.guildId
        val index = holder.vaultIndex
        val player = event.player as Player
        val playerUuid = player.uniqueId
        val playerName = player.name

        // 2. 在主线程完成序列化（避免异步操作 Inventory 导致的线程竞争问题）
        val data = SerializationUtil.itemsToBase64(event.inventory.contents)

        plugin.runAsync {
            // 3. 尝试保存数据
            val saveSuccess = plugin.dbManager.vaultRepository.saveVault(guildId, index, data, playerUuid)

            if (saveSuccess) {
                // 4. 只有保存成功，才释放物理锁与内存锁，并广播跨服解锁（回到主线程发包）
                plugin.dbManager.vaultRepository.releaseLock(guildId, index, playerUuid)
                plugin.runMain {
                    plugin.vaultService.releaseVaultLock(guildId, index, isFromNetwork = false)
                }
            } else {
                // 5. 保存失败：保留物理锁（不释放 releaseLock），直到租约过期。
                // 保留内存锁，避免本服在锁仍有效时重复打开导致数据竞争。
                // 物品已在关闭时从背包移除，但数据未写入；提示玩家，等待租约过期后重试。
                plugin.logger.severe(lang.get("error-save-vault", "id" to guildId.toString(), "index" to index.toString(), "player" to playerName))
                plugin.runMain {
                    player.sendMessage(lang.get("error-database"))
                }
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerUuid = event.player.uniqueId
        // 查找该玩家在当前服务器持有的所有锁
        plugin.guildService.clearAllLocksByPlayer(playerUuid)
    }
}
