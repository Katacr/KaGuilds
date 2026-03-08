package org.katacr.kaguilds.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.katacr.kaguilds.KaGuilds
import kotlin.math.pow
import kotlin.math.roundToInt

class GuildListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        // 玩家加入时，异步从数据库读取其公会ID并存入缓存
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
            if (guildId != null) {
                plugin.playerGuildCache[player.uniqueId] = guildId

                // 检查并计算公会的银行利息
                checkAndCalculateGuildInterest(guildId)
            }
        })
    }

    /**
     * 检查并计算公会的银行利息
     * @param guildId 公会ID
     */
    private fun checkAndCalculateGuildInterest(guildId: Int) {
        val guildData = plugin.dbManager.getGuildData(guildId) ?: return

        val today = getTodayDateLong()
        val lastInterestDate = guildData.lastInterestDate

        // 计算距离上次计息的天数
        val daysSinceLastInterest = calculateDaysBetween(lastInterestDate, today)

        if (daysSinceLastInterest > 0) {
            // 获取该等级的利息率
            val interestRate = getInterestRate(guildData.level)
            if (interestRate == null || interestRate <= 0) {
                plugin.logger.fine("[Interest] 公会 ${guildData.name} (等级 ${guildData.level}) 无利息配置")
                return
            }

            // 复利计算：每天的利息都会累加到本金中
            val rateMultiplier = 1 + (interestRate / 100.0)
            val finalBalance = guildData.balance * rateMultiplier.pow(daysSinceLastInterest.toDouble())
            val totalInterest = ((finalBalance - guildData.balance) * 100).roundToInt() / 100.0

            if (totalInterest > 0) {
                // 更新公会余额
                val success = plugin.dbManager.updateGuildBalance(guildId, totalInterest)

                if (success) {
                    // 记录利息日志
                    plugin.dbManager.logBankTransaction(
                        guildId,
                        "系统",
                        "INTEREST",
                        totalInterest
                    )

                    // 更新计息日期
                    plugin.dbManager.updateLastInterestDate(guildId, today)

                }
            }
        }
    }

    /**
     * 计算两个时间戳之间的天数差异
     * @param lastTimestamp 上次时间戳
     * @param currentTimestamp 当前时间戳
     * @return 天数
     */
    private fun calculateDaysBetween(lastTimestamp: Long, currentTimestamp: Long): Int {
        if (lastTimestamp == 0L) return 1 // 首次计息，按1天计算

        val lastDate = java.time.LocalDate.ofEpochDay(lastTimestamp / 86400000)
        val currentDate = java.time.LocalDate.ofEpochDay(currentTimestamp / 86400000)

        return (currentDate.toEpochDay() - lastDate.toEpochDay()).toInt()
    }

    /**
     * 获取今日0点的时间戳
     * @return 时间戳
     */
    private fun getTodayDateLong(): Long {
        val now = java.time.LocalDateTime.now()
        val todayMidnight = now.toLocalDate().atStartOfDay()
        return java.time.ZonedDateTime.of(todayMidnight, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * 获取指定等级的银行利息率
     * @param level 公会等级
     * @return 利息率（百分比），如 0.5 表示 0.5%
     */
    private fun getInterestRate(level: Int): Double? {
        val levelSection = plugin.levelsConfig.getConfigurationSection("levels.$level")
        if (levelSection == null) {
            plugin.logger.warning("[Interest] 未找到等级 $level 的配置")
            return null
        }

        return levelSection.getDouble("bank-interest", 0.0)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player

        // 玩家退出时，清理内存，防止内存泄漏
        plugin.playerGuildCache.remove(player.uniqueId)

    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        // 只有当坐标（Block）发生变化时才取消，原地转头不取消
        val from = event.from
        val to = event.to
        if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
            val player = event.player
            if (plugin.guildService.isTeleporting(player.uniqueId)) {
                plugin.guildService.cancelTeleport(player.uniqueId)
                player.sendMessage(plugin.langManager.get("tp-cancelled-move"))
            }
        }
    }

    @EventHandler
    fun onPlayerDamage(event: EntityDamageEvent) {
        if (event.entity is Player) {
            val player = event.entity as Player
            if (plugin.guildService.isTeleporting(player.uniqueId)) {
                plugin.guildService.cancelTeleport(player.uniqueId)
                player.sendMessage(plugin.langManager.get("tp-cancelled-damage"))
            }
        }
    }
}
