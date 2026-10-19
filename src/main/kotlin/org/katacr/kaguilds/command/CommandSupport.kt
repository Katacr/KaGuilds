package org.katacr.kaguilds.command

import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.MessageUtil

/**
 * 指令层公共辅助：权限检查、确认提示等，供各 *CommandHandler 复用，避免逐字复制。
 */
object CommandSupport {

    /**
     * 玩家命令权限：拥有 kaguilds.use 或 kaguilds.command.<command> 之一即可。
     * 不通过时发送 no-permission 并返回 false。
     */
    fun checkPermission(plugin: KaGuilds, player: Player, command: String? = null): Boolean {
        if (player.hasPermission("kaguilds.use")) return true
        if (command != null && player.hasPermission("kaguilds.command.$command")) return true
        player.sendMessage(plugin.langManager.get("no-permission"))
        return false
    }

    /**
     * 管理员命令权限：拥有 kaguilds.admin 或 kaguilds.admin.<action> 之一即可。
     */
    fun checkAdminPermission(plugin: KaGuilds, sender: CommandSender, action: String? = null): Boolean {
        if (sender.hasPermission("kaguilds.admin")) return true
        if (action != null && sender.hasPermission("kaguilds.admin.$action")) return true
        sender.sendMessage(plugin.langManager.get("no-permission"))
        return false
    }

    /**
     * 为未被显式处理的失败结果生成默认提示文案，避免静默无反馈。
     * 成功或无法归类的返回 null。
     */
    fun failureMessage(plugin: KaGuilds, result: org.katacr.kaguilds.service.OperationResult): String? {
        val lang = plugin.langManager
        return when (result) {
            is org.katacr.kaguilds.service.OperationResult.Success -> null
            is org.katacr.kaguilds.service.OperationResult.NoPermission -> lang.get("no-permission")
            is org.katacr.kaguilds.service.OperationResult.NotInGuild -> lang.get("not-in-guild")
            is org.katacr.kaguilds.service.OperationResult.AlreadyInGuild -> lang.get("join-already")
            is org.katacr.kaguilds.service.OperationResult.NameAlreadyExists -> lang.get("create-name-exists")
            is org.katacr.kaguilds.service.OperationResult.InsufficientFunds -> lang.get("error-balance-not-enough")
            is org.katacr.kaguilds.service.OperationResult.Error -> result.message
        }
    }

    /**
     * 发送"输入 /kg confirm 确认"的点击提示。
     */
    fun sendConfirmHint(plugin: KaGuilds, player: Player) {
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
