package org.katacr.kaguilds.listener

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockFertilizeEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.katacr.kaguilds.KaGuilds

/**
 * 任务监听器
 * 监听游戏事件并触发任务进度更新
 */
class TaskListener(private val plugin: KaGuilds) : Listener {

    /**
     * 玩家登录事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // 1. 清除过期的每日任务缓存（确保新的一天任务状态正确）
        plugin.taskManager.clearExpiredDailyCache(player)

        // 2. 延迟触发 login 任务事件
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.taskManager.handleEvent(player, "login")
        }, 20L * 2) // 2秒后触发
    }

    /**
     * 玩家退出游戏事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        // 清理玩家的每日任务缓存
        plugin.taskManager.clearDailyCache(player.uniqueId)
    }

    /**
     * 实体死亡事件 - 击杀怪物任务
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.isCancelled) return
        val killer = event.entity.killer ?: return

        // 获取实体类型
        val entityType = event.entity.type.name.lowercase()

        // 触发击杀怪物事件
        plugin.taskManager.handleEvent(killer, "kill_mobs", entityType)
    }

    /**
     * 方块破坏事件 - 挖矿任务
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val blockType = event.block.type

        // 如果你的任务是"挖掘任何矿石"，可以在这里先做一次聚合判断
        // 否则直接传入具体的方块名
        plugin.taskManager.handleEvent(player, "break_block", blockType.name.lowercase())
    }

    /**
     * 处理公会捐赠事件
     * 由 GuildCommand 调用
     */
    fun onGuildDonate(player: Player, amount: Double) {
        // 捐赠金额作为增量值传递
        plugin.taskManager.handleEvent(player, "donate", "*", amount.toInt())
    }

    /**
     * 钓鱼事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerFish(event: PlayerFishEvent) {
        if (event.isCancelled) return
        val player = event.player

        // 检查是否成功钓到鱼
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH) return

        plugin.taskManager.handleEvent(player, "fishing", "*")
    }

    /**
     * 桶填满事件 (装水、装岩浆、挤奶)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBucketFill(event: org.bukkit.event.player.PlayerBucketFillEvent) {
        val player = event.player
        val itemStack = event.itemStack // 这是填满后的物品

        // 检查填满后是否为奶桶
        itemStack?.let {
            if (it.type == Material.MILK_BUCKET) {
                plugin.taskManager.handleEvent(player, "milk", "cow")
            }
        }
    }

    /**
     * 剪羊毛事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onShearSheep(event: org.bukkit.event.player.PlayerShearEntityEvent) {
        if (event.isCancelled) return
        val player = event.player

        // 检查是否是羊
        if (event.entity is org.bukkit.entity.Sheep) {
            plugin.taskManager.handleEvent(player, "shear", "sheep")
        }
    }

    /**
     * 优化：村民交易事件 (支持批量交易与按住 Shift)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onVillagerTrade(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory as? org.bukkit.inventory.MerchantInventory ?: return

        // 1. 必须是点击了村民交易的产出槽位 (Slot 2)
        if (event.rawSlot != 2) return

        // 2. 获取当前选择的交易配方
        val recipe = inventory.selectedRecipe ?: return
        val resultItem = recipe.result
        val recipeAmount = resultItem.amount

        // 3. 获取点击动作
        val isShift = event.isShiftClick

        // 我们需要知道点击前玩家背包里有多少个该物品（用于 Shift 交易计算）
        val amountBefore = if (isShift) getItemAmount(player, resultItem.type) else 0

        // 4. 延迟 1 刻执行，等待服务器处理完背包数据变更
        plugin.server.scheduler.runTask(plugin, Runnable {
            val tradeCount = if (isShift) {
                // Shift 点击逻辑：通过计算背包差值来反推交易次数
                val amountAfter = getItemAmount(player, resultItem.type)
                val diff = amountAfter - amountBefore
                if (diff > 0) diff / recipeAmount else 0
            } else {
                // 普通点击：只要光标上拿到了物品，就记为 1 次交易
                1
            }

            if (tradeCount > 0) {
                plugin.taskManager.handleEvent(player, "trade", resultItem.type.name.lowercase(), tradeCount)
            }
        })
    }

    // 辅助方法：获取玩家背包中某种物品的总量
    private fun getItemAmount(player: Player, material: Material): Int {
        return player.inventory.contents
            .filterNotNull()
            .filter { it.type == material }
            .sumOf { it.amount }
    }

    /**
     * 食用食物事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerItemConsume(event: PlayerItemConsumeEvent) {
        if (event.isCancelled) return
        val player = event.player
        val item = event.item

        // 获取食物类型
        val foodType = item.type.name.lowercase()

        plugin.taskManager.handleEvent(player, "eat_food", foodType)
    }

    /**
     * 监听骨粉施肥成功的事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockFertilize(event: BlockFertilizeEvent) {
        val player = event.player ?: return
        val blockType = event.block.type

        // 骨粉确实生效时此事件才会被触发。
        plugin.taskManager.handleEvent(player, "bonemeal", blockType.name.lowercase())
    }

    /**
     * 玩家取出熔炼成品
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory

        if (inventory.type != InventoryType.FURNACE &&
            inventory.type != InventoryType.BLAST_FURNACE &&
            inventory.type != InventoryType.SMOKER) return

        if (event.rawSlot != 2) return

        val item = event.currentItem ?: return
        if (item.type == Material.AIR) return

        val amount = when {
            event.isShiftClick -> item.amount
            event.isLeftClick || event.isRightClick -> {
                item.amount
            }
            else -> 0
        }

        if (amount <= 0) return

        plugin.taskManager.handleEvent(player, "smelt", item.type.name.lowercase(), amount)
    }
}
