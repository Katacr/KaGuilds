package org.katacr.kaguilds.service

import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.listener.VaultHolder
import org.katacr.kaguilds.util.SerializationUtil
import java.util.UUID

/**
 * 公会仓库服务，负责仓库打开、锁同步、租约刷新和跨服锁消息。
 */
class VaultService(private val plugin: KaGuilds) {
    // 内存锁：主线程（开仓库/跨服收包）与异步线程（关闭保存后释放）都会访问，必须线程安全
    val vaultLocks = java.util.concurrent.ConcurrentHashMap<Pair<Int, Int>, UUID>()

    /**
     * 玩家尝试开启仓库。
     */
    fun openVault(player: Player, vaultIndex: Int) {
        val lang = plugin.langManager
        val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId) ?: return

        val guildData = plugin.dbManager.guildRepository.getGuildData(guildId) ?: return
        val maxVaults = plugin.levelsConfig.getInt("levels.${guildData.level}.vaults", 1)
        if (vaultIndex > maxVaults) {
            player.sendMessage(lang.get("vault-max-vaults", "max" to maxVaults.toString()))
            return
        }

        // 先抢本服内存锁并广播 Lock（与管理员路径一致），避免绕过跨服锁同步导致多服同时编辑
        if (!tryLockVault(guildId, vaultIndex, player)) return

        plugin.runAsync {
            val success = plugin.dbManager.vaultRepository.tryGrabLock(guildId, vaultIndex, player.uniqueId)

            if (!success) {
                plugin.runMain {
                    // DB 抢锁失败：回滚内存锁并广播 Unlock，避免其他服一直认为被占用
                    releaseVaultLock(guildId, vaultIndex, false)
                    player.sendMessage(lang.get("vault-locked", "index" to vaultIndex.toString()))
                }
                return@runAsync
            }

            val rawData = plugin.dbManager.vaultRepository.getVaultData(guildId, vaultIndex)

            plugin.runMain {
                val holder = VaultHolder(guildId, vaultIndex)
                val inv = plugin.server.createInventory(holder, 54, lang.get("vault-title", "index" to vaultIndex.toString()))
                holder.setInventory(inv)

                if (!rawData.isNullOrEmpty()) {
                    inv.contents = SerializationUtil.itemStackArrayFromBase64(rawData)
                }

                player.openInventory(inv)
                holder.leaseTask = startVaultLeaseTask(player, guildId, vaultIndex)
            }
        }
    }

    /**
     * 尝试锁定仓库。
     */
    fun tryLockVault(guildId: Int, vaultIndex: Int, player: Player, isFromNetwork: Boolean = false): Boolean {
        val lockKey = Pair(guildId, vaultIndex)

        if (vaultLocks.containsKey(lockKey)) {
            val occupantUuid = vaultLocks[lockKey]
            if (occupantUuid != player.uniqueId) {
                player.sendMessage(plugin.langManager.get("vault-locked", "index" to vaultIndex.toString()))
                return false
            }
            return true
        }

        if (plugin.isProxyEnabled() && !isFromNetwork) {
            val isLocked = plugin.dbManager.vaultRepository.isVaultLocked(guildId, vaultIndex)
            if (isLocked) {
                player.sendMessage(plugin.langManager.get("vault-locked", "index" to vaultIndex.toString()))
                return false
            }
        }

        vaultLocks[lockKey] = player.uniqueId

        if (!isFromNetwork && plugin.isProxyEnabled()) {
            sendVaultSyncPacket(guildId, vaultIndex, player.uniqueId, "Lock")
        }

        return true
    }

    /**
     * 玩家退出时，清理其持有的所有锁并同步到跨服。
     */
    fun clearAllLocksByPlayer(playerUuid: UUID) {
        val locksToRelease = vaultLocks.filterValues { it == playerUuid }.keys.toList()

        for (lockKey in locksToRelease) {
            val guildId = lockKey.first
            val index = lockKey.second

            vaultLocks.remove(lockKey)

            if (plugin.isProxyEnabled()) {
                sendVaultSyncPacket(guildId, index, playerUuid, "Unlock")
            }

            plugin.logger.info(plugin.langManager.get("vault-unlocked", "id" to guildId.toString(), "index" to index.toString()))
        }
    }

    /**
     * 强制重置所有仓库锁。
     */
    fun forceResetAllLocks() {
        vaultLocks.clear()
        if (plugin.isProxyEnabled()) {
            sendVaultSyncPacket(0, 0, UUID.randomUUID(), "ForceUnlockAll")
        }
    }

    /**
     * 释放仓库内存锁，并在需要时广播跨服解锁。
     * @param occupant 远端 Unlock 携带的操作者；仅当与当前内存锁持有者一致时才删除，
     *                 防止其他服的任意玩家关闭仓库时误清本服正在使用的锁。
     */
    fun releaseVaultLock(guildId: Int, vaultIndex: Int, isFromNetwork: Boolean = false, occupant: UUID? = null) {
        val lockKey = Pair(guildId, vaultIndex)
        if (isFromNetwork && occupant != null) {
            val current = vaultLocks[lockKey]
            if (current != null && current != occupant) {
                // 持有者不匹配，忽略该远端解锁，避免误清他人锁
                return
            }
        }
        val holder = vaultLocks.remove(lockKey) ?: return
        if (!isFromNetwork && plugin.isProxyEnabled()) {
            // 广播真实的锁持有者 UUID，接收端据此校验，避免误清他人锁
            sendVaultSyncPacket(guildId, vaultIndex, holder, "Unlock")
        }
    }

    /**
     * 同步远程服务器发来的仓库锁。
     */
    fun syncRemoteLock(guildId: Int, index: Int, uuid: UUID) {
        vaultLocks[Pair(guildId, index)] = uuid
    }

    /**
     * 管理员打开指定公会仓库。
     */
    fun adminOpenVault(admin: Player, guildId: Int, vaultIndex: Int) {
        val lang = plugin.langManager
        if (vaultIndex !in 1..9) {
            admin.sendMessage(plugin.langManager.get("error-invalid-vault-index"))
            return
        }

        if (!tryLockVault(guildId, vaultIndex, admin)) return

        plugin.runAsync {
            val success = plugin.dbManager.vaultRepository.tryGrabLock(guildId, vaultIndex, admin.uniqueId)

            if (!success) {
                plugin.runMain {
                    // 回滚内存锁并广播 Unlock，避免其他服一直认为该仓库被占用
                    releaseVaultLock(guildId, vaultIndex, false)
                    admin.sendMessage(lang.get("vault-locked", "index" to vaultIndex.toString()))
                }
                return@runAsync
            }

            val rawData = plugin.dbManager.vaultRepository.getVaultData(guildId, vaultIndex)

            plugin.runMain {
                val holder = VaultHolder(guildId, vaultIndex)
                val inv = plugin.server.createInventory(
                    holder,
                    54,
                    plugin.langManager.get(
                        "admin-vault-title",
                        "index" to vaultIndex.toString(),
                        "id" to guildId.toString()
                    )
                )
                holder.setInventory(inv)

                if (!rawData.isNullOrEmpty()) {
                    inv.contents = SerializationUtil.itemStackArrayFromBase64(rawData)
                }

                admin.openInventory(inv)
                holder.leaseTask = startVaultLeaseTask(admin, guildId, vaultIndex)
            }
        }
    }

    /**
     * 开启仓库锁租约刷新任务。
     */
    fun startVaultLeaseTask(player: Player, guildId: Int, index: Int): BukkitTask {
        return object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                // 玩家离线或已切走该仓库界面：停止空转（不再刷新租约，交由租约自然过期）
                if (!player.isOnline) {
                    cancel()
                    return
                }
                val holder = player.openInventory.topInventory.holder as? VaultHolder
                if (holder == null || holder.guildId != guildId || holder.vaultIndex != index) {
                    cancel()
                    return
                }
                plugin.runAsync {
                    val nextExpire = System.currentTimeMillis() + 30000
                    plugin.dbManager.vaultRepository.refreshLockLease(guildId, index, player.uniqueId, nextExpire)
                }
            }
        }.runTaskTimer(plugin, 100L, 100L)
    }

    private fun sendVaultSyncPacket(guildId: Int, index: Int, uuid: UUID, type: String) {
        plugin.networkBus.send(org.katacr.kaguilds.util.NetworkCodec.encodeVaultSync(type, guildId, index, uuid))
    }
}
