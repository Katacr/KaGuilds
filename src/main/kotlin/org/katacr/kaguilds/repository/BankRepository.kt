package org.katacr.kaguilds.repository

import org.katacr.kaguilds.DatabaseManager
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.ceil

/**
 * 公会金库数据仓库，负责金库余额和银行日志的 SQL 访问。
 */
class BankRepository(private val db: DatabaseManager) {
    private val plugin = db.plugin

    fun updateGuildBalance(guildId: Int, amount: Double): Boolean {
        val sql = "UPDATE guild_data SET balance = balance + ? WHERE id = ?"
        return executeBalanceUpdate(sql) { ps ->
            ps.setDouble(1, amount)
            ps.setInt(2, guildId)
        }
    }

    fun depositGuildBalanceIfWithinLimit(guildId: Int, amount: Double, maxBalance: Double): Boolean {
        val sql = "UPDATE guild_data SET balance = balance + ? WHERE id = ? AND balance + ? <= ?"
        return executeBalanceUpdate(sql) { ps ->
            ps.setDouble(1, amount)
            ps.setInt(2, guildId)
            ps.setDouble(3, amount)
            ps.setDouble(4, maxBalance)
        }
    }

    fun withdrawGuildBalanceIfEnough(guildId: Int, amount: Double): Boolean {
        val sql = "UPDATE guild_data SET balance = balance - ? WHERE id = ? AND balance >= ?"
        return executeBalanceUpdate(sql) { ps ->
            ps.setDouble(1, amount)
            ps.setInt(2, guildId)
            ps.setDouble(3, amount)
        }
    }

    fun getBankLogs(guildId: Int, page: Int): List<String> {
        val logs = mutableListOf<String>()
        val offset = (page - 1) * 10
        val sql = "SELECT * FROM guild_bank_logs WHERE guild_id = ? ORDER BY time DESC LIMIT 10 OFFSET ?"

        try {
            db.dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setInt(2, offset)
                    val rs = ps.executeQuery()

                    val dateFormat = SimpleDateFormat(plugin.config.get("date-format") as String)
                    while (rs.next()) {
                        val typeRaw = rs.getString("type")
                        val typeStr = when (typeRaw) {
                            "ADD" -> plugin.langManager.get("bank-text-add")
                            "REMOVE" -> plugin.langManager.get("bank-text-remove")
                            "SET" -> plugin.langManager.get("bank-text-set")
                            "SET_TP" -> plugin.langManager.get("bank-text-settp")
                            "SET_ICON" -> plugin.langManager.get("bank-text-seticon")
                            "SET_MOTD" -> plugin.langManager.get("bank-text-setmotd")
                            "RENAME" -> plugin.langManager.get("bank-text-rename")
                            "BUY_BUFF" -> plugin.langManager.get("bank-text-buybuff")
                            "INTEREST" -> plugin.langManager.get("bank-text-interest")
                            "GET" -> plugin.langManager.get("bank-text-get")
                            else -> "§cUnknown Action"
                        }
                        val time = dateFormat.format(Date(rs.getLong("time")))
                        logs.add("§7[$time] §7${rs.getString("player_name")} $typeStr §f${rs.getDouble("amount")} §7${plugin.langManager.get("balance-name")}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return logs
    }

    fun getBankLogTotalPages(guildId: Int): Int {
        try {
            db.connection.use { conn ->
                val ps = conn.prepareStatement("SELECT COUNT(*) FROM guild_bank_logs WHERE guild_id = ?")
                ps.setInt(1, guildId)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    val total = rs.getInt(1)
                    return ceil(total.toDouble() / 10.0).toInt().coerceAtLeast(1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 1
    }

    fun logBankTransaction(guildId: Int, playerName: String, type: String, amount: Double) {
        val sql = "INSERT INTO guild_bank_logs (guild_id, player_name, type, amount, time) VALUES (?, ?, ?, ?, ?)"
        try {
            db.dataSource?.connection?.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, guildId)
                    ps.setString(2, playerName)
                    ps.setString(3, type)
                    ps.setDouble(4, amount)
                    ps.setLong(5, System.currentTimeMillis())
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun executeBalanceUpdate(sql: String, bind: (java.sql.PreparedStatement) -> Unit): Boolean {
        return try {
            db.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    bind(ps)
                    ps.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
