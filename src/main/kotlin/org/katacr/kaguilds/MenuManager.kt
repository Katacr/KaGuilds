package org.katacr.kaguilds

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import org.katacr.kaguilds.listener.GuildMenuHolder
import org.katacr.kaguilds.service.TaskManager
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil


class MenuManager(private val plugin: KaGuilds) {
    private val menuCache = mutableMapOf<String, YamlConfiguration>()

    // 默认图标配置
    private val menuDefaultIcons = mutableMapOf<String, MenuDefaultIcon>()

    init {
        loadMenuDefaultIcons()
    }

    /**
     * 加载菜单默认图标配置
     */
    private fun loadMenuDefaultIcons() {
        val config = plugin.config.getConfigurationSection("menu-default-icon")
        if (config == null) {
            plugin.logger.warning("menu-default-icon 配置未找到，使用默认图标")
            return
        }

        // 加载 tasks 配置
        val tasksSection = config.getConfigurationSection("tasks")
        if (tasksSection != null) {
            menuDefaultIcons["tasks_unfinished"] = MenuDefaultIcon(
                tasksSection.getString("unfinished.material") ?: "BOOK",
                tasksSection.getInt("unfinished.custom_data", 0),
                tasksSection.getString("unfinished.item_model")
            )
            menuDefaultIcons["tasks_finished"] = MenuDefaultIcon(
                tasksSection.getString("finished.material") ?: "ENCHANTED_BOOK",
                tasksSection.getInt("finished.custom_data", 0),
                tasksSection.getString("finished.item_model")
            )
        }

        // 加载 buffs 配置
        val buffsSection = config.getConfigurationSection("buffs")
        if (buffsSection != null) {
            menuDefaultIcons["buffs_lock"] = MenuDefaultIcon(
                buffsSection.getString("lock.material") ?: "PAPER",
                buffsSection.getInt("lock.custom_data", 0),
                buffsSection.getString("lock.item_model")
            )
            menuDefaultIcons["buffs_unlock"] = MenuDefaultIcon(
                buffsSection.getString("unlock.material") ?: "PAPER",
                buffsSection.getInt("unlock.custom_data", 0),
                buffsSection.getString("unlock.item_model")
            )
        }

        // 加载 levels 配置
        val levelsSection = config.getConfigurationSection("levels")
        if (levelsSection != null) {
            menuDefaultIcons["levels_lock"] = MenuDefaultIcon(
                levelsSection.getString("lock.material") ?: "PAPER",
                levelsSection.getInt("lock.custom_data", 0),
                levelsSection.getString("lock.item_model")
            )
            menuDefaultIcons["levels_unlock"] = MenuDefaultIcon(
                levelsSection.getString("unlock.material") ?: "PAPER",
                levelsSection.getInt("unlock.custom_data", 0),
                levelsSection.getString("unlock.item_model")
            )
        }

        // 加载 vaults 配置
        val vaultsSection = config.getConfigurationSection("vaults")
        if (vaultsSection != null) {
            menuDefaultIcons["vaults_lock"] = MenuDefaultIcon(
                vaultsSection.getString("lock.material") ?: "PAPER",
                vaultsSection.getInt("lock.custom_data", 0),
                vaultsSection.getString("lock.item_model")
            )
            menuDefaultIcons["vaults_unlock"] = MenuDefaultIcon(
                vaultsSection.getString("unlock.material") ?: "PAPER",
                vaultsSection.getInt("unlock.custom_data", 0),
                vaultsSection.getString("unlock.item_model")
            )
        }
    }

    /**
     * 菜单默认图标数据类
     */
    data class MenuDefaultIcon(
        val material: String,
        val customData: Int = 0,
        val itemModel: String? = null
    )

    /**
     * 辅助方法：获取布局配置（支持多种键名变体）
     */
    private fun getLayout(config: ConfigurationSection): List<String> {
        val layoutPattern = Regex("(?i)^layouts?$")
        for (key in config.getKeys(false)) {
            if (layoutPattern.matches(key)) {
                return config.getStringList(key)
            }
        }
        return emptyList()
    }

    /**
     * 辅助方法：获取按钮配置（支持多种键名变体）
     */
    private fun getButtonsSection(config: ConfigurationSection): ConfigurationSection? {
        val buttonPattern = Regex("(?i)^buttons?$")
        for (key in config.getKeys(false)) {
            if (buttonPattern.matches(key)) {
                return config.getConfigurationSection(key)
            }
        }
        return null
    }

    /**
     * 辅助方法：获取材质名称（支持多种键名变体）
     */
    private fun getMaterial(display: ConfigurationSection): String? {
        val materialPattern = Regex("(?i)^mat(erials?)?$")
        for (key in display.getKeys(false)) {
            if (materialPattern.matches(key)) {
                return display.getString(key)
            }
        }
        return null
    }

    /**
     * 辅助方法：设置物品模型（支持 1.21.4+ 的 ItemModel 和旧版本的 CustomModelData）
     * @param meta 物品元数据
     * @param display 显示配置
     */
    private fun setItemModel(meta: org.bukkit.inventory.meta.ItemMeta, display: ConfigurationSection) {
        // 1. 尝试使用 1.21.4+ 的 ItemModel API
        if (display.contains("item_model")) {
            val itemModelString = display.getString("item_model") ?: return

            try {
                // 检查服务器版本
                val serverVersion = Bukkit.getBukkitVersion()
                val versionParts = serverVersion.split(".")
                val minorVersion = versionParts.getOrNull(1)?.toIntOrNull()
                val patchVersion = versionParts.getOrNull(2)?.split("-")?.get(0)?.toIntOrNull()

                // 检查是否是 1.21.4 或更高版本
                val isModernVersion = when {
                    minorVersion == null -> false
                    minorVersion > 21 -> true
                    minorVersion == 21 -> {
                        // 1.21.4 及以上才支持 ItemModel
                        (patchVersion ?: 0) >= 4
                    }
                    else -> false
                }

                // 解析 item_model 字符串
                val parts = itemModelString.split(":")

                if (parts.size != 2) {
                    plugin.logger.warning("Invalid item_model format: '$itemModelString'. Expected format: 'namespace:key'")
                    applyCustomModelData(meta, display)
                    return
                }

                val namespace = parts[0]
                val key = parts[1]

                // 对于旧版本，降级到 CustomModelData
                if (!isModernVersion) {
                    plugin.logger.info("Server version $serverVersion does not support ItemModel API (requires 1.21.4+), using CustomModelData")
                    applyCustomModelData(meta, display)
                    return
                }

                // 尝试使用 ItemModel API（setItemModel(NamespacedKey)）
                try {
                    val namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey")
                    val itemMetaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta")

                    // 获取 setItemModel(NamespacedKey) 方法
                    val setItemModelMethod = itemMetaClass.getMethod("setItemModel", namespacedKeyClass)

                    // 创建 NamespacedKey（支持任何命名空间，包括 oraxen）
                    val modelKey = namespacedKeyClass.getDeclaredConstructor(
                        String::class.java,
                        String::class.java
                    ).newInstance(namespace, key)

                    // 应用 ItemModel
                    setItemModelMethod.invoke(meta, modelKey)
                } catch (e: ClassNotFoundException) {
                    plugin.logger.warning("Failed to apply ItemModel '$itemModelString': ${e.message}")
                    applyCustomModelData(meta, display)
                } catch (e: NoSuchMethodException) {
                    plugin.logger.warning("Failed to apply ItemModel '$itemModelString': ${e.message}")
                    applyCustomModelData(meta, display)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to apply ItemModel '$itemModelString': ${e.message}")
                    applyCustomModelData(meta, display)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Unexpected exception: ${e.message}")
                plugin.logger.info("Falling back to CustomModelData")
                applyCustomModelData(meta, display)
            }
        } else {
            applyCustomModelData(meta, display)
        }
    }

    /**
     * 应用自定义模型数据（降级方案）
     */
    private fun applyCustomModelData(meta: org.bukkit.inventory.meta.ItemMeta, display: ConfigurationSection) {
        if (display.contains("custom_data")) {
            val customData = display.getInt("custom_data")
            meta.setCustomModelData(customData)
        }
    }

    /**
     * 应用菜单默认图标配置
     * @param meta 物品元数据
     * @param iconKey 图标键 (如 "buffs_lock", "buffs_unlock")
     */
    private fun applyMenuDefaultIcon(meta: org.bukkit.inventory.meta.ItemMeta, iconKey: String) {
        val iconConfig = menuDefaultIcons[iconKey] ?: return

        // 1. 尝试使用 1.21.4+ 的 ItemModel API
        if (iconConfig.itemModel != null) {
            val itemModelString = iconConfig.itemModel

            try {
                // 检查服务器版本
                val serverVersion = Bukkit.getBukkitVersion()
                val versionParts = serverVersion.split(".")
                val minorVersion = versionParts.getOrNull(1)?.toIntOrNull()
                val patchVersion = versionParts.getOrNull(2)?.split("-")?.get(0)?.toIntOrNull()

                // 检查是否是 1.21.4 或更高版本
                val isModernVersion = when {
                    minorVersion == null -> false
                    minorVersion > 21 -> true
                    minorVersion == 21 -> {
                        // 1.21.4 及以上才支持 ItemModel
                        (patchVersion ?: 0) >= 4
                    }
                    else -> false
                }

                // 解析 item_model 字符串
                val parts = itemModelString.split(":")

                if (parts.size != 2) {
                    plugin.logger.warning("Invalid item_model format: '$itemModelString'. Expected format: 'namespace:key'")
                    meta.setCustomModelData(iconConfig.customData)
                    return
                }

                val namespace = parts[0]
                val key = parts[1]

                // 对于旧版本，降级到 CustomModelData
                if (!isModernVersion) {
                    plugin.logger.info("Server version $serverVersion does not support ItemModel API (requires 1.21.4+), using CustomModelData")
                    meta.setCustomModelData(iconConfig.customData)
                    return
                }

                // 尝试使用 ItemModel API（setItemModel(NamespacedKey)）
                try {
                    val namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey")
                    val itemMetaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta")

                    // 获取 setItemModel(NamespacedKey) 方法
                    val setItemModelMethod = itemMetaClass.getMethod("setItemModel", namespacedKeyClass)

                    // 创建 NamespacedKey（支持任何命名空间，包括 oraxen）
                    val modelKey = namespacedKeyClass.getDeclaredConstructor(
                        String::class.java,
                        String::class.java
                    ).newInstance(namespace, key)

                    // 应用 ItemModel
                    setItemModelMethod.invoke(meta, modelKey)
                } catch (e: ClassNotFoundException) {
                    plugin.logger.warning("Failed to apply ItemModel '$itemModelString': ${e.message}")
                    meta.setCustomModelData(iconConfig.customData)
                } catch (e: NoSuchMethodException) {
                    plugin.logger.warning("Failed to apply ItemModel '$itemModelString': ${e.message}")
                    meta.setCustomModelData(iconConfig.customData)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to apply ItemModel '$itemModelString': ${e.message}")
                    meta.setCustomModelData(iconConfig.customData)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Unexpected exception: ${e.message}")
                meta.setCustomModelData(iconConfig.customData)
            }
        } else {
            // 降级到 CustomModelData
            meta.setCustomModelData(iconConfig.customData)
        }
    }

    /**
     * 应用任务定义的物品显示配置
     */
    private fun applyTaskItemDisplay(meta: org.bukkit.inventory.meta.ItemMeta, taskDisplay: TaskManager.TaskItemDisplay) {
        // 1. 尝试使用 1.21.4+ 的 ItemModel API
        if (taskDisplay.itemModel != null) {
            val itemModelString = taskDisplay.itemModel

            try {
                // 检查服务器版本
                val serverVersion = Bukkit.getBukkitVersion()
                val versionParts = serverVersion.split(".")
                val minorVersion = versionParts.getOrNull(1)?.toIntOrNull()
                val patchVersion = versionParts.getOrNull(2)?.split("-")?.get(0)?.toIntOrNull()

                // 检查是否是 1.21.4 或更高版本
                val isModernVersion = when {
                    minorVersion == null -> false
                    minorVersion > 21 -> true
                    minorVersion == 21 -> {
                        // 1.21.4 及以上才支持 ItemModel
                        (patchVersion ?: 0) >= 4
                    }
                    else -> false
                }

                // 解析 item_model 字符串
                val parts = itemModelString.split(":")

                if (parts.size != 2) {
                    plugin.logger.warning("Invalid item_model format: '$itemModelString'. Expected format: 'namespace:key'")
                    meta.setCustomModelData(taskDisplay.customData)
                    return
                }

                val namespace = parts[0]
                val key = parts[1]

                // 对于旧版本，降级到 CustomModelData
                if (!isModernVersion) {
                    plugin.logger.info("Server version $serverVersion does not support ItemModel API (requires 1.21.4+), using CustomModelData")
                    meta.setCustomModelData(taskDisplay.customData)
                    return
                }

                // 尝试使用 ItemModel API（setItemModel(NamespacedKey)）
                try {
                    val namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey")
                    val itemMetaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta")

                    // 获取 setItemModel(NamespacedKey) 方法
                    val setItemModelMethod = itemMetaClass.getMethod("setItemModel", namespacedKeyClass)

                    // 创建 NamespacedKey（支持任何命名空间，包括 oraxen）
                    val modelKey = namespacedKeyClass.getDeclaredConstructor(
                        String::class.java,
                        String::class.java
                    ).newInstance(namespace, key)

                    // 应用 ItemModel
                    setItemModelMethod.invoke(meta, modelKey)
                } catch (e: ClassNotFoundException) {
                    plugin.logger.warning("Failed to apply ItemModel '$itemModelString': ${e.message}")
                    meta.setCustomModelData(taskDisplay.customData)
                } catch (e: NoSuchMethodException) {
                    plugin.logger.warning("Failed to apply ItemModel '$itemModelString': ${e.message}")
                    meta.setCustomModelData(taskDisplay.customData)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to apply ItemModel '$itemModelString': ${e.message}")
                    meta.setCustomModelData(taskDisplay.customData)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Unexpected exception: ${e.message}")
                meta.setCustomModelData(taskDisplay.customData)
            }
        } else {
            // 降级到 CustomModelData
            meta.setCustomModelData(taskDisplay.customData)
        }
    }

    /**
     * 菜单统一入口
     * @param player 玩家
     * @param menuName 菜单名称
     * @param page 页码
     */
    fun openMenu(player: Player, menuName: String, page: Int = 0) {
        val lang = plugin.langManager
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        if (!file.exists()) {
            player.sendMessage(lang.get("menu-not-found", "menu" to menuName))
            return
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val buttons = getButtonsSection(config) ?: return

        // 检查按钮类型
        var isGuildList = false
        var isMemberList = false
        var isAllPlayerList = false
        var isBuffList = false
        var isVaultList = false
        var isUpgradeList = false
        var isTaskDailyList = false
        var isTaskGlobalList = false

        for (key in buttons.getKeys(false)) {
            val type = buttons.getConfigurationSection(key)?.getString("type")
            when (type) {
                "GUILDS_LIST" -> isGuildList = true
                "MEMBERS_LIST" -> isMemberList = true
                "ALL_PLAYER" -> isAllPlayerList = true
                "BUFF_LIST" -> isBuffList = true
                "GUILD_VAULTS" -> isVaultList = true
                "GUILD_UPGRADE" -> isUpgradeList = true
                "TASK_DAILY" -> isTaskDailyList = true
                "TASK_GLOBAL" -> isTaskGlobalList = true
            }
        }

        when {
            isMemberList -> openMemberListMenu(player, menuName, page)
            isAllPlayerList -> openAllPlayerMenu(player, menuName, page)
            isGuildList -> openGuildListMenu(player, menuName, page)
            isBuffList -> openBuffShopMenu(player, menuName, page)
            isVaultList -> openVaultMenu(player, menuName)
            isUpgradeList -> openUpgradeMenu(player, menuName, page)
            isTaskDailyList -> openTaskMenu(player, menuName, page, "daily")
            isTaskGlobalList -> openTaskMenu(player, menuName, page, "global")
            else -> renderStandardMenu(player, config, menuName)
        }
    }

    /**
     * 标准菜单渲染
     * @param player 玩家
     * @param config 菜单配置
     * @param menuName 菜单名称
     */
    private fun renderStandardMenu(player: Player, config: YamlConfiguration, menuName: String) {
        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "Menu")!!)
        val layout = getLayout(config)
        val buttons = getButtonsSection(config)

        // 这里 page 默认为 0，普通菜单通常不需要分页逻辑
        val holder = GuildMenuHolder(title, layout, buttons, 0, menuName, player)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 填充图标并检查按钮级别的 update
        for (r in layout.indices) {
            val line = layout[r]
            for (c in line.indices) {
                val slot = r * 9 + c
                val char = line[c].toString()
                val btnSection = buttons?.getConfigurationSection(char) ?: continue

                // 普通菜单使用 buildNormalItem 即可
                inv.setItem(slot, buildNormalItem(btnSection, holder, 1, player))
            }
        }

        // 处理刷新任务 (针对普通菜单的动态变量刷新)
        val updateTicks = config.getLong("update", 0L)
        if (updateTicks > 0) {
            holder.updateTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                refreshMenu(holder)
            }, updateTicks, updateTicks)
        }

        player.openInventory(inv)
    }
    /**
     * 公会列表菜单渲染
     * @param player 玩家
     * @param menuName 菜单名称
     * @param page 页码
     */
    fun openGuildListMenu(player: Player, menuName: String, page: Int = 0) {
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        if (!file.exists()) return

        val config = YamlConfiguration.loadConfiguration(file)
        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "Guild List")!!)
        val layout = getLayout(config)
        val buttons = getButtonsSection(config) ?: return

        // 1. 统计布局中共有多少个用于展示公会的槽位
        val listSlots = mutableListOf<Int>()
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val char = layout[r][c].toString()
                if (buttons.getConfigurationSection(char)?.getString("type") == "GUILDS_LIST") {
                    listSlots.add(r * 9 + c)
                }
            }
        }

        // 2. 数据库分页计算
        val guildsPerPage = listSlots.size
        val totalGuilds = plugin.dbManager.getGuildCount() // 从数据库实时获取总数

        val maxPages = if (guildsPerPage > 0) {
            ceil(totalGuilds.toDouble() / guildsPerPage).toInt().coerceAtLeast(1)
        } else 1

        // 确保请求页码不越界
        val safePage = page.coerceIn(0, maxPages - 1)

        // 关键优化：只从数据库加载当前页面所需的公会数据
        val currentPageGuilds = if (guildsPerPage > 0) {
            plugin.dbManager.getGuildsByPage(safePage, guildsPerPage)
        } else emptyList()

        val holder = GuildMenuHolder(title, layout, buttons, safePage, menuName, player)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 3. 开始渲染槽位
        for (r in layout.indices) {
            val line = layout[r]
            for (c in line.indices) {
                val slot = r * 9 + c
                val char = line[c].toString()
                if (char == " ") continue

                val btnSection = buttons.getConfigurationSection(char) ?: continue

                if (btnSection.getString("type") == "GUILDS_LIST") {
                    // 处理动态公会项
                    val relativeIdx = listSlots.indexOf(slot)
                    if (relativeIdx != -1 && relativeIdx < currentPageGuilds.size) {
                        // 使用当前页的数据源
                        inv.setItem(slot, buildGuildItem(btnSection, currentPageGuilds[relativeIdx], player))
                    } else {
                        inv.setItem(slot, null)
                    }
                } else {
                    // 渲染普通按钮，并传入 player 以支持解析玩家自己的公会变量
                    inv.setItem(slot, buildNormalItem(btnSection, holder, maxPages, player))
                }
            }
        }
        player.openInventory(inv)
    }
    /**
     * 构建公会项
     * @param section 按钮配置
     * @param guild 公会数据
     * @param player 玩家
     * @return 公会项
     */
    private fun buildGuildItem(section: ConfigurationSection, guild: DatabaseManager.GuildData, player: Player): ItemStack {
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.PAPER)
        val material = Material.getMaterial((guild.icon ?: "PAPER").uppercase()) ?: Material.PAPER

        val amount = display.getInt("amount", 1).coerceIn(1, 64)
        val item = ItemStack(material, amount)
        val meta = item.itemMeta ?: return item

        // 优先使用 display 配置中的 item_model/custom_data，其次使用数据库中的公会图标配置
        if (!display.contains("item_model") && !display.contains("custom_data")) {
            // 如果 display 没有配置，则使用数据库中的公会图标
            applyGuildIcon(meta, guild)
        } else {
            setItemModel(meta, display)
        }

        val key = NamespacedKey(plugin, "guild_id")
        meta.persistentDataContainer.set(key, PersistentDataType.INTEGER, guild.id)

        val placeholders = getGuildPlaceholders(guild, player)
        meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, player))
        meta.lore = display.getStringList("lore").map { applyPlaceholders(it, placeholders, player) }

        item.itemMeta = meta

        // 安全地获取 customModelData（如果 ItemModel 已设置，customModelData 可能不可用）
        val customModelData = if (meta.hasCustomModelData()) {
            meta.customModelData
        } else {
            null
        }
        return item
    }

    /**
     * 应用公会图标配置
     */
    private fun applyGuildIcon(meta: org.bukkit.inventory.meta.ItemMeta, guild: DatabaseManager.GuildData) {
        // 优先使用 icon_item_model
        if (!guild.iconItemModel.isNullOrEmpty()) {
            try {
                // 检查服务器版本
                val serverVersion = Bukkit.getBukkitVersion()
                val versionParts = serverVersion.split(".")
                val minorVersion = versionParts.getOrNull(1)?.toIntOrNull()
                val patchVersion = versionParts.getOrNull(2)?.split("-")?.get(0)?.toIntOrNull()

                // 检查是否是 1.21.4 或更高版本
                val isModernVersion = when {
                    minorVersion == null -> false
                    minorVersion > 21 -> true
                    minorVersion == 21 -> {
                        // 1.21.4 及以上才支持 ItemModel
                        (patchVersion ?: 0) >= 4
                    }
                    else -> false
                }

                if (isModernVersion) {
                    // 解析 item_model 字符串
                    val parts = guild.iconItemModel.split(":")
                    if (parts.size == 2) {
                        val namespace = parts[0]
                        val key = parts[1]

                        try {
                            val namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey")
                            val itemMetaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta")

                            // 获取 setItemModel(NamespacedKey) 方法
                            val setItemModelMethod = itemMetaClass.getMethod("setItemModel", namespacedKeyClass)

                            // 创建 NamespacedKey
                            val modelKey = namespacedKeyClass.getDeclaredConstructor(
                                String::class.java,
                                String::class.java
                            ).newInstance(namespace, key)

                            // 应用 ItemModel
                            setItemModelMethod.invoke(meta, modelKey)
                        } catch (e: Exception) {
                            // 失败则尝试使用 CustomModelData
                            plugin.logger.warning("Failed to apply ItemModel: ${e.message}")
                            if (guild.iconCustomData != null) {
                                meta.setCustomModelData(guild.iconCustomData)
                            }
                        }
                    } else {
                        plugin.logger.warning("Invalid ItemModel format: ${guild.iconItemModel}")
                        if (guild.iconCustomData != null) {
                            // 格式不正确，使用 CustomModelData
                            meta.setCustomModelData(guild.iconCustomData)
                        }
                    }
                } else {
                    // 旧版本，使用 CustomModelData
                    if (guild.iconCustomData != null) {
                        meta.setCustomModelData(guild.iconCustomData)
                    }
                }
            } catch (e: Exception) {
                // 解析失败，忽略
                plugin.logger.warning("Error in applyGuildIcon: ${e.message}")
                e.printStackTrace()
            }
        } else {
            // 使用 CustomModelData
            if (guild.iconCustomData != null) {
                meta.setCustomModelData(guild.iconCustomData)
            }
        }
    }

    /**
     * 成员列表菜单渲染
     * @param player 玩家
     * @param menuName 菜单名称
     * @param page 页码
     */
    fun openMemberListMenu(player: Player, menuName: String, page: Int = 0) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        val layout = getLayout(config)
        val buttons = getButtonsSection(config) ?: return

        val allMembers = plugin.dbManager.getGuildMembers(guildId)
        val listSlots = mutableListOf<Int>()
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val char = layout[r][c].toString()
                if (buttons.getConfigurationSection(char)?.getString("type") == "MEMBERS_LIST") {
                    listSlots.add(r * 9 + c)
                }
            }
        }

        val membersPerPage = listSlots.size
        val maxPages = ceil(allMembers.size.toDouble() / membersPerPage.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val currentPageData = allMembers.drop(page * membersPerPage).take(membersPerPage)

        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "Members")!!)
        val holder = GuildMenuHolder(title, layout, buttons, page, menuName, player)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 初始填充逻辑
        for (r in layout.indices) {
            val line = layout[r]
            for (c in line.indices) {
                val slot = r * 9 + c
                val char = line[c].toString()
                val btnSection = buttons.getConfigurationSection(char) ?: continue

                if (btnSection.getString("type") == "MEMBERS_LIST") {
                    val relativeIdx = listSlots.indexOf(slot)
                    if (relativeIdx != -1 && relativeIdx < currentPageData.size) {
                        // 修复：传入 player 作为 viewer
                        inv.setItem(slot, buildMemberItem(btnSection, currentPageData[relativeIdx], player))
                    }
                } else {
                    inv.setItem(slot, buildNormalItem(btnSection, holder, maxPages, player))
                }
            }
        }

        // --- 启动菜单级别的定时刷新任务 ---
        val updateTicks = config.getLong("update", 0L)
        if (updateTicks > 0) {
            holder.updateTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                refreshMenu(holder)
            }, updateTicks, updateTicks)
        }

        player.openInventory(inv)
    }

    /**
     * 打开全服玩家列表菜单
     * @param player 玩家
     * @param menuName 菜单名称
     * @param page 页码
     */
    fun openAllPlayerMenu(player: Player, menuName: String, page: Int = 0) {
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        val layout = getLayout(config)
        val buttons = getButtonsSection(config) ?: return

        val allPlayers = getCrossServerOnlinePlayers()
        val listSlots = mutableListOf<Int>()
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val char = layout[r][c].toString()
                if (buttons.getConfigurationSection(char)?.getString("type") == "ALL_PLAYER") {
                    listSlots.add(r * 9 + c)
                }
            }
        }

        val playersPerPage = listSlots.size
        val maxPages = ceil(allPlayers.size.toDouble() / playersPerPage.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val currentPageData = allPlayers.drop(page * playersPerPage).take(playersPerPage)

        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "All Players")!!)
        val holder = GuildMenuHolder(title, layout, buttons, page, menuName, player)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 初始填充逻辑
        for (r in layout.indices) {
            val line = layout[r]
            for (c in line.indices) {
                val slot = r * 9 + c
                val char = line[c].toString()
                val btnSection = buttons.getConfigurationSection(char) ?: continue

                if (btnSection.getString("type") == "ALL_PLAYER") {
                    val relativeIdx = listSlots.indexOf(slot)
                    if (relativeIdx != -1 && relativeIdx < currentPageData.size) {
                        val playerName = currentPageData[relativeIdx]
                        val playerUUID = getUUIDByName(playerName)
                        // 获取玩家的公会信息
                        val guildName = playerUUID?.let { uuid ->
                            val gid = plugin.dbManager.getGuildIdByPlayer(uuid)
                            gid?.let { plugin.dbManager.getGuildData(it)?.name }
                        } ?: plugin.langManager.get("papi-no-guild")

                        val placeholders = mapOf(
                            "player_name" to playerName,
                            "guild_name" to guildName
                        )
                        inv.setItem(slot, buildAllPlayerItem(btnSection, playerName, playerUUID, placeholders, player))
                    }
                } else {
                    inv.setItem(slot, buildNormalItem(btnSection, holder, maxPages, player))
                }
            }
        }

        // --- 启动菜单级别的定时刷新任务 ---
        val updateTicks = config.getLong("update", 0L)
        if (updateTicks > 0) {
            holder.updateTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                refreshMenu(holder)
            }, updateTicks, updateTicks)
        }

        player.openInventory(inv)
    }

    /**
     * 构建全服玩家项
     * @param section 按钮配置
     * @param playerName 玩家名
     * @param playerUUID 玩家UUID
     * @param placeholders 占位符映射
     * @param viewer 查看者
     * @return 玩家项
     */
    private fun buildAllPlayerItem(
        section: ConfigurationSection,
        playerName: String,
        playerUUID: UUID?,
        placeholders: Map<String, String>,
        viewer: Player
    ): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item

        // 设置玩家名称（用于显示头骨皮肤）
        meta.owningPlayer = plugin.server.getOfflinePlayer(playerUUID ?: plugin.server.getOfflinePlayer(playerName).uniqueId)

        // 写入 PDC 标识（UUID）
        if (playerUUID != null) {
            val key = NamespacedKey(plugin, "player_uuid")
            meta.persistentDataContainer.set(key, PersistentDataType.STRING, playerUUID.toString())
        }

        meta.setDisplayName(applyPlaceholders(section.getConfigurationSection("display")?.getString("name") ?: "", placeholders, viewer))
        meta.lore = section.getConfigurationSection("display")?.getStringList("lore")?.map { applyPlaceholders(it, placeholders, viewer) }

        item.itemMeta = meta

        // 异步加载皮肤
        if (playerUUID != null) {
            loadSkinAsync(item, playerUUID)
        }

        return item
    }

    /**
     * 构建成员项
     * @param section 按钮配置
     * @param member 成员数据
     * @param viewer 玩家
     * @return 成员项
     */
    private fun buildMemberItem(section: ConfigurationSection, member: DatabaseManager.MemberData, viewer: Player): ItemStack {
        // 1. 创建头颅 ItemStack，此时它是空白/默认皮肤
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item

        // 2. 写入 PDC 标识（UUID）
        val key = NamespacedKey(plugin, "member_uuid")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, member.uuid.toString())

        // 3. 填充变量 (Name, Role, JoinTime)
        val roleDisplay = when (member.role.uppercase()) {
            "OWNER" -> plugin.langManager.get("papi-role-owner")
            "ADMIN" -> plugin.langManager.get("papi-role-admin")
            "MEMBER" -> plugin.langManager.get("papi-role-member")
            else -> plugin.langManager.get("papi-role-none")
        }

        val placeholders = mapOf(
            "members_name" to (member.name ?: "Null"),
            "members_role" to roleDisplay,
            "members_contribution" to member.contribution.toString(),
            "members_join_time" to SimpleDateFormat(plugin.config.getString("date-format", "yyyy-MM-dd HH:mm:ss")!!).format(Date(member.joinTime))
        )

        meta.setDisplayName(applyPlaceholders(section.getConfigurationSection("display")?.getString("name") ?: "", placeholders, viewer))
        meta.lore = section.getConfigurationSection("display")?.getStringList("lore")?.map { applyPlaceholders(it, placeholders, viewer) }

        item.itemMeta = meta

        // 4. 调用异步加载逻辑：物品会先显示默认皮肤，随后自动变成玩家皮肤
        loadSkinAsync(item, member.uuid)

        return item
    }

    /**
     * 构建普通项
     * @param section 按钮配置
     * @param holder 菜单持有者
     * @param maxPages 最大页数
     * @param player 玩家
     * @return 普通项
     */
    private fun buildNormalItem(section: ConfigurationSection, holder: GuildMenuHolder, maxPages: Int = 1, player: Player): ItemStack {
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.STONE)
        val materialName = getMaterial(display)?.uppercase() ?: "STONE"

        // 读取数量和自定义材质数据
        val amount = display.getInt("amount", 1).coerceIn(1, 64)
        val item = ItemStack(Material.getMaterial(materialName) ?: Material.STONE, amount)
        val meta = item.itemMeta ?: return item

        setItemModel(meta, display)

        // 基础变量
        val placeholders = mutableMapOf(
            "page" to (holder.currentPage + 1).toString(),
            "total_pages" to maxPages.toString(),
            "player" to player.name,
            "balance_rename" to plugin.config.getDouble("balance.rename", 3000.0).toString(),
            "balance_settp" to plugin.config.getDouble("balance.settp", 1000.0).toString(),
            "balance_seticon" to plugin.config.getDouble("balance.seticon", 1000.0).toString(),
            "balance_setmotd" to plugin.config.getDouble("balance.setmotd", 100.0).toString(),
            "balance_pvp" to plugin.config.getDouble("balance.pvp", 300.0).toString()
        )

        val guildId = plugin.playerGuildCache[player.uniqueId]
        if (guildId != null) {
            val myGuildData = plugin.dbManager.getGuildData(guildId)
            if (myGuildData != null) {
                placeholders.putAll(getGuildPlaceholders(myGuildData, player))
            }
        }

        meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, player))
        meta.lore = display.getStringList("lore").map { applyPlaceholders(it, placeholders, player) }

        item.itemMeta = meta
        return item
    }
    /**
     * 统一变量替换核心方法
     * @param text 原始文本
     * @param placeholders 变量映射表
     * @param viewer 玩家
     * @return 替换后的文本
     */
    private fun applyPlaceholders(text: String, placeholders: Map<String, String>, viewer: Player): String {
        var result = text

        // 1. 优先处理插件内部变量: 使用 {} 格式
        placeholders.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }

        // 2. 处理 PAPI 变量: 使用 %% 格式
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            result = PlaceholderAPI.setPlaceholders(viewer, result)
        }

        // 3. 最后转换颜色
        return ChatColor.translateAlternateColorCodes('&', result)
    }

    /**
     * 获取指定公会数据的变量映射表
     * @param guild 公会数据
     * @param player 玩家
     * @return 变量映射表
     */
    fun getGuildPlaceholders(guild: DatabaseManager.GuildData, player: Player): Map<String, String> {
        val onlineCount = plugin.playerGuildCache.values.count { it == guild.id }
        val formatPattern = plugin.config.getString("date-format", "yyyy-MM-dd HH:mm:ss")!!
        val createTimeStr = SimpleDateFormat(formatPattern).format(Date(guild.createTime))

        // 获取当前等级的传送费用
        val tpMoney = plugin.levelsConfig.getDouble("levels.${guild.level}.tp-money", 0.0)

        return mapOf(
            "id" to guild.id.toString(),
            "name" to guild.name,
            "level" to guild.level.toString(),
            "members" to plugin.dbManager.getMemberCount(guild.id).toString(),
            "max_members" to guild.maxMembers.toString(),
            "online" to onlineCount.toString(),
            "balance" to String.format("%.2f", guild.balance),
            "announcement" to (guild.announcement ?: "None"),
            "create_time" to createTimeStr,
            "owner" to (guild.ownerName ?: "Null"),
            "player" to player.name,
            "role" to (plugin.dbManager.getPlayerRole(player.uniqueId) ?: "NONE"),
            "role_node" to when (plugin.dbManager.getPlayerRole(player.uniqueId)) {
                "OWNER" -> "3"
                "ADMIN" -> "2"
                "MEMBER" -> "1"
                else -> "0"
            },
            "tp_money" to tpMoney.toString()
        )
    }

    /**
     * 异步加载皮肤
     * @param item 物品
     * @param playerUuid 玩家UUID
     */
    private fun loadSkinAsync(item: ItemStack, playerUuid: UUID?) {
        if (playerUuid == null) return
        val meta = item.itemMeta as? SkullMeta ?: return
        meta.owningPlayer = Bukkit.getOfflinePlayer(playerUuid)
        item.itemMeta = meta
    }

    /**
     * 通用刷新菜单内容 (供 update 任务调用)
     * @param holder 菜单持有者
     */
    private fun refreshMenu(holder: GuildMenuHolder) {
        val layout = holder.layout
        val buttons = holder.buttons
        val inv = holder.inventory
        val player = holder.player

        for (r in layout.indices) {
            val line = layout[r]
            for (c in line.indices) {
                val slot = r * 9 + c
                val char = line[c].toString()
                if (char == " ") continue

                val btnSection = buttons?.getConfigurationSection(char) ?: continue
                when (val type = btnSection.getString("type")) {
                    "MEMBERS_LIST" -> {
                        // 实时获取成员列表数据
                        val guildId = plugin.playerGuildCache[player.uniqueId]
                        if (guildId != null) {
                            val allMembers = plugin.dbManager.getGuildMembers(guildId)
                            val listSlots = mutableListOf<Int>()
                            for (lr in layout.indices) {
                                for (lc in layout[lr].indices) {
                                    if (buttons.getConfigurationSection(layout[lr][lc].toString())?.getString("type") == "MEMBERS_LIST") {
                                        listSlots.add(lr * 9 + lc)
                                    }
                                }
                            }
                            val membersPerPage = listSlots.size
                            val currentPageData = allMembers.drop(holder.currentPage * membersPerPage).take(membersPerPage)
                            val relativeIdx = listSlots.indexOf(slot)
                            if (relativeIdx != -1 && relativeIdx < currentPageData.size) {
                                val item = buildMemberItem(btnSection, currentPageData[relativeIdx], player)
                                inv.setItem(slot, item)
                            }
                        }
                    }
                    "GUILDS_LIST" -> {
                        // 实时获取公会列表数据
                        val listSlots = mutableListOf<Int>()
                        for (lr in layout.indices) {
                            for (lc in layout[lr].indices) {
                                if (buttons.getConfigurationSection(layout[lr][lc].toString())?.getString("type") == "GUILDS_LIST") {
                                    listSlots.add(lr * 9 + lc)
                                }
                            }
                        }
                        val guildsPerPage = listSlots.size
                        val currentPageGuilds = if (guildsPerPage > 0) {
                            plugin.dbManager.getGuildsByPage(holder.currentPage, guildsPerPage)
                        } else emptyList()
                        val relativeIdx = listSlots.indexOf(slot)
                        if (relativeIdx != -1 && relativeIdx < currentPageGuilds.size) {
                            val item = buildGuildItem(btnSection, currentPageGuilds[relativeIdx], player)
                            inv.setItem(slot, item)
                        }
                    }
                    "BUFF_LIST" -> {
                        // 实时获取Buff数据
                        val guildId = plugin.playerGuildCache[player.uniqueId]
                        if (guildId != null) {
                            val guildData = plugin.dbManager.getGuildData(guildId)
                            if (guildData != null) {
                                val allBuffKeys = plugin.buffsConfig.getConfigurationSection("buffs")?.getKeys(false)?.toList() ?: emptyList()
                                val listSlots = mutableListOf<Int>()
                                for (lr in layout.indices) {
                                    for (lc in layout[lr].indices) {
                                        if (buttons.getConfigurationSection(layout[lr][lc].toString())?.getString("type") == "BUFF_LIST") {
                                            listSlots.add(lr * 9 + lc)
                                        }
                                    }
                                }
                                val buffsPerPage = listSlots.size
                                val currentPageBuffs = allBuffKeys.drop(holder.currentPage * buffsPerPage).take(buffsPerPage)
                                val relativeIdx = listSlots.indexOf(slot)
                                if (relativeIdx != -1 && relativeIdx < currentPageBuffs.size) {
                                    val buffKey = currentPageBuffs[relativeIdx]
                                    val allowedBuffs = plugin.levelsConfig.getStringList("levels.${guildData.level}.use-buff")
                                    val isUnlocked = allowedBuffs.contains(buffKey)
                                    val item = buildBuffItem(btnSection, buffKey, isUnlocked, player)
                                    inv.setItem(slot, item)
                                }
                            }
                        }
                    }
                    "GUILD_VAULTS" -> {
                        // 实时获取金库数据
                        val guildId = plugin.playerGuildCache[player.uniqueId] ?: 0
                        val guildData = plugin.dbManager.getGuildData(guildId)
                        val level = guildData?.level ?: 1
                        val unlockedCount = plugin.levelsConfig.getInt("levels.$level.vaults", 0)
                        var vaultCounter = 1
                        for (vr in layout.indices) {
                            for (vc in layout[vr].indices) {
                                val vSlot = vr * 9 + vc
                                val vChar = layout[vr][vc].toString()
                                if (buttons.getConfigurationSection(vChar)?.getString("type") == "GUILD_VAULTS") {
                                    if (slot == vSlot) {
                                        val item = buildVaultItem(player, vaultCounter, unlockedCount, btnSection)
                                        inv.setItem(slot, item)
                                    }
                                    vaultCounter++
                                }
                            }
                        }
                    }
                    "GUILD_UPGRADE" -> {
                        // 实时获取升级数据
                        val guildId = plugin.playerGuildCache[player.uniqueId]
                        if (guildId != null) {
                            val guildData = plugin.dbManager.getGuildData(guildId)
                            if (guildData != null) {
                                val allLevelKeys = plugin.levelsConfig.getConfigurationSection("levels")?.getKeys(false)?.toList()
                                    ?.mapNotNull { it.toIntOrNull() }?.sorted() ?: emptyList()
                                val listSlots = mutableListOf<Int>()
                                for (ur in layout.indices) {
                                    for (uc in layout[ur].indices) {
                                        if (buttons.getConfigurationSection(layout[ur][uc].toString())?.getString("type") == "GUILD_UPGRADE") {
                                            listSlots.add(ur * 9 + uc)
                                        }
                                    }
                                }
                                val itemsPerPage = listSlots.size
                                val currentPageLevels = allLevelKeys.drop(holder.currentPage * itemsPerPage).take(itemsPerPage)
                                val relativeIdx = listSlots.indexOf(slot)
                                if (relativeIdx != -1 && relativeIdx < currentPageLevels.size) {
                                    val targetLevel = currentPageLevels[relativeIdx]
                                    val item = buildUpgradeItem(btnSection, targetLevel, guildData, player)
                                    inv.setItem(slot, item)
                                }
                            }
                        }
                    }
                    "TASK_DAILY", "TASK_GLOBAL" -> {
                        // 实时获取任务数据
                        val guildId = plugin.playerGuildCache[player.uniqueId]
                        if (guildId != null) {
                            val taskType = if (type == "TASK_DAILY") "daily" else "global"
                            val allTasks = plugin.taskManager.taskDefinitions.values.filter { it.type == taskType }
                            val listSlots = mutableListOf<Int>()
                            for (tr in layout.indices) {
                                for (tc in layout[tr].indices) {
                                    if (buttons.getConfigurationSection(layout[tr][tc].toString())?.getString("type") == type) {
                                        listSlots.add(tr * 9 + tc)
                                    }
                                }
                            }
                            val tasksPerPage = listSlots.size
                            val currentPageTasks = allTasks.drop(holder.currentPage * tasksPerPage).take(tasksPerPage)
                            val relativeIdx = listSlots.indexOf(slot)
                            if (relativeIdx != -1 && relativeIdx < currentPageTasks.size) {
                                val task = currentPageTasks[relativeIdx]
                                val item = buildTaskItem(btnSection, task, player, guildId)
                                inv.setItem(slot, item)
                            }
                        }
                    }
                    else -> {
                        // 普通按钮（变量替换）
                        val item = buildNormalItem(btnSection, holder, 1, player)
                        inv.setItem(slot, item)
                    }
                }
            }
        }
    }

    /**
     * 打开 Buff 商店菜单
     * @param player 玩家
     * @param menuName 菜单名
     * @param page 页数
     */
    fun openBuffShopMenu(player: Player, menuName: String, page: Int = 0) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return
        val guildData = plugin.dbManager.getGuildData(guildId) ?: return // 假设你有获取公会数据的方法
        val currentLevel = guildData.level

        // 1. 获取当前等级解锁的 Buff 列表
        val allowedBuffs = plugin.levelsConfig.getStringList("levels.$currentLevel.use-buff")

        // 2. 获取所有的 Buff 配置 (从 buffs.yml)
        val buffsSection = plugin.buffsConfig.getConfigurationSection("buffs") ?: return
        val allBuffKeys = buffsSection.getKeys(false).toList() // 获取 NightVision, Speed 等

        // 3. 加载菜单布局
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        val menuConfig = YamlConfiguration.loadConfiguration(file)
        val layout = getLayout(menuConfig)
        val buttons = getButtonsSection(menuConfig) ?: return

        // 4. 计算分页
        val buffSlots = mutableListOf<Int>()
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                if (buttons.getConfigurationSection(layout[r][c].toString())?.getString("type") == "BUFF_LIST") {
                    buffSlots.add(r * 9 + c)
                }
            }
        }

        val buffsPerPage = buffSlots.size
        val maxPages = ceil(allBuffKeys.size.toDouble() / buffsPerPage.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val currentPageBuffs = allBuffKeys.drop(page * buffsPerPage).take(buffsPerPage)

        val title = ChatColor.translateAlternateColorCodes('&', menuConfig.getString("title", "Buff Shop")!!)
        val holder = GuildMenuHolder(title, layout, buttons, page, menuName, player)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 5. 渲染循环
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val slot = r * 9 + c
                val char = layout[r][c].toString()
                val btnSection = buttons.getConfigurationSection(char) ?: continue
                val type = btnSection.getString("type")

                if (type == "BUFF_LIST") {
                    val relativeIdx = buffSlots.indexOf(slot)
                    if (relativeIdx != -1 && relativeIdx < currentPageBuffs.size) {
                        val buffKey = currentPageBuffs[relativeIdx]
                        val isUnlocked = allowedBuffs.contains(buffKey)
                        inv.setItem(slot, buildBuffItem(btnSection, buffKey, isUnlocked, player))
                    }
                } else {
                    inv.setItem(slot, buildNormalItem(btnSection, holder, maxPages, player))
                }
            }
        }
        player.openInventory(inv)
    }

    /**
     * 构建 Buff 物品
     * @param section 按钮配置
     * @param buffKey Buff 键
     * @param isUnlocked 是否解锁
     * @param viewer 玩家
     */
    private fun buildBuffItem(section: ConfigurationSection, buffKey: String, isUnlocked: Boolean, viewer: Player): ItemStack {
        val lang = plugin.langManager
        val buffConfig = plugin.buffsConfig.getConfigurationSection("buffs.$buffKey") ?: return ItemStack(Material.BARRIER)
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.PAPER)

        // 判断材质和状态
        val iconKey = if (isUnlocked) "buffs_unlock" else "buffs_lock"
        val status = if (isUnlocked) { lang.get("menu-text-buff-unlocked") } else { lang.get("menu-text-buff-locked") }

        // 使用配置的图标，如果配置不存在则使用默认的 PAPER
        val iconConfig = menuDefaultIcons[iconKey]
        val materialName = iconConfig?.material ?: if (isUnlocked) "HONEY_BOTTLE" else "GLASS_BOTTLE"

        val item = ItemStack(Material.matchMaterial(materialName) ?: Material.PAPER)
        val meta = item.itemMeta ?: return item

        // 如果有配置图标，应用配置的图标
        if (iconConfig != null) {
            applyMenuDefaultIcon(meta, iconKey)
        } else {
            setItemModel(meta, display)
        }

        // 准备变量替换
        val placeholders = mapOf(
            "buff_keyname" to buffKey,
            "buff_name" to (buffConfig.getString("name") ?: buffKey),
            "buff_price" to buffConfig.getDouble("price").toString(),
            "buff_time" to buffConfig.getInt("time", 90).toString(),
            "buff_status" to status
        )

        meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, viewer))

        // 构建lore列表，支持多行描述
        val loreLines = mutableListOf<String>()
        display.getStringList("lore").forEach { line ->
            val processedLine = applyPlaceholders(line, placeholders, viewer)
            // 如果行中包含换行符，拆分成多行
            if (processedLine.contains("\n")) {
                loreLines.addAll(processedLine.split("\n"))
            } else {
                loreLines.add(processedLine)
            }
        }
        meta.lore = loreLines

        // 写入 PDC 方便点击时识别（如果你想在监听器里处理的话，也可以直接用 action 处理）
        val key = NamespacedKey(plugin, "buff_keyname")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, buffKey)

        item.itemMeta = meta
        return item
    }
    /**
     * 渲染金库菜单
     * @param player 玩家
     * @param menuName 菜单名
     */
    private fun openVaultMenu(player: Player, menuName: String) {
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        if (!file.exists()) return

        val config = YamlConfiguration.loadConfiguration(file)
        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "Guild Vaults")!!)
        val layout = getLayout(config)
        val buttons = getButtonsSection(config) ?: return

        // 1. 获取公会等级数据
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: 0
        val guildData = plugin.dbManager.getGuildData(guildId)
        val level = guildData?.level ?: 1
        val unlockedCount = plugin.levelsConfig.getInt("levels.$level.vaults", 0)

        // 2. 创建 Holder 和 Inventory (关键：必须传入 holder 才能防止物品被取走)
        val holder = GuildMenuHolder(title, layout, buttons, 0, menuName, player)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 3. 渲染循环
        var vaultCounter = 1
        for (row in layout.indices) {
            val line = layout[row]
            for (col in line.indices) {
                val char = line[col].toString()
                if (char == " ") continue

                val btnSection = buttons.getConfigurationSection(char) ?: continue
                val slot = row * 9 + col

                if (btnSection.getString("type") == "GUILD_VAULTS") {
                    // 渲染金库图标
                    inv.setItem(slot, buildVaultItem(player, vaultCounter, unlockedCount, btnSection))
                    vaultCounter++
                } else {
                    // 渲染普通按钮 (传入 maxPages 为 1)
                    inv.setItem(slot, buildNormalItem(btnSection, holder, 1, player))
                }
            }
        }

        player.openInventory(inv)
    }

    /**
     * 构建单个金库图标
     * @param viewer 玩家
     * @param vaultNum 金库编号
     * @param unlockedCount 已解锁金库数量
     * @param section 按钮配置
     */
    private fun buildVaultItem(viewer: Player, vaultNum: Int, unlockedCount: Int, section: ConfigurationSection): ItemStack {
        val lang = plugin.langManager
        val isUnlocked = vaultNum <= unlockedCount
        val iconKey = if (isUnlocked) "vaults_unlock" else "vaults_lock"
        val status = if (isUnlocked) { lang.get("menu-text-vault-unlocked") } else { lang.get("menu-text-vault-locked") }

        // 使用配置的图标，如果配置不存在则使用默认的材质
        val iconConfig = menuDefaultIcons[iconKey]
        val materialName = iconConfig?.material ?: if (isUnlocked) "CHEST_MINECART" else "MINECART"

        val item = ItemStack(Material.matchMaterial(materialName) ?: Material.CHEST_MINECART)
        val meta = item.itemMeta ?: return item
        val display = section.getConfigurationSection("display")!!

        // 如果有配置图标，应用配置的图标
        if (iconConfig != null) {
            applyMenuDefaultIcon(meta, iconKey)
        } else {
            setItemModel(meta, display)
        }

        val placeholders = mapOf(
            "vault_num" to vaultNum.toString(),
            "vault_status" to status
        )

        meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, viewer))

        // 构建lore列表，支持多行描述
        val loreLines = mutableListOf<String>()
        display.getStringList("lore").forEach { line ->
            val processedLine = applyPlaceholders(line, placeholders, viewer)
            // 如果行中包含换行符，拆分成多行
            if (processedLine.contains("\n")) {
                loreLines.addAll(processedLine.split("\n"))
            } else {
                loreLines.add(processedLine)
            }
        }
        meta.lore = loreLines

        // 关键：存入 PDC 供监听器检查
        val vaultNumKey = NamespacedKey(plugin, "vault_num")
        val unlockedKey = NamespacedKey(plugin, "vault_unlocked")
        meta.persistentDataContainer.set(vaultNumKey, PersistentDataType.INTEGER, vaultNum)
        meta.persistentDataContainer.set(unlockedKey, PersistentDataType.INTEGER, if (isUnlocked) 1 else 0)

        item.itemMeta = meta
        return item
    }

    /**
     * 打开公会升级菜单
     * @param player 玩家
     * @param menuName 菜单名
     * @param page 当前页
     */
    fun openUpgradeMenu(player: Player, menuName: String, page: Int = 0) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return
        val guildData = plugin.dbManager.getGuildData(guildId) ?: return

        // 1. 获取所有配置好的等级列表 (从 levels.yml 的 levels 节点获取)
        val levelsSection = plugin.levelsConfig.getConfigurationSection("levels") ?: return
        val allLevelKeys = levelsSection.getKeys(false).toList().mapNotNull { it.toIntOrNull() }.sorted()

        // 2. 加载菜单布局
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        if (!file.exists()) return
        val menuConfig = YamlConfiguration.loadConfiguration(file)
        val layout = getLayout(menuConfig)
        val buttons = getButtonsSection(menuConfig) ?: return

        // 3. 计算分页槽位
        val upgradeSlots = mutableListOf<Int>()
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val char = layout[r][c].toString()
                if (buttons.getConfigurationSection(char)?.getString("type") == "GUILD_UPGRADE") {
                    upgradeSlots.add(r * 9 + c)
                }
            }
        }

        val itemsPerPage = upgradeSlots.size
        val maxPages = ceil(allLevelKeys.size.toDouble() / itemsPerPage.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val currentPageLevels = allLevelKeys.drop(page * itemsPerPage).take(itemsPerPage)

        val title = ChatColor.translateAlternateColorCodes('&', menuConfig.getString("title", "Guild Upgrade")!!)
        val holder = GuildMenuHolder(title, layout, buttons, page, menuName, player)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 4. 渲染循环
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val slot = r * 9 + c
                val char = layout[r][c].toString()
                val btnSection = buttons.getConfigurationSection(char) ?: continue
                val type = btnSection.getString("type")

                if (type == "GUILD_UPGRADE") {
                    val relativeIdx = upgradeSlots.indexOf(slot)
                    if (relativeIdx != -1 && relativeIdx < currentPageLevels.size) {
                        val targetLevel = currentPageLevels[relativeIdx]
                        // 传入当前公会等级进行状态判断
                        inv.setItem(slot, buildUpgradeItem(btnSection, targetLevel, guildData, player))
                    }
                } else {
                    inv.setItem(slot, buildNormalItem(btnSection, holder, maxPages, player))
                }
            }
        }
        player.openInventory(inv)
    }

    /**
     * 构建公会等级升级物品
     */
    private fun buildUpgradeItem(section: ConfigurationSection, targetLevel: Int, guildData: DatabaseManager.GuildData, viewer: Player): ItemStack {
        val lang = plugin.langManager
        val levelConfig = plugin.levelsConfig.getConfigurationSection("levels.$targetLevel") ?: return ItemStack(Material.BARRIER)
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.PAPER)

        // 1. 状态逻辑判断
        // 0: 锁定 (需按顺序), 1: 可升级 (经验够), 2: 经验不足, 3: 已达成
        var statusCode: Int
        val status = when {
            guildData.level >= targetLevel -> {
                statusCode = 3
                lang.get("menu-text-level-upgrade-done")
            }
            guildData.level == targetLevel - 1 -> {
                val needExp = levelConfig.getInt("need-exp")
                if (guildData.exp >= needExp) {
                    statusCode = 1
                    lang.get("menu-text-level-can-upgrade")
                } else {
                    statusCode = 2
                    lang.get("menu-text-level-not-exp")
                }
            }
            else -> {
                statusCode = 0
                lang.get("menu-text-level-locked")
            }
        }

        // 2. 状态判断图标键
        val iconKey = when {
            guildData.level >= targetLevel -> "levels_unlock"  // 已达成
            guildData.level == targetLevel - 1 -> {
                val needExp = levelConfig.getInt("need-exp")
                if (guildData.exp >= needExp) "levels_unlock" else "levels_lock"
            }
            else -> "levels_lock"  // 锁定
        }

        // 3. 动态材质 (使用配置的图标，如果配置不存在则使用默认的材质)
        val iconConfig = menuDefaultIcons[iconKey]
        val materialName = iconConfig?.material ?: if (guildData.level >= targetLevel) "ENCHANTED_BOOK" else "BOOK"

        val item = ItemStack(Material.matchMaterial(materialName) ?: Material.BOOK)
        val meta = item.itemMeta ?: return item

        // 如果有配置图标，应用配置的图标
        if (iconConfig != null) {
            applyMenuDefaultIcon(meta, iconKey)
        } else {
            setItemModel(meta, display)
        }

        // 3. 准备占位符变量
        val placeholders = mapOf(
            "upgrade_level" to targetLevel.toString(),
            "upgrade_max_members" to levelConfig.getInt("max-members").toString(),
            "upgrade_max_money" to levelConfig.getInt("max-money").toString(),
            "upgrade_max_vaults" to levelConfig.getInt("vaults").toString(),
            "upgrade_tp_money" to levelConfig.getInt("tp-money").toString(),
            "upgrade_use_buff" to (levelConfig.getStringList("use-buff").size).toString(),
            "upgrade_bank-interest" to (levelConfig.getStringList("bank-interest").size).toString(),
            "upgrade_current_exp" to guildData.exp.toString(),
            "upgrade_need_exp" to levelConfig.getInt("need-exp").toString(),
            "upgrade_status" to status
        )

        meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, viewer))

        // 构建lore列表，支持多行描述
        val loreLines = mutableListOf<String>()
        display.getStringList("lore").forEach { line ->
            val processedLine = applyPlaceholders(line, placeholders, viewer)
            // 如果行中包含换行符，拆分成多行
            if (processedLine.contains("\n")) {
                loreLines.addAll(processedLine.split("\n"))
            } else {
                loreLines.add(processedLine)
            }
        }
        meta.lore = loreLines

        // 4. 关键：存入 PDC 供监听器检查
        val upgradeLevelKey = NamespacedKey(plugin, "upgrade_level_num")
        val upgradeStatusKey = NamespacedKey(plugin, "upgrade_status_type")

        meta.persistentDataContainer.set(upgradeLevelKey, PersistentDataType.INTEGER, targetLevel)
        meta.persistentDataContainer.set(upgradeStatusKey, PersistentDataType.INTEGER, statusCode)

        item.itemMeta = meta
        return item
    }


    /**
     * 打开任务菜单
     * @param player 玩家
     * @param menuName 菜单名
     * @param page 当前页
     * @param taskType 任务类型: daily 或 global
     */
    fun openTaskMenu(player: Player, menuName: String, page: Int = 0, taskType: String = "daily") {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return

        // 1. 检查并重置过期的进度（异步）
        when (taskType) {
            "daily" -> {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    plugin.dbManager.checkAndResetDailyTasks(guildId, player.uniqueId)
                    // 延迟1tick再打开菜单，确保数据库更新完成
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        openTaskMenuInternal(player, menuName, page, taskType)
                    })
                })
            }
            "global" -> {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    plugin.dbManager.checkAndResetGlobalTasks(guildId)
                    // 延迟1tick再打开菜单，确保数据库更新完成
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        openTaskMenuInternal(player, menuName, page, taskType)
                    })
                })
            }
            else -> {
                // 无需重置，直接打开
                openTaskMenuInternal(player, menuName, page, taskType)
            }
        }
    }

    /**
     * 内部方法：实际打开任务菜单
     */
    private fun openTaskMenuInternal(player: Player, menuName: String, page: Int, taskType: String) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return

        // 1. 获取指定类型的任务
        val allTasks = plugin.taskManager.taskDefinitions.values.filter { it.type == taskType }
        val taskTypeKey = if (taskType == "daily") "TASK_DAILY" else "TASK_GLOBAL"

        // 2. 加载菜单布局
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        if (!file.exists()) return
        val menuConfig = YamlConfiguration.loadConfiguration(file)
        val layout = getLayout(menuConfig)
        val buttons = getButtonsSection(menuConfig) ?: return

        // 3. 计算分页槽位
        val taskSlots = mutableListOf<Int>()
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val char = layout[r][c].toString()
                if (buttons.getConfigurationSection(char)?.getString("type") == taskTypeKey) {
                    taskSlots.add(r * 9 + c)
                }
            }
        }

        val tasksPerPage = taskSlots.size
        val maxPages = ceil(allTasks.size.toDouble() / tasksPerPage.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val currentPageTasks = allTasks.drop(page * tasksPerPage).take(tasksPerPage)

        val title = ChatColor.translateAlternateColorCodes('&', menuConfig.getString("title", if (taskType == "daily") "Daily Tasks" else "Global Tasks")!!)
        val holder = GuildMenuHolder(title, layout, buttons, page, menuName, player)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 4. 渲染循环
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val slot = r * 9 + c
                val char = layout[r][c].toString()
                val btnSection = buttons.getConfigurationSection(char) ?: continue
                val type = btnSection.getString("type")

                if (type == taskTypeKey) {
                    val relativeIdx = taskSlots.indexOf(slot)
                    if (relativeIdx != -1 && relativeIdx < currentPageTasks.size) {
                        val task = currentPageTasks[relativeIdx]
                        inv.setItem(slot, buildTaskItem(btnSection, task, player, guildId))
                    }
                } else {
                    inv.setItem(slot, buildNormalItem(btnSection, holder, maxPages, player))
                }
            }
        }

        // 启动刷新任务
        val updateTicks = menuConfig.getLong("update", 0L)
        if (updateTicks > 0) {
            holder.updateTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                refreshMenu(holder)
            }, updateTicks, updateTicks)
        }

        player.openInventory(inv)
    }

    /**
     * 构建任务物品
     * @param section 按钮配置
     * @param task 任务定义
     * @param viewer 玩家
     * @param guildId 公会ID
     */
    private fun buildTaskItem(section: ConfigurationSection, task: TaskManager.TaskDefinition, viewer: Player, guildId: Int): ItemStack {
        val lang = plugin.langManager
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.PAPER)

        // 获取任务进度
        val progress = plugin.dbManager.getGuildTaskProgress(guildId, task.key, if (task.type == "daily") viewer.uniqueId else null)
        val currentProgress = progress?.progress ?: 0
        val isCompleted = currentProgress >= task.amount

        // 使用任务定义中的 display 配置（如果存在）
        if (task.display != null) {
            val taskItemDisplay = if (isCompleted) task.display.finished else task.display.unfinished

            // 使用任务定义的 display 配置
            val material = Material.matchMaterial(taskItemDisplay.material) ?: Material.PAPER
            val item = ItemStack(material)
            val meta = item.itemMeta ?: return item

            // 应用显示配置
            applyTaskItemDisplay(meta, taskItemDisplay)

            // 准备变量替换
            val placeholders = mapOf(
                "task_key" to task.key,
                "task_name" to task.name,
                "task_lore" to task.lore.joinToString("\n"),
                "task_progress" to currentProgress.toString(),
                "task_amount" to task.amount.toString(),
                "task_status" to (if (isCompleted) lang.get("task-status-completed") else lang.get("task-status-incomplete"))
            )

            meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, viewer))

            // 构建lore列表，支持多行描述
            val loreLines = mutableListOf<String>()
            display.getStringList("lore").forEach { line ->
                val processedLine = applyPlaceholders(line, placeholders, viewer)
                // 如果行中包含换行符，拆分成多行
                if (processedLine.contains("\n")) {
                    loreLines.addAll(processedLine.split("\n"))
                } else {
                    loreLines.add(processedLine)
                }
            }
            meta.lore = loreLines

            // 写入 PDC 供监听器检查
            val taskKey = NamespacedKey(plugin, "task_key")
            meta.persistentDataContainer.set(taskKey, PersistentDataType.STRING, task.key)

            item.itemMeta = meta
            return item
        }

        // 任务没有配置 display，使用 config 中的默认图标
        val iconKey = if (isCompleted) "tasks_finished" else "tasks_unfinished"
        val iconConfig = menuDefaultIcons[iconKey]
        val materialName = iconConfig?.material ?: if (isCompleted) "ENCHANTED_BOOK" else "BOOK"
        val material = Material.matchMaterial(materialName) ?: Material.BOOK
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        // 如果有配置图标，应用配置的图标
        if (iconConfig != null) {
            applyMenuDefaultIcon(meta, iconKey)
        } else {
            // 回退到菜单配置的 display
            setItemModel(meta, display)
        }

        // 准备变量替换
        val placeholders = mapOf(
            "task_key" to task.key,
            "task_name" to task.name,
            "task_lore" to task.lore.joinToString("\n"),
            "task_progress" to currentProgress.toString(),
            "task_amount" to task.amount.toString(),
            "task_status" to (if (isCompleted) lang.get("task-status-completed") else lang.get("task-status-incomplete"))
        )

        meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, viewer))

        // 构建lore列表，支持多行描述
        val loreLines = mutableListOf<String>()
        display.getStringList("lore").forEach { line ->
            val processedLine = applyPlaceholders(line, placeholders, viewer)
            // 如果行中包含换行符，拆分成多行
            if (processedLine.contains("\n")) {
                loreLines.addAll(processedLine.split("\n"))
            } else {
                loreLines.add(processedLine)
            }
        }
        meta.lore = loreLines

        // 写入 PDC 供监听器检查
        val taskKey = NamespacedKey(plugin, "task_key")
        meta.persistentDataContainer.set(taskKey, PersistentDataType.STRING, task.key)

        item.itemMeta = meta
        return item
    }

    fun reload() {
        menuCache.clear()
        menuDefaultIcons.clear()
        loadMenuDefaultIcons()
        val guiFolder = File(plugin.dataFolder, "gui")
        if (!guiFolder.exists()) guiFolder.mkdirs()

        plugin.server.onlinePlayers.forEach { player ->
            if (player.openInventory.topInventory.holder is GuildMenuHolder) {
                player.closeInventory()
            }
        }
    }

    /**
     * 获取跨服在线玩家列表
     * @return 玩家名列表
     */
    private fun getCrossServerOnlinePlayers(): List<String> {
        val isProxy = plugin.config.getBoolean("proxy", false)
        return if (isProxy) {
            plugin.crossServerOnlinePlayers.keys.toList()
        } else {
            plugin.server.onlinePlayers.map { it.name }
        }
    }

    /**
     * 通过玩家名获取 UUID
     * @param playerName 玩家名
     * @return 玩家UUID，未找到返回null
     */
    private fun getUUIDByName(playerName: String): UUID? {
        // 先尝试从在线玩家中获取
        val onlinePlayer = plugin.server.onlinePlayers.firstOrNull { it.name.equals(playerName, ignoreCase = true) }
        if (onlinePlayer != null) return onlinePlayer.uniqueId

        // 如果在线玩家中没有，从数据库获取
        return plugin.dbManager.getUuidByPlayerName(playerName)
    }
}