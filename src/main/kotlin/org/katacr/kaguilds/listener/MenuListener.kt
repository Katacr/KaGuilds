package org.katacr.kaguilds.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.katacr.kaguilds.KaGuilds
import java.util.UUID

class MenuListener(private val plugin: KaGuilds) : Listener {

    @EventHandler

    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        if (slot < 0) return

        val holder = event.inventory.holder as? GuildMenuHolder ?: return
        if (slot >= event.inventory.size) return

        event.isCancelled = true

        // 定义 clickedItem
        val clickedItem = event.currentItem
        if (clickedItem == null || clickedItem.type == org.bukkit.Material.AIR) return

        val iconChar = holder.getIconChar(slot) ?: return
        val button = holder.buttons?.getConfigurationSection(iconChar) ?: return
        val actionsSection = button.getConfigurationSection("actions") ?: return

        // 获取点击类型
        val clickTypeKey = if (event.click.isLeftClick) "left" else if (event.click.isRightClick) "right" else return
        val clickConfig = actionsSection.getList(clickTypeKey) ?: return

        // 获取上下文 ID (公会 ID 或 成员 UUID)
        val guildIdKey = org.bukkit.NamespacedKey(plugin, "guild_id")
        val memberUuidKey = org.bukkit.NamespacedKey(plugin, "member_uuid")

        val clickedGuildId = clickedItem.itemMeta?.persistentDataContainer?.get(guildIdKey, org.bukkit.persistence.PersistentDataType.INTEGER)
        val clickedMemberUuid = clickedItem.itemMeta?.persistentDataContainer?.get(memberUuidKey, org.bukkit.persistence.PersistentDataType.STRING)

        // 遍历执行动作
        for (item in clickConfig) {
            when (item) {
                is String -> {
                    processAndExecute(player, item, clickedGuildId, clickedMemberUuid)
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val group = item as Map<String, Any>
                    val conditionStr = group["condition"] as? String
                    val successActions = group["actions"] as? List<String> ?: emptyList()
                    val denyActions = group["deny"] as? List<String> ?: emptyList()

                    if (checkCondition(player, conditionStr)) {
                        successActions.forEach { processAndExecute(player, it, clickedGuildId, clickedMemberUuid) }
                    } else {
                        denyActions.forEach { processAndExecute(player, it, clickedGuildId, clickedMemberUuid) }
                    }
                }
            }
        }
    }

    /**
     * 内部处理：替换变量并提交执行
     */
    private fun processAndExecute(player: Player, line: String, guildId: Int?, memberUuid: String?) {
        var finalLine = line

        // 1. 替换内置公会变量 {id}
        if (guildId != null) {
            finalLine = finalLine.replace("{id}", guildId.toString())
        }

        // 2. 替换内置成员变量 {members_name}
        if (memberUuid != null) {
            // 通过 UUID 获取离线玩家名
            val targetPlayer = Bukkit.getOfflinePlayer(UUID.fromString(memberUuid))
            val memberName = targetPlayer.name ?: "Unknown"
            finalLine = finalLine.replace("{members_name}", memberName)
        }

        // 3. 替换 PAPI 变量
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            finalLine = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, finalLine)
        }

        executeActionLine(player, finalLine)
    }
    /**
     * 解析并检查条件字符串 (例如 "%vault_eco_balance% >= 2000")
     */
    private fun checkCondition(player: Player, condition: String?): Boolean {
        if (condition == null || condition.isBlank()) return true

        // 1. 变量替换 (PAPI 变量在此处被解析为具体的数值或字符串)
        var processed = condition
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            processed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, processed)
        }

        // 2. 匹配操作符
        val regex = "(>=|<=|==|!=|>|<)".toRegex()
        val match = regex.find(processed) ?: return false

        val op = match.value
        // 使用 limit = 2 拆分，防止右侧内容包含相同字符导致解析错误
        val parts = processed.split(op, limit = 2)
        val left = parts[0].trim()
        val right = parts[1].trim()

        // 3. 逻辑判断
        return when (op) {
            "==" -> left.equals(right, ignoreCase = true)
            "!=" -> !left.equals(right, ignoreCase = true)
            ">"  -> left.toDoubleDefault(0.0) > right.toDoubleDefault(0.0)
            ">=" -> left.toDoubleDefault(0.0) >= right.toDoubleDefault(0.0)
            "<"  -> left.toDoubleDefault(0.0) < right.toDoubleDefault(0.0)
            "<=" -> left.toDoubleDefault(0.0) <= right.toDoubleDefault(0.0)
            else -> false
        }
    }
    private fun executeActionLine(player: Player, line: String) {
        if (line.isBlank()) return

        // 特殊逻辑：翻页
        if (line == "PAGE_NEXT" || line == "PAGE_PREV") {
            val holder = player.openInventory.topInventory.holder as? GuildMenuHolder ?: return
            val newPage = if (line == "PAGE_NEXT") holder.currentPage + 1 else holder.currentPage - 1
            if (newPage >= 0) {
                plugin.menuManager.openGuildListMenu(player, "guild_list", newPage)
            }
            return
        }

        val parts = line.split(":", limit = 2)
        val type = parts[0].trim().lowercase()
        val rawArgs = parts.getOrNull(1)?.trim() ?: ""

        // 统一处理颜色和通用变量 %player%
        val args = org.bukkit.ChatColor.translateAlternateColorCodes('&', rawArgs.replace("%player%", player.name))

        when (type) {
            "tell" -> player.sendMessage(args)
            "command" -> player.performCommand(rawArgs.replace("%player%", player.name))
            "console" -> plugin.server.dispatchCommand(plugin.server.consoleSender, rawArgs.replace("%player%", player.name))
            "sound" -> {
                val soundName = rawArgs.replace(".", "_").uppercase()
                try {
                    player.playSound(player.location, org.bukkit.Sound.valueOf(soundName), 1f, 1f)
                } catch (e: Exception) {
                    plugin.logger.warning("无效声音: $soundName")
                }
            }
            "open" -> {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.menuManager.openMenu(player, rawArgs)
                })
            }
            "close" -> player.closeInventory()
        }
    }

    private fun String.toDoubleDefault(default: Double): Double = this.toDoubleOrNull() ?: default

    private fun isRoleEnough(current: String, required: String): Boolean {
        val rolePower = mapOf("OWNER" to 3, "ADMIN" to 2, "MEMBER" to 1)
        return (rolePower[current.uppercase()] ?: 0) >= (rolePower[required.uppercase()] ?: 1)
    }
}