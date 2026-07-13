package org.katacr.kaguilds.command

import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.service.OperationResult
import org.katacr.kaguilds.util.MessageUtil

/**
 * 处理 /kg pvp 子命令，负责公会对战邀请、接受、准备和退出流程。
 */
class PvpCommandHandler(private val plugin: KaGuilds) {

    fun handle(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        if (!checkPermission(player, "pvp")) {
            return
        }

        if (args.size < 2) {
            player.sendMessage(lang.get("arena-pvp-help-header"))
            player.sendMessage(lang.get("arena-pvp-help-start"))
            player.sendMessage(lang.get("arena-pvp-help-accept"))
            player.sendMessage(lang.get("arena-pvp-help-ready"))
            player.sendMessage(lang.get("arena-pvp-help-exit"))
            return
        }

        val action = args[1].lowercase()
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: run {
            player.sendMessage(lang.get("error-no-guild"))
            return
        }

        when (action) {
            "start" -> handleStart(player, args, guildId)
            "accept" -> handleAccept(player, guildId)
            "ready" -> handleReady(player, guildId)
            "exit" -> handleExit(player)
            else -> player.sendMessage(lang.get("error-missing-args"))
        }
    }

    private fun handleStart(player: Player, args: Array<out String>, guildId: Int) {
        val lang = plugin.langManager

        if (args.size < 3) {
            player.sendMessage(lang.get("arena-pvp-start-usage"))
            return
        }

        if (plugin.pvpManager.currentMatch != null) {
            player.sendMessage(lang.get("arena-pvp-error-arena-busy"))
            return
        }

        val targetInput = args[2]
        val targetGuild = if (targetInput.startsWith("#")) {
            plugin.dbManager.guildRepository.getGuildData(targetInput.substring(1).toIntOrNull() ?: -1)
        } else {
            plugin.dbManager.guildRepository.getGuildByName(targetInput)
        }

        if (targetGuild == null || targetGuild.id == guildId) {
            player.sendMessage(lang.get("arena-pvp-error-invalid-target"))
            return
        }

        val isTargetStaffOnline = plugin.server.onlinePlayers.any { onlinePlayer ->
            val onlinePlayerGuildId = plugin.playerGuildCache[onlinePlayer.uniqueId]
            onlinePlayerGuildId == targetGuild.id && plugin.dbManager.memberRepository.isStaff(onlinePlayer.uniqueId, targetGuild.id)
        }
        if (!isTargetStaffOnline) {
            player.sendMessage(lang.get("arena-pvp-error-target-offline"))
            return
        }

        val senderRemainingCooldown = plugin.pvpManager.checkCooldown(guildId)
        if (senderRemainingCooldown > 0) {
            player.sendMessage(lang.get("arena-pvp-error-cooldown-sender", "time" to senderRemainingCooldown.toString()))
            return
        }

        val targetRemainingCooldown = plugin.pvpManager.checkCooldown(targetGuild.id)
        if (targetRemainingCooldown > 0) {
            player.sendMessage(lang.get("arena-pvp-error-cooldown-target", "time" to targetRemainingCooldown.toString()))
            return
        }

        plugin.guildService.startPvPChallenge(player, targetGuild) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val cost = plugin.config.getDouble("balance.pvp", 300.0)
                    player.sendMessage(lang.get("arena-pvp-challenge-sent", "fee" to cost.toString()))

                    val myGuildName = plugin.dbManager.guildRepository.getGuildData(guildId)?.name ?: "Unknown"
                    plugin.pvpManager.notifyTargetGuild(targetGuild.id, myGuildName)
                }
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    private fun handleAccept(player: Player, guildId: Int) {
        val lang = plugin.langManager

        if (!plugin.dbManager.memberRepository.isStaff(player.uniqueId, guildId)) {
            player.sendMessage(lang.get("not-staff"))
            return
        }

        val senderId = plugin.pvpManager.acceptChallenge(guildId)
        if (senderId != null) {
            val match = plugin.pvpManager.currentMatch
            val senderName = plugin.dbManager.guildRepository.getGuildData(senderId)?.name ?: "Opponent"

            match?.smartBroadcast(lang.get("arena-pvp-accept-broadcast", "player" to player.name, "sender" to senderName))

            val msg = MessageUtil.createText(lang.get("arena-pvp-ready-hint"))
            val readyBtn = MessageUtil.createClickableText(
                text = lang.get("arena-pvp-ready-btn"),
                hoverText = lang.get("arena-pvp-ready-btn-hover"),
                command = "/kg pvp ready"
            )

            msg.addExtra(readyBtn)
            match?.smartBroadcastText(msg)
        } else {
            player.sendMessage(lang.get("arena-pvp-error-no-invite"))
        }
    }

    private fun handleReady(player: Player, guildId: Int) {
        val lang = plugin.langManager
        val match = plugin.pvpManager.currentMatch ?: run {
            player.sendMessage(lang.get("arena-pvp-error-no-match"))
            return
        }

        if (match.isStarted) {
            player.sendMessage(lang.get("arena-pvp-error-started"))
            return
        }

        val maxPerTeam = plugin.config.getInt("guild.arena.max-players", 5)
        val currentInTeam = match.players.count { plugin.playerGuildCache[it] == guildId }

        if (match.players.contains(player.uniqueId)) {
            player.sendMessage(lang.get("arena-pvp-already-ready"))
            return
        }

        if (currentInTeam >= maxPerTeam) {
            player.sendMessage(lang.get("arena-pvp-error-team-full", "max" to maxPerTeam.toString()))
            return
        }

        val arena = plugin.arenaManager.arena
        if (arena.redSpawn == null || arena.blueSpawn == null) {
            player.sendMessage(lang.get("arena-pvp-no-spawn"))
            return
        }

        if (match.players.add(player.uniqueId)) {
            val guildName = plugin.dbManager.guildRepository.getGuildData(guildId)?.name ?: "Unknown"
            val isRed = guildId == match.redGuildId
            val teamDisplay = if (isRed) lang.get("arena-pvp-red-team-name") else lang.get("arena-pvp-blue-team-name")

            match.smartBroadcast(
                lang.get(
                    "arena-pvp-join-broadcast",
                    "team" to teamDisplay,
                    "guild" to guildName,
                    "player" to player.name
                )
            )

            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
        }
    }

    private fun handleExit(player: Player) {
        val lang = plugin.langManager
        val match = plugin.pvpManager.currentMatch
        if (match == null || !match.players.contains(player.uniqueId)) {
            player.sendMessage(lang.get("arena-pvp-error-not-in-match"))
            return
        }

        match.players.remove(player.uniqueId)
        plugin.pvpManager.restoreSnapshot(player)
        player.gameMode = GameMode.SURVIVAL
        player.teleport(player.world.spawnLocation)

        player.sendMessage(lang.get("arena-pvp-exit-success"))

        if (match.isStarted) {
            match.smartBroadcast(lang.get("arena-pvp-exit-broadcast", "player" to player.name))
            plugin.pvpManager.checkWinCondition()
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
