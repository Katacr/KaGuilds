package org.katacr.kaguilds.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.katacr.kaguilds.KaGuilds
import java.util.UUID

class MenuListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        // 1. 识别是否为本插件菜单
        val holder = event.inventory.holder as? GuildMenuHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot

        // 基础边界检查
        if (slot < 0 || slot >= event.inventory.size) return
        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == org.bukkit.Material.AIR) return

        // 2. 获取按钮配置
        val iconChar = holder.getIconChar(slot) ?: return
        val button = holder.buttons?.getConfigurationSection(iconChar) ?: return
        val actionsSection = button.getConfigurationSection("actions") ?: return

        // 3. 状态拦截：检查金库解锁和升级解锁
        val vaultUnlockedKey = org.bukkit.NamespacedKey(plugin, "vault_unlocked")
        val upgradeStatusKey = org.bukkit.NamespacedKey(plugin, "upgrade_status_type")

        val vStatus = clickedItem.itemMeta?.persistentDataContainer?.get(vaultUnlockedKey, org.bukkit.persistence.PersistentDataType.INTEGER)
        val uStatus = clickedItem.itemMeta?.persistentDataContainer?.get(upgradeStatusKey, org.bukkit.persistence.PersistentDataType.INTEGER)

        // 如果金库未解锁 (0)
        if (vStatus != null && vStatus == 0) {
            player.sendMessage("§c§l! §7该公会金库尚未解锁，请先提升公会等级。")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            return
        }

        // 如果升级等级处于 锁定(0) 或 经验不足(2) 状态
        if (uStatus != null) {
            if (uStatus == 0) {
                player.sendMessage("§c§l! §7请按顺序升级公会等级。")
                return
            } else if (uStatus == 2) {
                player.sendMessage("§c§l! §7公会经验不足，无法升级。")
                return
            } else if (uStatus == 3) {
                player.sendMessage("§a§l! §7公会已达到该等级。")
                return
            }
        }

        // 4. 识别点击类型并获取对应的 action 列表
        val clickTypeKey = if (event.click.isLeftClick) "left" else if (event.click.isRightClick) "right" else return
        val clickConfig = actionsSection.getList(clickTypeKey) ?: return

        // 5. 提取上下文变量 (PDC)
        val buffKeyNameKey = org.bukkit.NamespacedKey(plugin, "buff_keyname")
        val guildIdKey = org.bukkit.NamespacedKey(plugin, "guild_id")
        val memberUuidKey = org.bukkit.NamespacedKey(plugin, "member_uuid")
        val vaultNumKey = org.bukkit.NamespacedKey(plugin, "vault_num")
        val upgradeLevelKey = org.bukkit.NamespacedKey(plugin, "upgrade_level_num")

        val clickedGuildId = clickedItem.itemMeta?.persistentDataContainer?.get(guildIdKey, org.bukkit.persistence.PersistentDataType.INTEGER)
        val clickedMemberUuid = clickedItem.itemMeta?.persistentDataContainer?.get(memberUuidKey, org.bukkit.persistence.PersistentDataType.STRING)
        val clickedBuffKey = clickedItem.itemMeta?.persistentDataContainer?.get(buffKeyNameKey, org.bukkit.persistence.PersistentDataType.STRING)
        val clickedVaultNum = clickedItem.itemMeta?.persistentDataContainer?.get(vaultNumKey, org.bukkit.persistence.PersistentDataType.INTEGER)
        val clickedUpgradeLevel = clickedItem.itemMeta?.persistentDataContainer?.get(upgradeLevelKey, org.bukkit.persistence.PersistentDataType.INTEGER)

        // 6. 遍历并执行动作
        for (item in clickConfig) {
            when (item) {
                is String -> {
                    var finalLine = item
                    // 变量替换
                    if (clickedVaultNum != null) finalLine = finalLine.replace("{vault_num}", clickedVaultNum.toString())
                    if (clickedUpgradeLevel != null) finalLine = finalLine.replace("{upgrade_level}", clickedUpgradeLevel.toString())

                    processAndExecute(player, finalLine, clickedGuildId, clickedMemberUuid, clickedBuffKey)
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val group = item as Map<String, Any>
                    val conditionStr = group["condition"] as? String
                    val successActions = group["actions"] as? List<String> ?: emptyList()
                    val denyActions = group["deny"] as? List<String> ?: emptyList()

                    if (checkCondition(player, conditionStr)) {
                        successActions.forEach { action ->
                            var finalAction = action
                            if (clickedVaultNum != null) finalAction = finalAction.replace("{vault_num}", clickedVaultNum.toString())
                            if (clickedUpgradeLevel != null) finalAction = finalAction.replace("{upgrade_level}", clickedUpgradeLevel.toString())
                            processAndExecute(player, finalAction, clickedGuildId, clickedMemberUuid, clickedBuffKey)
                        }
                    } else {
                        denyActions.forEach { action ->
                            var finalAction = action
                            if (clickedVaultNum != null) finalAction = finalAction.replace("{vault_num}", clickedVaultNum.toString())
                            if (clickedUpgradeLevel != null) finalAction = finalAction.replace("{upgrade_level}", clickedUpgradeLevel.toString())
                            processAndExecute(player, finalAction, clickedGuildId, clickedMemberUuid, clickedBuffKey)
                        }
                    }
                }
            }
        }
    }

    /**
     * 内部处理：替换变量并提交执行
     */
    private fun processAndExecute(player: Player, line: String, guildId: Int?, memberUuid: String?, buffKey: String?) {
        var finalLine = line

        // 1. 处理公会相关内部变量 {id}, {name}, {balance} 等
        if (guildId != null) {
            val guildData = plugin.dbManager.getGuildData(guildId)
            if (guildData != null) {
                // 调用 MenuManager 的方法获取所有变量 Map
                val placeholders = plugin.menuManager.getGuildPlaceholders(guildData, player)
                placeholders.forEach { (key, value) ->
                    finalLine = finalLine.replace("{$key}", value)
                }
            } else {
                // 如果拿不到详细数据，至少把 ID 替换了
                finalLine = finalLine.replace("{id}", guildId.toString())
            }
        }

        // 2. 处理成员相关内部变量 {members_name}
        if (memberUuid != null) {
            val targetPlayer = Bukkit.getOfflinePlayer(UUID.fromString(memberUuid))
            val memberName = targetPlayer.name ?: "Unknown"
            finalLine = finalLine.replace("{members_name}", memberName)
        }

        // 3. 处理 Buff 相关内部变量 {buff_keyname}
        if (buffKey != null) {
            finalLine = finalLine.replace("{buff_keyname}", buffKey)
        }

        // 4. 替换 PAPI 变量 (%% 格式)
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            finalLine = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, finalLine)
        }

        // 5. 执行最终动作
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

    /**
     * 执行动作行
     */
    private fun executeActionLine(player: Player, line: String) {
        if (line.isBlank()) return
        if (line == "PAGE_NEXT" || line == "PAGE_PREV") {
            val holder = player.openInventory.topInventory.holder as? GuildMenuHolder ?: return

            // 1. 获取当前菜单的总数量 (根据不同菜单类型动态计算)
            val totalCount = when {
                holder.menuName.contains("member", ignoreCase = true) -> {
                    val guildId = plugin.playerGuildCache[player.uniqueId] ?: 0
                    plugin.dbManager.getGuildMembers(guildId).size
                }
                holder.menuName.contains("buff", ignoreCase = true) -> {
                    plugin.config.getConfigurationSection("guild.buffs")?.getKeys(false)?.size ?: 0
                }
                holder.menuName.contains("list", ignoreCase = true) -> {
                    plugin.dbManager.getGuildCount()
                }
                else -> 0
            }

            // 2. 计算每页槽位数量 (通过布局中的特定标识符统计)
            val itemsPerPage = holder.layout.joinToString("").count { char ->
                val type = holder.buttons?.getConfigurationSection(char.toString())?.getString("type")
                type == "MEMBERS_LIST" || type == "GUILDS_LIST" || type == "BUFF_LIST"
            }.coerceAtLeast(1)

            val maxPages = kotlin.math.ceil(totalCount.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)

            // 3. 执行带边界检查的翻页
            val newPage = if (line == "PAGE_NEXT") holder.currentPage + 1 else holder.currentPage - 1

            // 确保 0 <= newPage < maxPages
            if (newPage in 0 until maxPages) {
                plugin.menuManager.openMenu(player, holder.menuName, newPage)
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
            "catcher" -> {
                chatCatchers[player.uniqueId] = rawArgs.lowercase()
                // 捕获器触发后通常会自动执行 close 逻辑，
                // 但为了保险，可以在这里直接调用 closeInventory()
                player.closeInventory()
            }
        }
    }

    private fun String.toDoubleDefault(default: Double): Double = this.toDoubleOrNull() ?: default

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? GuildMenuHolder ?: return
        holder.stopUpdate() // 停止该玩家的刷新任务
    }

    /**
     * 禁止拖拽
     */
    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        // 如果是我们的菜单，直接禁止拖拽任何东西
        if (event.inventory.holder is GuildMenuHolder) {
            event.isCancelled = true
        }
    }

    companion object {
        private val chatCatchers = mutableMapOf<UUID, String>()

        /**
         * 提供给外部（如 MenuManager 或其他类）清理数据的公开方法
         */
        fun clearCatcher(uuid: UUID) {
            chatCatchers.remove(uuid)
        }
    }

    /**
     * 处理聊天捕获逻辑
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        // 尝试从缓存中获取当前玩家的捕获任务
        val type = chatCatchers[player.uniqueId] ?: return

        // 1. 拦截消息
        event.isCancelled = true
        chatCatchers.remove(player.uniqueId)

        val message = event.message.trim()

        // 允许玩家取消
        if (message.equals("cancel", ignoreCase = true)) {
            player.sendMessage("§e[!] 已取消输入。")
            return
        }

        // 2. 验证是否为数字 (bank_add/get 专用)
        val amount = message.toLongOrNull()
        if (amount == null || amount <= 0) {
            player.sendMessage("§c[!] 错误：请输入有效的正整数金额。")
            return
        }

        // 3. 回到同步主线程执行指令
        plugin.server.scheduler.runTask(plugin, Runnable {
            when (type) {
                "bank_add" -> player.performCommand("kg bank add $amount")
                "bank_get" -> player.performCommand("kg bank get $amount")
            }
        })
    }

    /**
     * 玩家退出时清理缓存，防止内存泄漏
     */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        chatCatchers.remove(event.player.uniqueId)
    }
}