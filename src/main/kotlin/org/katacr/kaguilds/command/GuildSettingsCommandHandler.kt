package org.katacr.kaguilds.command

import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Material
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.service.GuildService
import org.katacr.kaguilds.service.OperationResult
import org.katacr.kaguilds.util.MessageUtil

/**
 * 处理公会设置类玩家命令，包括传送点、改名、图标、公告和升级。
 */
class GuildSettingsCommandHandler(private val plugin: KaGuilds) {

    fun handleSetTp(player: Player) {
        val lang = plugin.langManager

        if (!checkPermission(player, "settp")) return

        plugin.guildService.setGuildTP(player) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val cost = plugin.config.getDouble("balance.settp", 1000.0)
                    player.sendMessage(lang.get("tp-set-success"))
                    player.sendMessage(lang.get("tp-set-cost", "cost" to cost.toString()))
                }
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleTp(player: Player) {
        val lang = plugin.langManager

        if (!checkPermission(player, "tp")) return

        plugin.guildService.teleportToGuild(player) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("tp-success"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleRename(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkPermission(player, "rename")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("rename-usage"))
            return
        }

        val newName = args[1]

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)

            if (guildId == null) {
                player.sendMessage(lang.get("not-in-guild"))
                return@Runnable
            }

            val guildData = plugin.dbManager.guildRepository.getGuildData(guildId)

            if (guildData?.ownerUuid != player.uniqueId.toString()) {
                player.sendMessage(lang.get("not-staff"))
                return@Runnable
            }

            plugin.server.scheduler.runTask(plugin, Runnable {
                player.sendMessage(lang.get("rename-confirm", "name" to newName))
                plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Rename(newName))
                sendConfirmHint(player)
            })
        })
    }

    fun performRename(player: Player, newName: String) {
        val lang = plugin.langManager

        plugin.guildService.renameGuild(player, newName) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val price = plugin.config.getDouble("balance.rename", 3000.0).toString()
                    player.sendMessage(
                        lang.get(
                            "rename-success",
                            "name" to newName,
                            "price" to price
                        )
                    )
                }
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleSetIcon(player: Player) {
        val lang = plugin.langManager

        if (!checkPermission(player, "seticon")) return

        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) {
            player.sendMessage(lang.get("error-no-item"))
            return
        }

        val materialName = item.type.name

        plugin.guildService.setGuildIcon(player, materialName) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val cost = plugin.config.getDouble("balance.seticon", 1000.0)
                    player.sendMessage(lang.get("seticon-success", "cost" to cost.toString(), "material" to materialName))
                }
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleMotd(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkPermission(player, "motd")) return

        if (args.size < 2) {
            player.sendMessage(lang.get("motd-usage"))
            return
        }

        val content = args.slice(1 until args.size).joinToString(" ")

        if (content.length > 64) {
            player.sendMessage(lang.get("motd-too-long"))
            return
        }

        val forbiddenPattern = Regex("""['";\\<>{}\[\]§&]""")
        if (forbiddenPattern.containsMatchIn(content)) {
            player.sendMessage(lang.get("motd-forbidden-char"))
            return
        }

        plugin.guildService.setGuildMotd(player, content) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val cost = plugin.config.getDouble("balance.setmotd", 100.0)
                    player.sendMessage(lang.get("motd-success", "cost" to cost.toString()))
                }
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    fun handleUpgrade(player: Player) {
        val lang = plugin.langManager

        if (!checkPermission(player, "upgrade")) return

        plugin.guildService.upgradeGuild(player) { result ->
            if (result is OperationResult.Error) player.sendMessage(result.message)
            else player.sendMessage(lang.get("menu-upgrade-level-up-msg"))
        }
    }

    private fun checkPermission(player: Player, command: String? = null): Boolean {
        val lang = plugin.langManager

        if (player.hasPermission("kaguilds.use")) return true
        if (command != null && player.hasPermission("kaguilds.command.$command")) return true

        player.sendMessage(lang.get("no-permission"))
        return false
    }

    private fun sendConfirmHint(player: Player) {
        val lang = plugin.langManager
        val prefix = TextComponent(lang.get("confirm-hint-prefix"))
        val clickBtn = MessageUtil.createClickableText(
            lang.get("confirm-hint-button"),
            lang.get("confirm-hint-button-hover"),
            "/kg confirm"
        )
        val suffix = TextComponent(lang.get("confirm-hint-suffix"))

        player.spigot().sendMessage(prefix, clickBtn, suffix)
    }
}
