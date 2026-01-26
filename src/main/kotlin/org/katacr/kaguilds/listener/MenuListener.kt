package org.katacr.kaguilds.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.katacr.kaguilds.KaGuilds

class MenuListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        // 1. 基础校验：只处理属于我们插件的菜单
        val holder = event.inventory.holder as? GuildMenuHolder ?: return
        event.isCancelled = true // 锁定菜单，防止玩家拿走物品

        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot

        // 获取点击位置对应的字符标识 (如 '#', '1', 'B')
        val iconChar = holder.getIconChar(slot) ?: return

        // 获取该按钮的配置小节
        val button = holder.buttons?.getConfigurationSection(iconChar) ?: return
        val actionsSection = button.getConfigurationSection("actions") ?: return

        // 2. 识别点击类型并获取对应的动作列表 (left 或 right)
        val clickTypeKey = when {
            event.click.isLeftClick -> "left"
            event.click.isRightClick -> "right"
            else -> return
        }

        // 获取该点击下的内容列表
        val clickConfig = actionsSection.getList(clickTypeKey) ?: return

        // 3. 准备数据上下文（如果是 GUILDS_LIST 类型的按钮，从 NBT 获取 ID）
        val clickedItem = event.currentItem
        if (clickedItem == null || clickedItem.type == org.bukkit.Material.AIR) return
        if (button.getString("type") == "GUILDS_LIST") {
            val guildIdKey = org.bukkit.NamespacedKey(plugin, "guild_id")
            val hasId = clickedItem.itemMeta?.persistentDataContainer?.has(guildIdKey, org.bukkit.persistence.PersistentDataType.INTEGER) == true

            // 如果这个按钮配置为公会列表项，但物品上没有绑定 ID，说明是“多余的格子”，不执行动作
            if (!hasId) return
        }
        val guildIdKey = org.bukkit.NamespacedKey(plugin, "guild_id")
        val clickedGuildId = clickedItem.itemMeta?.persistentDataContainer?.get(guildIdKey, org.bukkit.persistence.PersistentDataType.INTEGER)

        // 4. 遍历并执行动作列表
        for (item in clickConfig) {
            when (item) {
                // 情况 A: 这是一个简单的字符串指令 (如 - 'command: kg info')
                is String -> {
                    var finalLine = item
                    if (clickedGuildId != null) {
                        finalLine = finalLine.replace("%guilds_id%", clickedGuildId.toString())
                    }
                    executeActionLine(player, finalLine)
                }

                // 情况 B: 这是一个复杂的逻辑组 (带 condition, actions, deny)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val group = item as Map<String, Any>
                    val conditionStr = group["condition"] as? String
                    val successActions = group["actions"] as? List<String> ?: emptyList()
                    val denyActions = group["deny"] as? List<String> ?: emptyList()

                    // 执行条件判定
                    if (checkCondition(player, conditionStr)) {
                        // 成功：执行 actions 列表
                        successActions.forEach { line ->
                            var finalLine = line
                            if (clickedGuildId != null) {
                                finalLine = finalLine.replace("%guilds_id%", clickedGuildId.toString())
                            }
                            executeActionLine(player, finalLine)
                        }
                    } else {
                        // 失败：执行 deny 列表
                        denyActions.forEach { line ->
                            var finalLine = line
                            if (clickedGuildId != null) {
                                finalLine = finalLine.replace("%guilds_id%", clickedGuildId.toString())
                            }
                            executeActionLine(player, finalLine)
                        }
                    }
                }
            }
        }
    }

    /**
     * 解析并检查条件字符串 (例如 "has_money: 2000")
     */
    private fun checkCondition(player: Player, condition: String?): Boolean {
        if (condition == null || condition.isBlank()) return true

        val parts = condition.split(":", limit = 2)
        val type = parts[0].trim().lowercase()
        val value = parts.getOrNull(1)?.trim() ?: ""

        return when (type) {
            "has_money" -> (plugin.economy?.getBalance(player) ?: 0.0) >= value.toDoubleDefault(0.0)
            "has_status" -> {
                // 使用 ?: "MEMBER" (或者 "NONE") 来处理空值情况
                val currentRole = plugin.dbManager.getPlayerRole(player.uniqueId) ?: "MEMBER"
                isRoleEnough(currentRole, value)
            }
            "has_guild" -> {
                // 新增一个检查：是否拥有公会
                plugin.playerGuildCache.containsKey(player.uniqueId)
            }
            else -> true
        }
    }

    /**
     * 解析并执行单行动作字符串 (例如 "tell: &a消息")
     */
    private fun executeActionLine(player: Player, line: String) {
        if (line.isBlank()) return

        // 处理特殊的翻页逻辑 (不带冒号)
        if (line == "PAGE_NEXT" || line == "PAGE_PREV") {
            val holder = player.openInventory.topInventory.holder as? GuildMenuHolder ?: return
            val newPage = if (line == "PAGE_NEXT") holder.currentPage + 1 else holder.currentPage - 1
            if (newPage >= 0) {
                // 重新打开公会列表菜单并传入新页码
                plugin.menuManager.openGuildListMenu(player, "guild_list", newPage)
            }
            return
        }

        // 处理带冒号的标准动作 (如 tell: xxxx)
        val parts = line.split(":", limit = 2)
        val type = parts[0].trim().lowercase()
        val rawArgs = parts.getOrNull(1)?.trim() ?: ""

        // 替换玩家名和颜色
        val args = org.bukkit.ChatColor.translateAlternateColorCodes('&', rawArgs.replace("%player%", player.name))

        when (type) {
            "tell" -> player.sendMessage(args)
            "command" -> player.performCommand(rawArgs.replace("%player%", player.name))
            "console" -> plugin.server.dispatchCommand(plugin.server.consoleSender, rawArgs.replace("%player%", player.name))
            "sound" -> {
                val soundKey = org.bukkit.NamespacedKey.minecraft(rawArgs.lowercase())
                val sound = org.bukkit.Registry.SOUNDS.get(soundKey)
                if (sound != null) player.playSound(player.location, sound, 1f, 1f)
            }
            "open" -> {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.menuManager.openMenu(player, rawArgs)
                })
            }
            "close" -> player.closeInventory()
        }
    }

    // 辅助扩展：字符串转 Double 防止崩溃
    private fun String.toDoubleDefault(default: Double): Double = this.toDoubleOrNull() ?: default

    /**
     * 检查玩家职位是否达到或超过要求职位
     * OWNER(3) > ADMIN(2) > MEMBER(1)
     */
    private fun isRoleEnough(current: String, required: String): Boolean {
        val rolePower = mapOf("OWNER" to 3, "ADMIN" to 2, "MEMBER" to 1)
        val currentPower = rolePower[current.uppercase()] ?: 0
        val requiredPower = rolePower[required.uppercase()] ?: 1
        return currentPower >= requiredPower
    }
}