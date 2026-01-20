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
            "announcement" -> guild.announcement
            "member_count" -> plugin.dbManager.getMemberCount(guildId).toString()
            "max_members" -> guild.maxMembers.toString()

            "role_name" -> {
                when (plugin.dbManager.getPlayerRole(player.uniqueId)) {
                    "OWNER" -> lang.get("papi-role-owner")
                    "ADMIN" -> lang.get("papi-role-admin")
                    "MEMBER" -> lang.get("papi-role-member")
                    else -> lang.get("papi-role-none")
                }
            }

            "members" -> {
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
}