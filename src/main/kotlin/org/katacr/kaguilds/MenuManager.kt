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
import net.skinsrestorer.api.SkinsRestorerProvider
import net.skinsrestorer.api.property.SkinProperty
import java.lang.reflect.Field
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property


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

        val isGuildList = buttons.getKeys(false).any { key ->
            buttons.getConfigurationSection(key)?.getString("type") == "GUILDS_LIST"
        }

        val isMemberList = buttons.getKeys(false).any { key ->
            buttons.getConfigurationSection(key)?.getString("type") == "MEMBERS_LIST"
        }

        if (isMemberList) {
            openMemberListMenu(player, menuName, page)
        } else if (isGuildList) {
            openGuildListMenu(player, menuName, page)
        } else {
            renderStandardMenu(player, config)
        }
    }

    /**
     * 标准菜单渲染
     */
    private fun renderStandardMenu(player: Player, config: YamlConfiguration) {
        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "Menu")!!)
        val layout = config.getStringList("Layout")
        val buttons = config.getConfigurationSection("button") ?: return

        val holder = GuildMenuHolder(title, layout, buttons, 0)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        for (row in layout.indices) {
            val line = layout[row]
            for (col in line.indices) {
                val char = line[col].toString()
                if (char == " ") continue

                val btnSection = buttons.getConfigurationSection(char) ?: continue
                val slot = row * 9 + col

                // 修复点：在这里传入 player 参数
                inv.setItem(slot, buildNormalItem(btnSection, holder, 1, player))
            }
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

        val holder = GuildMenuHolder(title, layout, buttons, safePage)
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

        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        // 写入 PDC 供点击监听器使用
        val key = NamespacedKey(plugin, "guild_id")
        meta.persistentDataContainer.set(key, PersistentDataType.INTEGER, guild.id)

        // 直接调用通用变量方法
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
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return player.sendMessage("§c你不在公会中！")
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        val layout = config.getStringList("Layout")
        val buttons = config.getConfigurationSection("button") ?: return

        val allMembers = plugin.dbManager.getGuildMembers(guildId)
        val totalMembers = allMembers.size

        val listSlots = mutableListOf<Int>()
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val char = layout[r][c].toString()
                val btnSection = buttons.getConfigurationSection(char)
                if (btnSection?.getString("type") == "MEMBERS_LIST") {
                    listSlots.add(r * 9 + c)
                }
            }
        }

        val membersPerPage = listSlots.size
        val maxPages = ceil(totalMembers.toDouble() / membersPerPage.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val currentPageMembers = allMembers.drop(page * membersPerPage).take(membersPerPage)

        val holder = GuildMenuHolder(config.getString("title")!!, layout, buttons, page)
        val inv = Bukkit.createInventory(holder, layout.size * 9, ChatColor.translateAlternateColorCodes('&', config.getString("title")!!))
        holder.setInventory(inv)

        // 2. 填充
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val slot = r * 9 + c
                val char = layout[r][c].toString()
                val btnSection = buttons.getConfigurationSection(char) ?: continue

                if (btnSection.getString("type") == "MEMBERS_LIST") {
                    val relativeIdx = listSlots.indexOf(slot)
                    if (relativeIdx != -1 && relativeIdx < currentPageMembers.size) {
                        inv.setItem(slot, buildMemberItem(btnSection, currentPageMembers[relativeIdx], player))
                    }
                } else {
                    inv.setItem(slot, buildNormalItem(btnSection, holder, maxPages, player))
                }
            }
        }
        player.openInventory(inv)
    }

    /**
     * 构建成员项
     */
    private fun buildMemberItem(section: ConfigurationSection, member: DatabaseManager.MemberData, viewer: Player): ItemStack {
        // 1. 创建头颅 ItemStack，此时它是空白/默认皮肤
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return item

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
        loadSkinAsync(item, member.uuid, member.name)

        return item
    }

    /**
     * 构建普通项（翻页、装饰等）
     */
    private fun buildNormalItem(section: ConfigurationSection, holder: GuildMenuHolder, maxPages: Int = 1, player: Player): ItemStack {
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.STONE)
        val materialName = display.getString("material", "STONE")!!.uppercase()
        val item = ItemStack(Material.getMaterial(materialName) ?: Material.STONE)
        val meta = item.itemMeta ?: return item

        // 基础变量
        val placeholders = mutableMapOf(
            "page" to (holder.currentPage + 1).toString(),
            "total_pages" to maxPages.toString(),
            "player" to player.name
        )

        // --- 关键增强：注入玩家所属公会的变量 ---
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
    private fun applyPlaceholders(text: String, internalMap: Map<String, String>, player: Player?): String {
        var result = text

        // 1. 替换内置变量 {key}
        internalMap.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }

        // 2. 如果启用了 PlaceholderAPI，进行替换
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && player != null) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, result)
        }

        // 3. 颜色转换
        return ChatColor.translateAlternateColorCodes('&', result)
    }

    /**
     * 获取指定公会数据的变量映射表
     */
    private fun getGuildPlaceholders(guild: DatabaseManager.GuildData, player: Player): Map<String, String> {
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
     * 异步加载皮肤：优先从 SkinsRestorer 获取
     */
    private fun loadSkinAsync(item: ItemStack, uuid: UUID, playerName: String?) {
        val plugin = plugin
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                // 1. 获取 API 实例 (符合文档 Getting the API instance)
                val skinsRestorerAPI = SkinsRestorerProvider.get()
                val playerStorage = skinsRestorerAPI.playerStorage

                // 2. 获取玩家当前皮肤 (符合文档 Getting a player's current skin)
                // 注意：SkinsRestorer 建议同时提供 UUID 和 Name 以获得最佳兼容性
                val propertyOptional = playerStorage.getSkinForPlayer(uuid, playerName)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    val meta = item.itemMeta as? SkullMeta ?: return@Runnable

                    if (propertyOptional.isPresent) {
                        // 3. 拿到 SkinProperty 后，注入到头颅 Meta
                        applySkinToMeta(meta, propertyOptional.get())
                    } else {
                        // 如果 SR 没数据，退回到 Bukkit 默认获取
                        meta.owningPlayer = plugin.server.getOfflinePlayer(uuid)
                    }
                    item.itemMeta = meta
                })
            } catch (e: Exception) {
                // 兜底：如果插件没加载或报错
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta ?: return@Runnable
                    meta.owningPlayer = plugin.server.getOfflinePlayer(uuid)
                    item.itemMeta = meta
                })
            }
        })
    }

    /**
     * 反射注入核心逻辑
     */
    private fun applySkinToMeta(meta: org.bukkit.inventory.meta.SkullMeta, skinProperty: net.skinsrestorer.api.property.SkinProperty) {
        try {
            // 1. 获取 AuthLib 的类 (通过反射，避开编译依赖问题)
            val profileClass = Class.forName("com.mojang.authlib.GameProfile")
            val propertyClass = Class.forName("com.mojang.authlib.properties.Property")

            // 2. 实例化 GameProfile: new GameProfile(UUID, null)
            val profile = profileClass.getConstructor(UUID::class.java, String::class.java)
                .newInstance(UUID.randomUUID(), null)

            // 3. 实例化 Property: new Property("textures", value, signature)
            // 从 skinProperty 获取数据 (符合 SR 文档)
            val texturesProperty = propertyClass.getConstructor(String::class.java, String::class.java, String::class.java)
                .newInstance("textures", skinProperty.value, skinProperty.signature)

            // 4. 获取 profile.getProperties() 并存入 textures
            val propertiesField = profileClass.getMethod("getProperties")
            val propertyMap = propertiesField.invoke(profile)
            val putMethod = propertyMap.javaClass.getMethod("put", Object::class.java, Object::class.java)
            putMethod.invoke(propertyMap, "textures", texturesProperty)

            // 5. 查找并设置 SkullMeta 中的 profile 字段
            // 注意：不同版本 meta 实现类不同，这里用循环查找最保险
            var currentClass: Class<*>? = meta.javaClass
            var profileField: java.lang.reflect.Field? = null

            while (currentClass != null) {
                try {
                    profileField = currentClass.getDeclaredField("profile")
                    break
                } catch (e: NoSuchFieldException) {
                    currentClass = currentClass.superclass
                }
            }

            profileField?.let {
                it.isAccessible = true
                it.set(meta, profile)
            }

        } catch (e: Exception) {
            // 如果反射彻底失败，输出一下错误以便调试
            // plugin.logger.warning("无法注入皮肤数据: ${e.message}")
        }
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