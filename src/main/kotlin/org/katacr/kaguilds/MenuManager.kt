package org.katacr.kaguilds

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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import org.bukkit.inventory.Inventory


class MenuManager(private val plugin: KaGuilds) {
    private val menuCache = mutableMapOf<String, YamlConfiguration>()

    /**
     * 菜单统一入口
     */
    fun openMenu(player: Player, menuName: String, page: Int = 0) {
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        if (!file.exists()) {
            player.sendMessage("§c菜单 $menuName 不存在！")
            return
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val buttons = config.getConfigurationSection("button") ?: return

        // 检查按钮类型
        var isGuildList = false
        var isMemberList = false
        var isBuffList = false
        var isVaultList = false // 新增

        for (key in buttons.getKeys(false)) {
            val type = buttons.getConfigurationSection(key)?.getString("type")
            when (type) {
                "GUILDS_LIST" -> isGuildList = true
                "MEMBERS_LIST" -> isMemberList = true
                "BUFF_LIST" -> isBuffList = true
                "GUILD_VAULTS" -> isVaultList = true // 新增
            }
        }

        when {
            isMemberList -> openMemberListMenu(player, menuName, page)
            isGuildList -> openGuildListMenu(player, menuName, page)
            isBuffList -> openBuffShopMenu(player, menuName, page)
            isVaultList -> openVaultMenu(player, menuName) // 新增金库渲染入口
            else -> renderStandardMenu(player, config, menuName)
        }
    }

    /**
     * 标准菜单渲染
     */
    private fun renderStandardMenu(player: Player, config: YamlConfiguration, menuName: String) {
        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "Menu")!!)
        val layout = config.getStringList("Layout")
        val buttons = config.getConfigurationSection("button")

        // 这里 page 默认为 0，普通菜单通常不需要分页逻辑
        val holder = GuildMenuHolder(title, layout, buttons, 0, menuName)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 填充图标
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
                refreshMenuContent(player, inv, holder)
            }, updateTicks, updateTicks)
        }

        player.openInventory(inv)
    }
    /**
     * 公会列表菜单渲染 (优化分页查询版)
     */
    fun openGuildListMenu(player: Player, menuName: String, page: Int = 0) {
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        if (!file.exists()) return

        val config = YamlConfiguration.loadConfiguration(file)
        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "Guild List")!!)
        val layout = config.getStringList("Layout")
        val buttons = config.getConfigurationSection("button") ?: return

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
            kotlin.math.ceil(totalGuilds.toDouble() / guildsPerPage).toInt().coerceAtLeast(1)
        } else 1

        // 确保请求页码不越界
        val safePage = page.coerceIn(0, maxPages - 1)

        // 关键优化：只从数据库加载当前页面所需的公会数据
        val currentPageGuilds = if (guildsPerPage > 0) {
            plugin.dbManager.getGuildsByPage(safePage, guildsPerPage)
        } else emptyList()

        val holder = GuildMenuHolder(title, layout, buttons, safePage, menuName)
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
     * 构建公会项，应用 {变量} 逻辑
     */
    private fun buildGuildItem(section: ConfigurationSection, guild: DatabaseManager.GuildData, player: Player): ItemStack {
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.PAPER)
        val material = Material.getMaterial((guild.icon ?: "PAPER").uppercase()) ?: Material.PAPER

        val amount = display.getInt("amount", 1).coerceIn(1, 64)
        val item = ItemStack(material, amount)
        val meta = item.itemMeta ?: return item

        if (display.contains("custom_data")) {
            meta.setCustomModelData(display.getInt("custom_data"))
        }

        val key = NamespacedKey(plugin, "guild_id")
        meta.persistentDataContainer.set(key, PersistentDataType.INTEGER, guild.id)

        val placeholders = getGuildPlaceholders(guild, player)
        meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, player))
        meta.lore = display.getStringList("lore").map { applyPlaceholders(it, placeholders, player) }

        item.itemMeta = meta
        return item
    }

    /**
     * 成员列表菜单渲染
     */
    fun openMemberListMenu(player: Player, menuName: String, page: Int = 0) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        val layout = config.getStringList("Layout")
        val buttons = config.getConfigurationSection("button") ?: return

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
        val holder = GuildMenuHolder(title, layout, buttons, page, menuName)
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

        // --- 启动定时刷新任务 ---
        val updateTicks = config.getLong("update", 0L)
        if (updateTicks > 0) {
            holder.updateTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                // 定时刷新逻辑
                refreshMenuContent(player, inv, holder)
            }, updateTicks, updateTicks)
        }

        player.openInventory(inv)
    }

    /**
     * 构建成员项
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
            "members_name" to (member.name ?: "未知"),
            "members_role" to roleDisplay,
            "members_join_time" to SimpleDateFormat(plugin.config.getString("date-format", "yyyy-MM-dd HH:mm:ss")!!).format(Date(member.joinTime))
        )

        meta.setDisplayName(applyPlaceholders(section.getConfigurationSection("display")?.getString("name") ?: "", placeholders, viewer))
        meta.lore = section.getConfigurationSection("display")?.getStringList("lore")?.map { applyPlaceholders(it, placeholders, viewer) }

        item.itemMeta = meta

        // 4. 调用异步加载逻辑：物品会先显示默认皮肤，随后自动变成玩家皮肤
        loadSkinAsync(item, member.name, viewer)

        return item
    }

    /**
     * 构建普通项（翻页、装饰等）
     */
    private fun buildNormalItem(section: ConfigurationSection, holder: GuildMenuHolder, maxPages: Int = 1, player: Player): ItemStack {
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.STONE)
        val materialName = display.getString("material", "STONE")!!.uppercase()

        // 读取数量和自定义材质数据
        val amount = display.getInt("amount", 1).coerceIn(1, 64)
        val item = ItemStack(Material.getMaterial(materialName) ?: Material.STONE, amount)
        val meta = item.itemMeta ?: return item

        if (display.contains("custom_data")) {
            meta.setCustomModelData(display.getInt("custom_data"))
        }

        // 基础变量
        val placeholders = mutableMapOf(
            "page" to (holder.currentPage + 1).toString(),
            "total_pages" to maxPages.toString(),
            "player" to player.name
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
     */
    private fun applyPlaceholders(text: String, placeholders: Map<String, String>, viewer: Player): String {
        var result = text

        // 1. 优先处理插件内部变量: 使用 {} 格式
        placeholders.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }

        // 2. 处理 PAPI 变量: 使用 %% 格式
        if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, result)
        }

        // 3. 最后转换颜色
        return ChatColor.translateAlternateColorCodes('&', result)
    }

    /**
     * 获取指定公会数据的变量映射表
     */
    fun getGuildPlaceholders(guild: DatabaseManager.GuildData, player: Player): Map<String, String> {
        val onlineCount = plugin.playerGuildCache.values.count { it == guild.id }
        val formatPattern = plugin.config.getString("date-format", "yyyy-MM-dd HH:mm:ss")!!
        val createTimeStr = SimpleDateFormat(formatPattern).format(Date(guild.createTime))

        return mapOf(
            "id" to guild.id.toString(),
            "name" to guild.name,
            "level" to guild.level.toString(),
            "members" to guild.memberCount.toString(),
            "max_members" to guild.maxMembers.toString(),
            "online" to onlineCount.toString(),
            "balance" to String.format("%.2f", guild.balance),
            "announcement" to (guild.announcement ?: "暂无公告"),
            "create_time" to createTimeStr,
            "owner" to (guild.ownerName ?: "未知"),
            "player" to player.name,
            "role" to (plugin.dbManager.getPlayerRole(player.uniqueId) ?: "NONE"),
            "role_node" to when (plugin.dbManager.getPlayerRole(player.uniqueId)) {
                "OWNER" -> "3"
                "ADMIN" -> "2"
                "MEMBER" -> "1"
                else -> "0"
            }
        )
    }

    /**
     * 异步加载皮肤
     */
    private fun loadSkinAsync(item: ItemStack, playerName: String?, viewer: Player) {
        if (playerName.isNullOrBlank()) return
        val meta = item.itemMeta as? SkullMeta ?: return
        meta.owningPlayer = Bukkit.getOfflinePlayer(playerName)
        item.itemMeta = meta

    }

    /**
     * 局部刷新菜单内容 (供 update 任务调用)
     */
    fun refreshMenuContent(player: Player, inv: Inventory, holder: GuildMenuHolder) {
        val layout = holder.layout
        val buttons = holder.buttons

        // 获取公会 ID (成员列表需要用)
        val guildId = plugin.playerGuildCache[player.uniqueId]

        for (r in layout.indices) {
            val line = layout[r]
            for (c in line.indices) {
                val slot = r * 9 + c
                val char = line[c].toString()
                if (char == " ") continue

                val btnSection = buttons?.getConfigurationSection(char) ?: continue

            }
        }
    }

    /**
     * 打开 Buff 商店菜单
     */
    fun openBuffShopMenu(player: Player, menuName: String, page: Int = 0) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return
        val guildData = plugin.dbManager.getGuildData(guildId) ?: return // 假设你有获取公会数据的方法
        val currentLevel = guildData.level

        // 1. 获取当前等级解锁的 Buff 列表
        val allowedBuffs = plugin.config.getStringList("level.$currentLevel.use-buff")

        // 2. 获取所有的 Buff 配置 (从 config.yml)
        val buffsSection = plugin.config.getConfigurationSection("guild.buffs") ?: return
        val allBuffKeys = buffsSection.getKeys(false).toList() // 获取 NightVision, Speed 等

        // 3. 加载菜单布局
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        val menuConfig = YamlConfiguration.loadConfiguration(file)
        val layout = menuConfig.getStringList("Layout")
        val buttons = menuConfig.getConfigurationSection("button") ?: return

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
        val holder = GuildMenuHolder(title, layout, buttons, page, menuName)
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
     */
    private fun buildBuffItem(section: ConfigurationSection, buffKey: String, isUnlocked: Boolean, viewer: Player): ItemStack {
        val buffConfig = plugin.config.getConfigurationSection("guild.buffs.$buffKey") ?: return ItemStack(Material.BARRIER)
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.GLASS_BOTTLE)

        // 判断材质和状态
        val materialName = if (isUnlocked) "HONEY_BOTTLE" else "GLASS_BOTTLE"
        val status = if (isUnlocked) "§a可购买" else "§c已锁定 (需要公会升级)"

        val item = ItemStack(Material.matchMaterial(materialName) ?: Material.GLASS_BOTTLE)
        val meta = item.itemMeta ?: return item

        if (display.contains("custom_data")) {
            meta.setCustomModelData(display.getInt("custom_data"))
        }
        // 准备变量替换
        val placeholders = mapOf(
            "buff_keyname" to buffKey,
            "buff_name" to (buffConfig.getString("name") ?: buffKey),
            "buff_price" to buffConfig.getDouble("price").toString(),
            "buff_time" to plugin.config.getInt("guild.buff-time").toString(),
            "buff_status" to status
        )

        meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, viewer))
        meta.lore = display.getStringList("lore").map { applyPlaceholders(it, placeholders, viewer) }

        // 写入 PDC 方便点击时识别（如果你想在监听器里处理的话，也可以直接用 action 处理）
        val key = NamespacedKey(plugin, "buff_keyname")
        meta.persistentDataContainer.set(key, PersistentDataType.STRING, buffKey)

        item.itemMeta = meta
        return item
    }
    /**
     * 渲染金库菜单
     */
    private fun openVaultMenu(player: Player, menuName: String) {
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        if (!file.exists()) return

        val config = YamlConfiguration.loadConfiguration(file)
        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "公会金库")!!)
        // 兼容 Layout 和 layout 两种写法
        val layout = config.getStringList("Layout").ifEmpty { config.getStringList("layout") }
        val buttons = config.getConfigurationSection("button") ?: return

        // 1. 获取公会等级数据
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: 0
        val guildData = plugin.dbManager.getGuildData(guildId)
        val level = guildData?.level ?: 1
        val unlockedCount = plugin.config.getInt("level.$level.vaults", 0)

        // 2. 创建 Holder 和 Inventory (关键：必须传入 holder 才能防止物品被取走)
        val holder = GuildMenuHolder(title, layout, buttons, 0, menuName)
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
     */
    private fun buildVaultItem(viewer: Player, vaultNum: Int, unlockedCount: Int, section: ConfigurationSection): ItemStack {
        val isUnlocked = vaultNum <= unlockedCount
        val materialName = if (isUnlocked) "CHEST_MINECART" else "MINECART"
        val status = if (isUnlocked) "§a已解锁" else "§c已锁定"

        val item = ItemStack(Material.matchMaterial(materialName) ?: Material.CHEST_MINECART)
        val meta = item.itemMeta ?: return item
        val display = section.getConfigurationSection("display")!!

        if (display.contains("custom_data")) {
            meta.setCustomModelData(display.getInt("custom_data"))
        }
        val placeholders = mapOf(
            "vault_num" to vaultNum.toString(),
            "vault_status" to status
        )

        meta.setDisplayName(applyPlaceholders(display.getString("name", "")!!, placeholders, viewer))
        meta.lore = display.getStringList("lore").map { applyPlaceholders(it, placeholders, viewer) }

        // 关键：存入 PDC 供监听器检查
        val vaultNumKey = NamespacedKey(plugin, "vault_num")
        val unlockedKey = NamespacedKey(plugin, "vault_unlocked")
        meta.persistentDataContainer.set(vaultNumKey, PersistentDataType.INTEGER, vaultNum)
        meta.persistentDataContainer.set(unlockedKey, PersistentDataType.INTEGER, if (isUnlocked) 1 else 0)

        item.itemMeta = meta
        return item
    }


    fun reload() {
        menuCache.clear()
        val guiFolder = File(plugin.dataFolder, "gui")
        if (!guiFolder.exists()) guiFolder.mkdirs()

        plugin.server.onlinePlayers.forEach { player ->
            if (player.openInventory.topInventory.holder is GuildMenuHolder) {
                player.closeInventory()
            }
        }
        plugin.logger.info("已重新加载所有 GUI 配置文件。")
    }
}