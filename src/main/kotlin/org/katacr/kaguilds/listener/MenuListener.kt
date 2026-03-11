package org.katacr.kaguilds.listener

import net.md_5.bungee.api.chat.TextComponent
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
import org.katacr.kaguilds.util.MessageUtil
import java.util.UUID

class MenuListener(private val plugin: KaGuilds) : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val lang = plugin.langManager
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
        val actionsSection = getActionsSection(button) ?: return

        // 3. 状态拦截：检查金库解锁和升级解锁
        val vaultUnlockedKey = org.bukkit.NamespacedKey(plugin, "vault_unlocked")
        val upgradeStatusKey = org.bukkit.NamespacedKey(plugin, "upgrade_status_type")

        val vStatus = clickedItem.itemMeta?.persistentDataContainer?.get(vaultUnlockedKey, org.bukkit.persistence.PersistentDataType.INTEGER)
        val uStatus = clickedItem.itemMeta?.persistentDataContainer?.get(upgradeStatusKey, org.bukkit.persistence.PersistentDataType.INTEGER)

        // 如果金库未解锁 (0)
        if (vStatus != null && vStatus == 0) {
            player.sendMessage(lang.get("menu-vault-not-unlocked"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            return
        }

        // 如果升级等级处于 锁定(0) 或 经验不足(2) 状态
        if (uStatus != null) {
            when (uStatus) {
                0 -> {
                    player.sendMessage(lang.get("menu-upgrade-not-unlocked"))
                    return
                }
                2 -> {
                    player.sendMessage(lang.get("menu-upgrade-not-exp"))
                    return
                }
                3 -> {
                    player.sendMessage(lang.get("menu-upgrade-now-level"))
                    return
                }
            }
        }

        // 4. 识别点击类型并获取对应的 action 列表
        val clickTypeKey = when {
            event.click == org.bukkit.event.inventory.ClickType.DROP -> "drop"
            event.click.isLeftClick && event.isShiftClick -> "shift_left"
            event.click.isLeftClick -> "left"
            event.click.isRightClick -> "right"
            else -> return
        }
        val clickConfig = actionsSection.getList(clickTypeKey) ?: return

        // 5. 提取上下文变量 (PDC)
        val buffKeyNameKey = org.bukkit.NamespacedKey(plugin, "buff_keyname")
        val guildIdKey = org.bukkit.NamespacedKey(plugin, "guild_id")
        val memberUuidKey = org.bukkit.NamespacedKey(plugin, "member_uuid")
        val playerUuidKey = org.bukkit.NamespacedKey(plugin, "player_uuid")
        val vaultNumKey = org.bukkit.NamespacedKey(plugin, "vault_num")
        val upgradeLevelKey = org.bukkit.NamespacedKey(plugin, "upgrade_level_num")

        val clickedGuildId = clickedItem.itemMeta?.persistentDataContainer?.get(guildIdKey, org.bukkit.persistence.PersistentDataType.INTEGER)
        val clickedMemberUuid = clickedItem.itemMeta?.persistentDataContainer?.get(memberUuidKey, org.bukkit.persistence.PersistentDataType.STRING)
                ?: clickedItem.itemMeta?.persistentDataContainer?.get(playerUuidKey, org.bukkit.persistence.PersistentDataType.STRING)
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
                    val successActions = (group["actions"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val denyActions = (group["deny"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

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
            // 对于 ALL_PLAYER 菜单，也替换 {player_name}
            finalLine = finalLine.replace("{player_name}", memberName)
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
     * 解析并检查条件字符串 (支持 &&, || 复合条件)
     * 例如: "%player_level% >= 0 && %player_level% < 10"
     */
    private fun checkCondition(player: Player, condition: String?): Boolean {
        if (condition == null || condition.isBlank()) return true

        // 先进行 PAPI 变量替换
        var processed = condition
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            processed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, processed)
        }

        // 解析逻辑表达式（支持 && 和 ||）
        return parseLogicalExpression(processed)
    }

    /**
     * 递归解析逻辑表达式（支持 && 和 ||）
     * 优先级：&& 高于 ||
     */
    private fun parseLogicalExpression(expression: String): Boolean {
        val trimmed = expression.trim()

        // 1. 先检查是否包含 ||（优先级最低）
        val orParts = splitByOperator(trimmed, "||")
        if (orParts.size > 1) {
            // 如果有 ||，先解析第一部分，如果为 true 则直接返回 true（短路求值）
            val firstPart = orParts[0]
            val firstResult = parseLogicalExpression(firstPart)
            if (firstResult) return true // || 短路求值

            // 否则继续解析剩余部分
            val remaining = trimmed.substring(firstPart.length).trim().removePrefix("||").trim()
            return parseLogicalExpression(remaining)
        }

        // 2. 再检查是否包含 &&（优先级高于 ||）
        val andParts = splitByOperator(trimmed, "&&")
        if (andParts.size > 1) {
            // 如果有 &&，先解析第一部分，如果为 false 则直接返回 false（短路求值）
            val firstPart = andParts[0]
            val firstResult = parseLogicalExpression(firstPart)
            if (!firstResult) return false // && 短路求值

            // 否则继续解析剩余部分
            val remaining = trimmed.substring(firstPart.length).trim().removePrefix("&&").trim()
            return parseLogicalExpression(remaining)
        }

        // 3. 如果没有逻辑运算符，则为基本条件
        return evaluateSingleCondition(trimmed)
    }

    /**
     * 按运算符分割表达式（考虑括号和优先级）
     */
    private fun splitByOperator(expression: String, operator: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0
        var i = 0

        while (i < expression.length) {
            val char = expression[i]

            when {
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' -> {
                    parenDepth--
                    current.append(char)
                }
                parenDepth > 0 -> {
                    // 括号内不分割
                    current.append(char)
                }
                else -> {
                    // 检查是否匹配运算符
                    if (i + operator.length <= expression.length &&
                        expression.substring(i, i + operator.length) == operator) {
                        // 匹配到运算符
                        result.add(current.toString().trim())
                        current = StringBuilder()
                        i += operator.length - 1 // 跳过运算符
                    } else {
                        current.append(char)
                    }
                }
            }
            i++
        }

        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }

        return result
    }

    /**
     * 评估单个条件（如 "5 >= 3"）
     */
    private fun evaluateSingleCondition(condition: String): Boolean {
        val trimmed = condition.trim()

        // 如果是括号包裹的表达式，去掉括号后递归解析
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            // 确保括号是成对的
            var parenCount = 0
            var isCompletePair = true
            for (char in inner) {
                when (char) {
                    '(' -> parenCount++
                    ')' -> parenCount--
                }
                if (parenCount < 0) {
                    isCompletePair = false
                    break
                }
            }
            if (isCompletePair && parenCount == 0) {
                return parseLogicalExpression(inner)
            }
        }

        // 匹配比较运算符
        val regex = "(>=|<=|==|!=|>|<)".toRegex()
        val match = regex.find(trimmed) ?: return false

        val op = match.value
        val parts = trimmed.split(op, limit = 2)
        val left = parts[0].trim()
        val right = parts[1].trim()

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
    internal fun executeActionLine(player: Player, line: String) {
        if (line.isBlank()) return
        if (line == "PAGE_NEXT" || line == "PAGE_PREV") {
            val holder = player.openInventory.topInventory.holder as? GuildMenuHolder ?: return

            // 1. 获取当前菜单的总数量 (根据不同菜单类型动态计算)
            val totalCount = when {
                holder.menuName.contains("member", ignoreCase = true) -> {
                    val guildId = plugin.playerGuildCache[player.uniqueId] ?: 0
                    plugin.dbManager.getGuildMembers(guildId).size
                }
                holder.menuName.contains("player", ignoreCase = true) -> {
                    // ALL_PLAYER 菜单：获取全服在线玩家数量
                    val isProxy = plugin.config.getBoolean("proxy", false)
                    if (isProxy) plugin.crossServerOnlinePlayers.size else plugin.server.onlinePlayers.size
                }
                holder.menuName.contains("buff", ignoreCase = true) -> {
                    plugin.buffsConfig.getConfigurationSection("buffs")?.getKeys(false)?.size ?: 0
                }
                holder.menuName.contains("list", ignoreCase = true) -> {
                    plugin.dbManager.getGuildCount()
                }
                holder.menuName.contains("task", ignoreCase = true) -> {
                    // 判断是 daily 还是 global 任务
                    val hasGlobalTask = holder.buttons?.getKeys(false)?.any { key ->
                        holder.buttons.getConfigurationSection(key)?.getString("type") == "TASK_GLOBAL"
                    } == true
                    val taskType = if (hasGlobalTask) "global" else "daily"
                    plugin.taskManager.taskDefinitions.values.count { it.type == taskType }
                }
                else -> 0
            }

            // 2. 计算每页槽位数量 (通过布局中的特定标识符统计)
            val itemsPerPage = holder.layout.joinToString("").count { char ->
                val type = holder.buttons?.getConfigurationSection(char.toString())?.getString("type")
                type == "MEMBERS_LIST" || type == "GUILDS_LIST" || type == "BUFF_LIST" || type == "TASK_DAILY" || type == "TASK_GLOBAL" || type == "ALL_PLAYER"
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
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: 0

        // 统一处理颜色和通用内置变量
        val guildData = plugin.dbManager.getGuildData(guildId)

        // 获取当前等级的传送费用
        val tpMoney = if (guildData != null) {
            plugin.levelsConfig.getDouble("levels.${guildData.level}.tp-money", 0.0)
        } else {
            0
        }

        val args = org.bukkit.ChatColor.translateAlternateColorCodes('&', rawArgs
            .replace("{player}", player.name)
            .replace("{player_uuid}", player.uniqueId.toString())
            .replace("{guild_id}", guildId.toString())
            .replace("{guild_name}", guildData?.name ?: "N/A")
            .replace("{balance_rename}", plugin.config.getDouble("balance.rename", 3000.0).toString())
            .replace("{balance_settp}", plugin.config.getDouble("balance.settp", 1000.0).toString())
            .replace("{balance_seticon}", plugin.config.getDouble("balance.seticon", 1000.0).toString())
            .replace("{balance_setmotd}", plugin.config.getDouble("balance.setmotd", 100.0).toString())
            .replace("{balance_pvp}", plugin.config.getDouble("balance.pvp", 300.0).toString())
            .replace("{tp_money}", tpMoney.toString()))

        when (type) {
            "tell" -> player.sendMessage(args)
            "hovertext" -> {
                val message = parseClickableText(args)
                player.spigot().sendMessage(message)
            }
            "command" -> player.performCommand(args)
            "console" -> plugin.server.dispatchCommand(plugin.server.consoleSender, args)
            "sound" -> {
                val soundName = rawArgs.replace(".", "_").uppercase()
                try {
                    player.playSound(player.location, org.bukkit.Sound.valueOf(soundName), 1f, 1f)
                } catch (_: Exception) {
                    plugin.logger.warning("Unknown sound: $soundName")
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
            }
            "update" -> {
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    val currentHolder = player.openInventory.topInventory.holder as? GuildMenuHolder
                    if (currentHolder != null) {
                        plugin.menuManager.openMenu(player, currentHolder.menuName, currentHolder.currentPage)
                    }
                }, 1L)
            }
        }
    }

    private fun String.toDoubleDefault(default: Double): Double = this.toDoubleOrNull() ?: default

    /**
     * 解析包含可点击文本的消息
     * 格式: <text='显示文字';hover='悬停文字';command='指令';newline='false'>
     */
    private fun parseClickableText(rawText: String): TextComponent {
        val mainComponent = TextComponent()

        var currentPos = 0

        while (currentPos < rawText.length) {
            val startIndex = rawText.indexOf('<', currentPos)

            if (startIndex == -1) {
                // 没有更多可点击部分，添加剩余文本
                mainComponent.addExtra(MessageUtil.createText(rawText.substring(currentPos)))
                break
            }

            // 添加可点击部分之前的普通文本
            if (startIndex > currentPos) {
                mainComponent.addExtra(MessageUtil.createText(rawText.substring(currentPos, startIndex)))
            }

            // 解析可点击部分
            val endIndex = findClosingBracket(rawText, startIndex)
            if (endIndex == -1) {
                // 没有找到闭合的 >，将剩余部分作为普通文本
                mainComponent.addExtra(MessageUtil.createText(rawText.substring(startIndex)))
                break
            }

            val content = rawText.substring(startIndex + 1, endIndex)
            val clickablePart = parseClickableComponent(content)

            if (clickablePart != null) {
                mainComponent.addExtra(clickablePart)
            }

            currentPos = endIndex + 1
        }

        return mainComponent
    }

    /**
     * 查找匹配的闭合 > 符号
     */
    private fun findClosingBracket(text: String, startIndex: Int): Int {
        var depth = 1
        var i = startIndex + 1

        while (i < text.length && depth > 0) {
            when (text[i]) {
                '<' -> depth++
                '>' -> depth--
            }
            i++
        }

        return if (depth == 0) i - 1 else -1
    }

    /**
     * 解析可点击组件的内容
     * 格式: text=`xxx`;hover=`xxx`;command=`xxx`;newline=`false`
     */
    private fun parseClickableComponent(content: String): TextComponent? {
        var text = ""
        var hover = ""
        var command = ""
        var newline = false

        // 解析各个属性
        val parts = content.split(';')
        for (part in parts) {
            val trimmed = part.trim()
            val eqIndex = trimmed.indexOf('=')

            if (eqIndex != -1) {
                val key = trimmed.take(eqIndex).trim().lowercase()
                val value = trimmed.substring(eqIndex + 1).trim()

                when (key) {
                    "text" -> text = value.removeSurrounding("`")
                    "hover" -> hover = value.removeSurrounding("`")
                    "command" -> command = value.removeSurrounding("`")
                    "newline" -> newline = value.removeSurrounding("`").equals("true", ignoreCase = true)
                }
            }
        }

        return if (text.isNotEmpty()) {
            MessageUtil.createClickableText(text, hover, command, newline)
        } else {
            null
        }
    }

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

    }

    /**
     * 处理聊天捕获逻辑
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val lang = plugin.langManager
        val player = event.player
        // 尝试从缓存中获取当前玩家的捕获任务
        val type = chatCatchers[player.uniqueId] ?: return

        // 1. 拦截消息
        event.isCancelled = true
        chatCatchers.remove(player.uniqueId)

        val message = event.message.trim()

        // 允许玩家取消
        if (message.equals("cancel", ignoreCase = true)) {
            player.sendMessage(lang.get("menu-catcher-cancel"))
            return
        }

        // 2. 回到同步主线程执行指令
        plugin.server.scheduler.runTask(plugin, Runnable {
            when (type) {
                "bank_add" -> player.performCommand("kg bank add $message")
                "bank_take" -> player.performCommand("kg bank take $message")
                "guild_rename" -> player.performCommand("kg rename $message")
                "guild_create" -> player.performCommand("kg create $message")
                "edit_motd" -> player.performCommand("kg motd $message")
            }
        })
    }

    /**
     * 玩家退出时清理缓存，防止内存泄漏
     */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        chatCatchers.remove(event.player.uniqueId)
        // 退出公会聊天模式
        plugin.guildChatPlayers.remove(event.player.uniqueId)
    }

    /**
     * 处理公会聊天模式
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onGuildChat(event: AsyncPlayerChatEvent) {
        val player = event.player

        // 优先检查：如果玩家正在进行菜单输入捕获，不处理公会聊天
        // 菜单输入捕获优先级更高，因为这是用户主动触发的操作
        if (chatCatchers.containsKey(player.uniqueId)) {
            return
        }

        // 检查玩家是否在公会聊天模式
        if (!plugin.guildChatPlayers.contains(player.uniqueId)) {
            return
        }

        // 拦截消息，改为发送到公会频道
        event.isCancelled = true

        // 发送公会聊天消息
        plugin.guildService.sendGuildChat(player, event.message)
    }

    /**
     * 辅助方法：获取按钮操作配置（支持多种键名变体）
     */
    private fun getActionsSection(button: org.bukkit.configuration.ConfigurationSection): org.bukkit.configuration.ConfigurationSection? {
        val keys = listOf("actions", "Actions", "action", "Action", "ACTION")
        for (key in button.getKeys(false)) {
            if (keys.contains(key)) {
                return button.getConfigurationSection(key)
            }
        }
        return null
    }
}