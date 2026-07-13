package org.katacr.kaguilds.command

import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.service.OperationResult
import kotlin.math.ceil
import kotlin.math.min

/**
 * 处理公会信息展示、帮助页和菜单入口。
 */
class GuildInfoCommandHandler(private val plugin: KaGuilds) {

    fun handleInfo(player: Player) {
        val lang = plugin.langManager

        if (!checkPermission(player, "info")) return

        plugin.guildService.getDetailedInfo(player) { result, info ->
            when (result) {
                is OperationResult.Success -> {
                    val d = info!!.data
                    val guildId = plugin.playerGuildCache[player.uniqueId] ?: return@getDetailedInfo
                    val coloredList = renderMemberList(info.memberNames, guildId)

                    player.sendMessage(lang.get("info-header", "name" to d.name, "id" to d.id.toString()))
                    player.sendMessage(lang.get("info-owner", "owner" to (d.ownerName ?: "Unknown")))
                    player.sendMessage(lang.get("info-level", "level" to d.level.toString(), "exp" to d.exp.toString()))
                    player.sendMessage(lang.get("info-balance", "balance" to d.balance.toString()))
                    player.sendMessage(lang.get("info-members", "current" to info.memberNames.size.toString(), "max" to d.maxMembers.toString(), "online" to info.onlineCount.toString()))
                    player.sendMessage(lang.get("info-list", "list" to coloredList))
                    player.sendMessage(lang.get("info-announcement", "announcement" to (d.announcement ?: "None")))
                    player.sendMessage(lang.get("info-footer"))
                }
                is OperationResult.NotInGuild -> player.sendMessage(lang.get("not-in-guild"))
                else -> player.sendMessage("§cError: $result")
            }
        }
    }

    fun sendHelp(sender: Player, page: Int) {
        val lang = plugin.langManager

        if (!sender.hasPermission("kaguilds.use") && !sender.hasPermission("kaguilds.command.help")) {
            sender.sendMessage(lang.get("no-permission"))
            return
        }

        val allCmds = listOf(
            "help [page]" to "desc-help",
            "menu" to "desc-menu",
            "create <Name>" to "desc-create",
            "join <Name>" to "desc-join",
            "info" to "desc-info",
            "requests" to "desc-requests",
            "accept <Player>" to "desc-accept",
            "deny <Player>" to "desc-deny",
            "promote <Player>" to "desc-promote",
            "demote <Player>" to "desc-demote",
            "kick <Player>" to "desc-kick",
            "leave" to "desc-leave",
            "delete" to "desc-delete",
            "invite <Player>" to "desc-invite",
            "confirm" to "desc-confirm",
            "yes" to "desc-yes",
            "no" to "desc-no",
            "reload" to "desc-reload",
            "buff <BuffName>" to "desc-buff",
            "bank <add|take|log>" to "desc-bank",
            "chat <Message>" to "desc-chat",
            "rename <NewName>" to "desc-rename",
            "settp" to "desc-settp",
            "tp" to "desc-tp",
            "seticon" to "desc-seticon",
            "motd <text>" to "desc-motd",
            "transfer <Player>" to "desc-transfer",
            "pvp <start|ready|exit|accept>" to "desc-pvp",
            "vault <num>" to "desc-vault",
            "upgrade" to "desc-upgrade"
        ).filter {
            if (it.first == "reload") sender.hasPermission("kaguilds.admin") else true
        }

        val pageSize = 10
        val maxPage = ceil(allCmds.size.toDouble() / pageSize).toInt()
        val currentPage = when {
            page < 1 -> 1
            page > maxPage -> maxPage
            else -> page
        }

        sender.sendMessage(lang.get("help-header"))

        val start = (currentPage - 1) * pageSize
        val end = min(start + pageSize, allCmds.size)

        for (i in start until end) {
            val (cmd, descKey) = allCmds[i]
            sender.sendMessage(" §6/guild $cmd §7- §f${lang.get(descKey)}")
        }

        val footer = lang.get("help-footer", "page" to currentPage.toString(), "max" to maxPage.toString())
        sender.sendMessage(footer)

        if (currentPage < maxPage) {
            sender.sendMessage(lang.get("help-next-page", "page" to (currentPage + 1).toString()))
        }
    }

    fun handleMenu(player: Player) {
        if (!checkPermission(player, "menu")) return

        openDefaultMenu(player)
    }

    fun openDefaultMenu(player: Player) {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId)
        val menuName = if (guildId == null) "guild_create" else "main_menu"
        plugin.menuManager.openMenu(player, menuName)
    }

    private fun renderMemberList(memberNames: List<String>, guildId: Int): String {
        val onlineNames = plugin.server.onlinePlayers
            .filter { plugin.playerGuildCache[it.uniqueId] == guildId }
            .map { it.name }
            .toSet()

        return memberNames.joinToString("§7, ") { name ->
            if (onlineNames.contains(name)) "§a$name" else "§f$name"
        }
    }

    private fun checkPermission(player: Player, command: String? = null): Boolean {
        val lang = plugin.langManager

        if (player.hasPermission("kaguilds.use")) return true
        if (command != null && player.hasPermission("kaguilds.command.$command")) return true

        player.sendMessage(lang.get("no-permission"))
        return false
    }
}
