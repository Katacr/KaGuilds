package org.katacr.kaguilds.service

import org.katacr.kaguilds.KaGuilds
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 公会银行利息结算。
 * 收敛原先分散在 GuildListener 与 TaskManager 的两份重复实现，统一为条件更新（防并发/多服重复计息）。
 */
class InterestService(private val plugin: KaGuilds) {

    /**
     * 结算单个公会的利息。
     * @return true 表示本次成功结算（余额与计息日期已推进）
     */
    fun settle(guildId: Int): Boolean {
        val guildData = plugin.dbManager.guildRepository.getGuildData(guildId) ?: return false
        val today = getTodayDateLong()
        val lastInterestDate = guildData.lastInterestDate
        val days = calculateDaysBetween(lastInterestDate, today)
        if (days <= 0) return false

        val interestRate = getInterestRate(guildData.level)
        if (interestRate == null || interestRate <= 0) return false

        val rateMultiplier = 1 + (interestRate / 100.0)
        val finalBalance = guildData.balance * rateMultiplier.pow(days.toDouble())
        val totalInterest = ((finalBalance - guildData.balance) * 100).roundToInt() / 100.0
        if (totalInterest <= 0) return false

        val applied = plugin.dbManager.bankRepository.applyInterestIfDateUnchanged(
            guildId, lastInterestDate, finalBalance, today
        )
        if (applied) {
            plugin.dbManager.bankRepository.logBankTransaction(guildId, "SYSTEM", "INTEREST", totalInterest)
        }
        return applied
    }

    /** 批量结算。 */
    fun settleAll(guildIds: List<Int>) {
        guildIds.forEach { settle(it) }
    }

    private fun getInterestRate(level: Int): Double? {
        val section = plugin.levelsConfig.getConfigurationSection("levels.$level") ?: return null
        return section.getDouble("bank-interest", 0.0)
    }

    private fun calculateDaysBetween(lastTimestamp: Long, currentTimestamp: Long): Int {
        if (lastTimestamp == 0L) return 1
        val zone = java.time.ZoneId.systemDefault()
        val lastDate = java.time.Instant.ofEpochMilli(lastTimestamp).atZone(zone).toLocalDate()
        val currentDate = java.time.Instant.ofEpochMilli(currentTimestamp).atZone(zone).toLocalDate()
        return (currentDate.toEpochDay() - lastDate.toEpochDay()).toInt()
    }

    private fun getTodayDateLong(): Long {
        val todayMidnight = java.time.LocalDate.now().atStartOfDay()
        return java.time.ZonedDateTime.of(todayMidnight, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
