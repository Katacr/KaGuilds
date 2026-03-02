package org.katacr.kaguilds.listener

import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.data.Ageable
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockFertilizeEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTameEvent
import org.bukkit.event.entity.SheepRegrowWoolEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
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
        // 延迟触发,确保玩家完全加载
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.taskManager.handleEvent(player, "login")
        }, 20L * 2) // 2秒后触发
    }

    /**
     * 实体死亡事件 - 击杀怪物任务
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.isCancelled) return
        val killer = event.entity.killer ?: return
        if (killer !is Player) return

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

        // 如果你的任务是“挖掘任何矿石”，可以在这里先做一次聚合判断
        // 否则直接传入具体的方块名
        plugin.taskManager.handleEvent(player, "break_block", blockType.name.lowercase())
    }

    /**
     * 处理公会聊天事件
     * 由 GuildService 调用
     */
    fun onGuildChat(player: Player) {
        plugin.taskManager.handleEvent(player, "chat")
    }

    /**
     * 处理公会捐赠事件
     * 由 GuildService 调用
     */
    fun onGuildDonate(player: Player, amount: Double) {
        plugin.taskManager.handleEvent(player, "donate", "*")
    }

    /**
     * 处理公会战胜利事件
     * 由 PvPManager 调用
     */
    fun onPvPWin(guildId: Int, player: Player) {
        plugin.taskManager.handleEvent(player, "pvp_win")
    }

    /**
     * 处理邀请成员事件
     * 由 GuildService 调用
     */
    fun onInviteMember(player: Player) {
        plugin.taskManager.handleEvent(player, "invite_member")
    }

    /**
     * 处理升级公会事件
     * 由 GuildService 调用
     */
    fun onGuildUpgrade(player: Player) {
        plugin.taskManager.handleEvent(player, "upgrade")
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
     * 挤奶事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerInteractEntity(event: org.bukkit.event.player.PlayerInteractEntityEvent) {
        if (event.isCancelled) return
        val player = event.player

        // 检查是否是挤奶桶
        val item = event.player.inventory.itemInMainHand
        if (item.type == Material.MILK_BUCKET) {
            // 获取实体类型
            val entity = event.rightClicked
            val entityType = entity.type.name.lowercase()

            plugin.taskManager.handleEvent(player, "milk", entityType)
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
     * 村民交易事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onTradeSelect(event: org.bukkit.event.inventory.TradeSelectEvent) {
        if (event.isCancelled) return
        val player = event.view.player as? Player ?: return

        plugin.taskManager.handleEvent(player, "trade_villager", "*")
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
}
