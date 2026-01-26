package org.katacr.kaguilds

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import org.bukkit.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.katacr.kaguilds.listener.GuildMenuHolder

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

        // 检查这个菜单里是否有任何按钮的 type 是 GUILDS_LIST
        val isGuildList = buttons.getKeys(false).any { key ->
            buttons.getConfigurationSection(key)?.getString("type") == "GUILDS_LIST"
        }

        if (isGuildList) {
            // 如果是公会列表菜单，走专门的动态渲染逻辑
            openGuildListMenu(player, menuName, page)
        } else {
            // 如果是普通菜单，走标准渲染逻辑
            renderStandardMenu(player, config)
        }
    }

    /**
     * 标准菜单渲染（非列表）
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
                inv.setItem(slot, buildNormalItem(btnSection, holder))
            }
        }
        player.openInventory(inv)
    }

    /**
     * 公会列表菜单渲染（动态分页）
     */
    fun openGuildListMenu(player: Player, menuName: String, page: Int = 0) {
        val file = File(plugin.dataFolder, "gui/$menuName.yml")
        if (!file.exists()) return

        val config = YamlConfiguration.loadConfiguration(file)
        val title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "Guild List")!!)
        val layout = config.getStringList("Layout")
        val buttons = config.getConfigurationSection("button") ?: return

        val holder = GuildMenuHolder(title, layout, buttons, page)
        val inv = Bukkit.createInventory(holder, layout.size * 9, title)
        holder.setInventory(inv)

        // 1. 获取所有公会数据
        val allGuilds = plugin.dbManager.getAllGuilds()
        val totalGuilds = allGuilds.size

        // 2. 统计布局中共有多少个用于展示公会的槽位 (字符对应配置 type 为 GUILDS_LIST 的)
        val listSlots = mutableListOf<Int>()
        for (r in layout.indices) {
            for (c in layout[r].indices) {
                val char = layout[r][c].toString()
                val btnSection = buttons.getConfigurationSection(char)
                if (btnSection?.getString("type") == "GUILDS_LIST") {
                    listSlots.add(r * 9 + c)
                }
            }
        }

        // 3. 计算分页信息
        val guildsPerPage = listSlots.size
        val startIdx = page * guildsPerPage

        // 计算总页数 (向上取整，确保至少为 1)
        val maxPages = if (guildsPerPage > 0) {
            kotlin.math.ceil(totalGuilds.toDouble() / guildsPerPage).toInt().coerceAtLeast(1)
        } else 1

        // 4. 开始填充容器
        for (r in layout.indices) {
            val line = layout[r]
            for (c in line.indices) {
                val slot = r * 9 + c
                val char = line[c].toString()
                if (char == " ") continue // 空白字符跳过

                val btnSection = buttons.getConfigurationSection(char) ?: continue

                // 判断按钮类型
                if (btnSection.getString("type") == "GUILDS_LIST") {
                    // 处理动态公会列表项
                    val relativeIdx = listSlots.indexOf(slot)
                    val guildIdx = startIdx + relativeIdx

                    if (guildIdx < totalGuilds) {
                        // 在数据范围内，渲染公会信息
                        inv.setItem(slot, buildGuildItem(btnSection, allGuilds[guildIdx]))
                    } else {
                        // 超出数据范围，设为空气，防止显示上一页的残留或多余图标
                        inv.setItem(slot, null)
                    }
                } else {
                    // 处理普通按钮（边框、翻页等），传入计算好的 maxPages
                    inv.setItem(slot, buildNormalItem(btnSection, holder, maxPages))
                }
            }
        }

        player.openInventory(inv)
    }

    /**
     * 构建公会菜单项
     */
    private fun buildGuildItem(section: ConfigurationSection, guild: DatabaseManager.GuildData): ItemStack {
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.PAPER)

        // 修复：必须先替换变量再获取 Material
        val rawMaterial = display.getString("material", "PAPER")!!
        val materialName = rawMaterial.replace("%guilds_icon%", guild.icon ?: "PAPER").uppercase()
        val material = Material.getMaterial(materialName) ?: Material.PAPER

        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        // 注入 ID 到 NBT
        val key = org.bukkit.NamespacedKey(plugin, "guild_id")
        meta.persistentDataContainer.set(key, org.bukkit.persistence.PersistentDataType.INTEGER, guild.id)

        val formatPattern = plugin.config.getString("date-format", "yyyy-MM-dd HH:mm:ss")!!
        val dateFormat = try {
            java.text.SimpleDateFormat(formatPattern)
        } catch (e: Exception) {
            // 防止用户填错格式导致报错，回退到默认值
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        }
        val createTimeStr = dateFormat.format(java.util.Date(guild.createTime))

        // 变量替换器
        val replacer = { s: String ->
            s.replace("%guilds_name%", guild.name)
                .replace("%guilds_id%", guild.id.toString())
                .replace("%guilds_level%", guild.level.toString())
                .replace("%guilds_members%", guild.memberCount.toString())
                .replace("%guilds_max_members%", guild.maxMembers.toString())
                .replace("%guilds_create_time%", createTimeStr)
                .replace("%guilds_announcement%", guild.announcement ?: "暂无公告")
                .replace("&", "§")
        }

        meta.setDisplayName(replacer(display.getString("name", "")!!))
        meta.lore = display.getStringList("lore").map { replacer(it) }

        item.itemMeta = meta
        return item
    }

    /**
     * 构建普通菜单项
     */
    private fun buildNormalItem(section: ConfigurationSection, holder: GuildMenuHolder, maxPages: Int = 1): ItemStack {
        val display = section.getConfigurationSection("display") ?: return ItemStack(Material.STONE)
        val materialName = display.getString("material", "STONE")!!

        val item = ItemStack(Material.getMaterial(materialName.uppercase()) ?: Material.STONE)
        val meta = item.itemMeta ?: return item

        // 变量替换
        val replacer = { s: String ->
            s.replace("%page%", (holder.currentPage + 1).toString())
                .replace("%total_pages%", maxPages.toString())
                .replace("&", "§")
        }

        meta.setDisplayName(replacer(display.getString("name", "")!!))
        meta.lore = display.getStringList("lore").map { replacer(it) }

        item.itemMeta = meta
        return item
    }

    /**
     * 重新加载所有菜单配置文件
     */
    fun reload() {
        menuCache.clear()
        val guiFolder = File(plugin.dataFolder, "gui")
        if (!guiFolder.exists()) guiFolder.mkdirs()

        // 关闭所有正在看菜单的玩家界面
        plugin.server.onlinePlayers.forEach { player ->
            if (player.openInventory.topInventory.holder is GuildMenuHolder) {
                player.closeInventory()
            }
        }
        plugin.logger.info("已重新加载所有 GUI 配置文件。")
    }
}