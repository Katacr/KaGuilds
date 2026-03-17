package org.katacr.kaguilds

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer


class KaGuildsExpansion(private val plugin: KaGuilds) : PlaceholderExpansion() {

    // 变量的前缀，即 %kaguilds_xxx% 中的 kaguilds
    override fun getIdentifier(): String = "kaguilds"

    override fun getAuthor(): String = plugin.description.authors.toString()

    override fun getVersion(): String = plugin.description.version

    // 必须返回 true，PAPI 才会加载这个扩展
    override fun persist(): Boolean = true

    // 核心逻辑：处理变量请求
    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return ""

        val lang = plugin.langManager

        // 特殊变量：检查玩家是否已加入公会
        if (params == "has_guild") {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
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

        // 1. 获取该玩家所属的公会 ID
        val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
            ?: return lang.get("papi-no-guild")

        // 2. 根据 ID 获取公会对象
        val guild = plugin.dbManager.getGuildById(guildId)
            ?: return lang.get("papi-error")


        return when (params.lowercase()) {
            "name" -> guild.name
            "id" -> guild.id.toString()
            "level" -> guild.level.toString()
            "owner" -> guild.ownerName
            "balance" -> guild.balance.toString()
            "motd" -> guild.announcement
            "member_count" -> plugin.dbManager.getMemberCount(guildId).toString()
            "max_members" -> guild.maxMembers.toString()
            "exp" -> guild.exp.toString()
            "contribution" -> plugin.dbManager.getPlayerContribution(player.uniqueId).toString()
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
            "pending_requests" -> plugin.dbManager.getRequests(guildId).size.toString()
            "pvp_wins" -> guild.pvpWins.toString()
            "pvp_losses" -> guild.pvpLosses.toString()
            "pvp_draws" -> guild.pvpDraws.toString()
            "pvp_total" -> guild.pvpTotal.toString()
            "is_admin" -> {
                when (plugin.dbManager.getPlayerRole(player.uniqueId)) {
                    "ADMIN" -> lang.get("papi-boolean-true")
                    else -> lang.get("papi-boolean-false")
                }
            }
            "is_owner" -> {
                when (plugin.dbManager.getPlayerRole(player.uniqueId)) {
                    "OWNER" -> lang.get("papi-boolean-true")
                    else -> lang.get("papi-boolean-false")
                }
            }
            "is_staff" -> {
                if (plugin.dbManager.isStaff(player.uniqueId, guildId))
                    lang.get("papi-boolean-true")
                else
                    lang.get("papi-boolean-false")
            }
            "role_name" -> {
                when (plugin.dbManager.getPlayerRole(player.uniqueId)) {
                    "OWNER" -> lang.get("papi-role-owner")
                    "ADMIN" -> lang.get("papi-role-admin")
                    "MEMBER" -> lang.get("papi-role-member")
                    else -> lang.get("papi-role-none")
                }
            }
            "member_list" -> {
                val memberList = plugin.dbManager.getMemberNames(guildId)
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