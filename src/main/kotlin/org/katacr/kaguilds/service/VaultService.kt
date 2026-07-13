package org.katacr.kaguilds.service

import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.listener.VaultHolder
import org.katacr.kaguilds.util.SerializationUtil
import java.io.DataOutputStream
import java.util.UUID

/**
 * 公会仓库服务，负责仓库打开、锁同步、租约刷新和跨服锁消息。
 */
class VaultService(private val plugin: KaGuilds) {
    val vaultLocks = mutableMapOf<Pair<Int, Int>, UUID>()

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

        plugin.runAsync {
            val success = plugin.dbManager.vaultRepository.tryGrabLock(guildId, vaultIndex, player.uniqueId)

            if (!success) {
                plugin.runMain {
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

        if (plugin.config.getBoolean("proxy", false) && !isFromNetwork) {
            val isLocked = plugin.dbManager.vaultRepository.isVaultLocked(guildId, vaultIndex)
            if (isLocked) {
                player.sendMessage(plugin.langManager.get("vault-locked", "index" to vaultIndex.toString()))
                return false
            }
        }

        vaultLocks[lockKey] = player.uniqueId

        if (!isFromNetwork && plugin.config.getBoolean("proxy")) {
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

            if (plugin.config.getBoolean("proxy")) {
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
        if (plugin.config.getBoolean("proxy")) {
            sendVaultSyncPacket(0, 0, UUID.randomUUID(), "ForceUnlockAll")
        }
    }

    /**
     * 释放仓库内存锁，并在需要时广播跨服解锁。
     */
    fun releaseVaultLock(guildId: Int, vaultIndex: Int, isFromNetwork: Boolean = false) {
        val lockKey = Pair(guildId, vaultIndex)
        if (vaultLocks.containsKey(lockKey)) {
            vaultLocks.remove(lockKey)

            if (!isFromNetwork && plugin.config.getBoolean("proxy")) {
                sendVaultSyncPacket(guildId, vaultIndex, UUID.randomUUID(), "Unlock")
            }
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
                    vaultLocks.remove(Pair(guildId, vaultIndex))
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
        return plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val topInv = player.openInventory.topInventory
            val holder = topInv.holder as? VaultHolder ?: return@Runnable

            if (holder.guildId != guildId || holder.vaultIndex != index) return@Runnable

            plugin.runAsync {
                val nextExpire = System.currentTimeMillis() + 30000
                plugin.dbManager.vaultRepository.refreshLockLease(guildId, index, player.uniqueId, nextExpire)
            }
        }, 100L, 100L)
    }

    private fun sendVaultSyncPacket(guildId: Int, index: Int, uuid: UUID, type: String) {
        if (!plugin.config.getBoolean("proxy")) return

        val out = createDataOutput()
        try {
            out.outputStream.writeUTF("VaultSync")
            out.outputStream.writeUTF(type)
            out.outputStream.writeInt(guildId)
            out.outputStream.writeInt(index)
            out.outputStream.writeUTF(uuid.toString())

            val messenger = plugin.server.onlinePlayers.firstOrNull()
            messenger?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
        } catch (e: Exception) {
            plugin.logger.warning(plugin.langManager.get("error-send-packet", "error" to e.message.toString()))
        }
    }

    private fun createDataOutput(): ByteArrayDataOutputStream {
        return ByteArrayDataOutputStream()
    }

    private class ByteArrayDataOutputStream {
        private val byteArrayStream = java.io.ByteArrayOutputStream()
        val outputStream = DataOutputStream(byteArrayStream)

        fun toByteArray(): ByteArray = byteArrayStream.toByteArray()
    }
}
