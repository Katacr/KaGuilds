package org.katacr.kaguilds

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer


class KaGuildsExpansion(private val plugin: KaGuilds) : PlaceholderExpansion() {

    // 短 TTL 缓存：PAPI 常被计分板/tab 高频刷新，避免每次请求都查库
    private val ttlMs = 3_000L
    private data class CacheEntry<T>(val value: T, val expireAt: Long)
    private val guildCache = java.util.concurrent.ConcurrentHashMap<Int, CacheEntry<org.katacr.kaguilds.model.GuildData?>>()
    private val memberCountCache = java.util.concurrent.ConcurrentHashMap<Int, CacheEntry<Int>>()

    private fun cachedGuild(guildId: Int): org.katacr.kaguilds.model.GuildData? {
        val now = System.currentTimeMillis()
        guildCache[guildId]?.let { if (it.expireAt > now) return it.value }
        val fresh = plugin.dbManager.guildRepository.getGuildById(guildId)
        guildCache[guildId] = CacheEntry(fresh, now + ttlMs)
        return fresh
    }

    private fun cachedMemberCount(guildId: Int): Int {
        val now = System.currentTimeMillis()
        memberCountCache[guildId]?.let { if (it.expireAt > now) return it.value }
        val fresh = plugin.dbManager.memberRepository.getMemberCount(guildId)
        memberCountCache[guildId] = CacheEntry(fresh, now + ttlMs)
        return fresh
    }

    // 变量的前缀，即 %kaguilds_xxx% 中的 kaguilds
    override fun getIdentifier(): String = "kaguilds"

    override fun getAuthor(): String = plugin.description.authors.toString()

    override fun getVersion(): String = plugin.description.version

    // 必须返回 true，PAPI 才会加载这个扩展
    override fun persist(): Boolean = true

    // 核心逻辑：处理变量请求
    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return ""
        return try {
            onRequestInternal(player, params)
        } catch (e: Exception) {
            plugin.logger.warning("处理占位符 $params 失败: ${e.message}")
            null
        }
    }

    private fun onRequestInternal(player: OfflinePlayer, params: String): String? {
        val lang = plugin.langManager

        // 特殊变量：检查玩家是否已加入公会
        if (params == "has_guild") {
            val guildId = plugin.membershipCache.getGuildId(player.uniqueId)
            return if (guildId != null) {
                lang.get("papi-boolean-true")
            } else {
                lang.get("papi-boolean-false")
            }
        }

        // 特殊变量：每日任务重置倒计时
        if (params == "reset_countdown") {
            return getResetCountdown()
        }

        // 排行榜变量：%kaguilds_top_wins_N%、%kaguilds_top_contribution_N%
        val topWinsMatch = Regex("^top_wins_(\\d+)$").matchEntire(params)
        if (topWinsMatch != null) {
            val rank = topWinsMatch.groupValues[1].toIntOrNull() ?: return ""
            val guild = plugin.dbManager.guildRepository.getTopGuildByWins(rank) ?: return lang.get("papi-no-data")
            return "$rank. ${guild.name} ${guild.ownerName ?: "?"} ${guild.pvpWins}"
        }

        val topContributionMatch = Regex("^top_contribution_(\\d+)$").matchEntire(params)
        if (topContributionMatch != null) {
            val rank = topContributionMatch.groupValues[1].toIntOrNull() ?: return ""
            val guild = plugin.dbManager.guildRepository.getTopGuildByContribution(rank) ?: return lang.get("papi-no-data")
            return "$rank. ${guild.name} ${guild.ownerName ?: "?"} ${guild.totalContribution}"
        }

        // 1. 获取该玩家所属的公会 ID（走身份缓存，带查库兜底）
        val guildId = plugin.membershipCache.getGuildId(player.uniqueId)
            ?: return lang.get("papi-no-guild")

        // 2. 根据 ID 获取公会对象（短 TTL 缓存）
        val guild = cachedGuild(guildId)
            ?: return lang.get("papi-error")


        return when (params.lowercase()) {
            "name" -> guild.name
            "id" -> guild.id.toString()
            "level" -> guild.level.toString()
            "owner" -> guild.ownerName
            "balance" -> guild.balance.toString()
            "motd" -> guild.announcement
            "member_count" -> cachedMemberCount(guildId).toString()
            "max_members" -> guild.maxMembers.toString()
            "exp" -> guild.exp.toString()
            "contribution" -> plugin.dbManager.memberRepository.getPlayerContribution(player.uniqueId).toString()
            "guild_contribution" -> guild.totalContribution.toString()
            "need_exp" -> {
                // 获取下一级所需经验
                val nextLevel = guild.level + 1
                val needExp = plugin.levelsConfig.getInt("levels.$nextLevel.need-exp", 0)
                if (needExp == 0) "0" else needExp.toString()
            }
            "create_time" -> {
                // 格式化创建时间
                val formatPattern = plugin.config.getString("date-format", "yyyy-MM-dd HH:mm:ss") ?: "yyyy-MM-dd HH:mm:ss"
                java.text.SimpleDateFormat(formatPattern).format(java.util.Date(guild.createTime))
            }
            "pending_requests" -> plugin.dbManager.requestRepository.getRequests(guildId).size.toString()
            "pvp_wins" -> guild.pvpWins.toString()
            "pvp_losses" -> guild.pvpLosses.toString()
            "pvp_draws" -> guild.pvpDraws.toString()
            "pvp_total" -> guild.pvpTotal.toString()
            "is_admin" -> {
                when (plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)) {
                    "ADMIN" -> lang.get("papi-boolean-true")
                    else -> lang.get("papi-boolean-false")
                }
            }
            "is_owner" -> {
                when (plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)) {
                    "OWNER" -> lang.get("papi-boolean-true")
                    else -> lang.get("papi-boolean-false")
                }
            }
            "is_staff" -> {
                if (plugin.dbManager.memberRepository.isStaff(player.uniqueId, guildId))
                    lang.get("papi-boolean-true")
                else
                    lang.get("papi-boolean-false")
            }
            "role_name" -> {
                when (plugin.dbManager.memberRepository.getPlayerRole(player.uniqueId)) {
                    "OWNER" -> lang.get("papi-role-owner")
                    "ADMIN" -> lang.get("papi-role-admin")
                    "MEMBER" -> lang.get("papi-role-member")
                    else -> lang.get("papi-role-none")
                }
            }
            "member_list" -> {
                val memberList = plugin.dbManager.memberRepository.getMemberNames(guildId)
                if (memberList.isEmpty()) {
                    lang.get("papi-no-member")
                } else {
                    // 注意：成员列表通常不需要翻译玩家名，只需要翻译连接符或空状态
                    memberList.joinToString(", ")
                }
            }

            else -> null
        }
    }

    /**
     * 计算到每日任务重置时间的倒计时
     * @return 格式化的倒计时字符串（例如：5小时30分钟20秒）
     */
    private fun getResetCountdown(): String {
        val lang = plugin.langManager
        try {
            // 从配置中获取重置时间（格式：HH:mm:ss）
            val resetTimeStr = plugin.config.getString("task.reset_time", "00:00:00") ?: "00:00:00"
            val parts = resetTimeStr.split(":")
            if (parts.size != 3) return lang.get("papi-error")

            val resetHour = parts[0].toIntOrNull() ?: 0
            val resetMinute = parts[1].toIntOrNull() ?: 0
            val resetSecond = parts[2].toIntOrNull() ?: 0

            // 获取当前时间
            val calendar = java.util.Calendar.getInstance()
            val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(java.util.Calendar.MINUTE)
            val currentSecond = calendar.get(java.util.Calendar.SECOND)

            // 计算当前时间距离重置时间的秒数
            val currentTotalSeconds = currentHour * 3600 + currentMinute * 60 + currentSecond
            val resetTotalSeconds = resetHour * 3600 + resetMinute * 60 + resetSecond

            val remainingSeconds = if (resetTotalSeconds > currentTotalSeconds) {
                // 今天还未到重置时间
                resetTotalSeconds - currentTotalSeconds
            } else {
                // 今天已过重置时间，计算到明天的重置时间
                (24 * 3600) - currentTotalSeconds + resetTotalSeconds
            }

            // 计算小时、分钟、秒
            val hours = remainingSeconds / 3600
            val minutes = (remainingSeconds % 3600) / 60
            val seconds = remainingSeconds % 60

            // 获取格式化字符串并替换变量
            val formatPattern = lang.get("papi-reset-countdown")
            return formatPattern
                .replace("%h%", hours.toString())
                .replace("%m%", minutes.toString())
                .replace("%s%", seconds.toString())

        } catch (e: Exception) {
            plugin.logger.warning("Failed to calculate reset countdown: ${e.message}")
            return lang.get("papi-error")
        }
    }
}