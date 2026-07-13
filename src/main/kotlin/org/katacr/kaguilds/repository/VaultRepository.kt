package org.katacr.kaguilds.repository

import org.katacr.kaguilds.DatabaseManager
import java.util.UUID

/**
 * 公会仓库数据仓库，负责仓库内容、数据库锁和租约相关 SQL。
 */
class VaultRepository(private val db: DatabaseManager) {
    private val plugin = db.plugin

    fun saveVault(guildId: Int, index: Int, data: String, editorUuid: UUID): Boolean {
        val sql = """
            UPDATE guild_vaults
            SET items_data = ?, lock_expire = 0
            WHERE guild_id = ? AND vault_index = ? AND last_editor = ?
        """.trimIndent()

        if (db.dataSource == null) return false

        return try {
            db.dataSource!!.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, data)
                    ps.setInt(2, guildId)
                    ps.setInt(3, index)
                    ps.setString(4, editorUuid.toString())
                    ps.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("无法保存公会 $guildId 的仓库 $index: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun getVaultData(guildId: Int, index: Int): String? {
        val sql = "SELECT items_data FROM guild_vaults WHERE guild_id = ? AND vault_index = ?"

        try {
            return db.dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setInt(2, index)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        rs.getString("items_data")
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe(
                plugin.langManager.get(
                    "error-load-vault",
                    "id" to guildId.toString(),
                    "index" to index.toString(),
                    "message" to e.message.toString()
                )
            )
            e.printStackTrace()
            return null
        }
    }

    fun tryGrabLock(guildId: Int, index: Int, playerUuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        val expireAt = now + 30000

        return try {
            db.dataSource?.connection?.use { conn ->
                val isMySQL = db.dataSource?.jdbcUrl?.contains("mysql", ignoreCase = true) == true
                val initSql = if (isMySQL) {
                    "INSERT IGNORE INTO guild_vaults (guild_id, vault_index, lock_expire) VALUES (?, ?, 0)"
                } else {
                    "INSERT OR IGNORE INTO guild_vaults (guild_id, vault_index, lock_expire) VALUES (?, ?, 0)"
                }

                conn.prepareStatement(initSql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setInt(2, index)
                    ps.executeUpdate()
                }

                val updateSql = """
                    UPDATE guild_vaults
                    SET last_editor = ?, lock_expire = ?
                    WHERE guild_id = ? AND vault_index = ?
                    AND (lock_expire < ? OR last_editor = ?)
                """.trimIndent()

                conn.prepareStatement(updateSql).use { ps ->
                    ps.setString(1, playerUuid.toString())
                    ps.setLong(2, expireAt)
                    ps.setInt(3, guildId)
                    ps.setInt(4, index)
                    ps.setLong(5, now)
                    ps.setString(6, playerUuid.toString())

                    ps.executeUpdate() > 0
                }
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun releaseLock(guildId: Int, index: Int, playerUuid: UUID) {
        val sql = "UPDATE guild_vaults SET lock_expire = 0 WHERE guild_id = ? AND vault_index = ? AND last_editor = ?"

        try {
            db.dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setInt(2, index)
                    ps.setString(3, playerUuid.toString())
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("释放仓库锁时发生数据库异常: ${e.message}")
            e.printStackTrace()
        }
    }

    fun refreshLockLease(guildId: Int, index: Int, playerUuid: UUID, expireAt: Long) {
        val sql = "UPDATE guild_vaults SET lock_expire = ? WHERE guild_id = ? AND vault_index = ? AND last_editor = ?"

        db.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, expireAt)
                ps.setInt(2, guildId)
                ps.setInt(3, index)
                ps.setString(4, playerUuid.toString())
                ps.executeUpdate()
            }
        }
    }

    fun isVaultLocked(guildId: Int, vaultIndex: Int): Boolean {
        val sql = "SELECT lock_expire FROM guild_vaults WHERE guild_id = ? AND vault_index = ?"
        return db.dataSource?.connection?.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, guildId)
                ps.setInt(2, vaultIndex)
                val rs = ps.executeQuery()
                rs.next() && rs.getLong("lock_expire") > System.currentTimeMillis()
            }
        } ?: false
    }
}
