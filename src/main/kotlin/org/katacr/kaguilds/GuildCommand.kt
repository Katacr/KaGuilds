package org.katacr.kaguilds

import com.google.common.io.ByteStreams
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaguilds.service.OperationResult
import java.util.UUID

class GuildCommand(private val plugin: KaGuilds) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        val lang = plugin.langManager

        // 1. 帮助与控制台基础检查
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            if (sender is Player) sendHelp(sender) else sender.sendMessage(lang.get("console-help"))
            return true
        }

        val subCommand = args[0].lowercase()

        // 2. 处理 reload (支持控制台)
        if (subCommand == "reload") {
            if (!sender.hasPermission("kaguilds.admin")) {
                sender.sendMessage(lang.get("no-permission"))
                return true
            }
            plugin.reloadConfig()
            plugin.langManager.load()
            sender.sendMessage(lang.get("reload-success"))
            return true
        }

        if (sender !is Player) {
            sender.sendMessage(lang.get("player-only"))
            return true
        }

        when (subCommand) {
            "info" -> handleInfo(sender)
            "create" -> handleCreate(sender, args)
            "invite" -> handleInvite(sender, args)
            "join" -> handleJoin(sender, args)
            "requests" -> handleRequests(sender)
            "accept" -> handleAccept(sender, args)
            "deny" -> handleDeny(sender, args)
            "promote" -> handleRoleChange(sender, args, "ADMIN", lang.get("role-admin"))
            "demote" -> handleRoleChange(sender, args, "MEMBER", lang.get("role-member"))
            "leave" -> handleLeave(sender)
            "delete" -> handleDelete(sender)
            "kick" -> handleKick(sender, args)
            "chat" -> handleChat(sender, args)
            "bank" -> handleBank(sender, args)
            else -> {
                sender.sendMessage(lang.get("unknown-command"))
                sender.sendMessage(lang.get("help-hint"))
            }
        }
        return true
    }
    private fun handleLeave(player: Player) {
        plugin.guildService.leaveGuild(player) { result ->
            val lang = plugin.langManager
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("leave-success"))
                is OperationResult.Error -> player.sendMessage(result.message)
                is OperationResult.NotInGuild -> player.sendMessage(lang.get("not-in-guild"))
                else -> {}
            }
        }
    }

    private fun handleChat(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        // 检查参数：/kg chat <消息...>
        if (args.size < 2) {
            player.sendMessage(lang.get("chat-usage"))
            return
        }

        // 合并消息内容
        val message = args.sliceArray(1 until args.size).joinToString(" ")
        plugin.guildService.sendGuildChat(player, message)
    }

    private fun handleBank(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 1. 基础检查
        if (args.size < 2) {
            player.sendMessage(lang.get("bank-usage"))
            return
        }

        val action = args[1].lowercase()

        // --- 分支：银行日志 (log) ---
        if (action == "log") {
            val page = if (args.size >= 3) args[2].toIntOrNull() ?: 1 else 1
            if (page < 1) {
                player.sendMessage(lang.get("bank-log-invalid-page"))
                return
            }

            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId) ?: return@Runnable

                // 权限检查：通常只有管理层能看日志
                if (!plugin.dbManager.isStaff(player.uniqueId, guildId)) {
                    player.sendMessage(lang.get("not-staff"))
                    return@Runnable
                }

                val totalPages = plugin.dbManager.getBankLogTotalPages(guildId)
                if (page > totalPages && totalPages > 0) {
                    player.sendMessage(lang.get("bank-log-invalid-page"))
                    return@Runnable
                }

                val logs = plugin.dbManager.getBankLogs(guildId, page)
                player.sendMessage(lang.get("bank-log-header", "page" to page.toString(), "total" to totalPages.toString()))

                if (logs.isEmpty()) {
                    player.sendMessage(lang.get("bank-log-empty"))
                } else {
                    logs.forEach { player.sendMessage(it) }
                    if (page < totalPages) {
                        player.sendMessage("§7使用 §f/kg bank log ${page + 1} §7查看下一页")
                    }
                }
            })
            return
        }

        // --- 分支：存取款金额校验 ---
        if (args.size < 3) {
            player.sendMessage(lang.get("bank-usage"))
            return
        }

        val amount = args[2].toDoubleOrNull()
        if (amount == null || amount <= 0) {
            player.sendMessage(lang.get("bank-invalid-amount"))
            return
        }

        val econ = plugin.economy ?: return // 经济系统检查

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId) ?: return@Runnable
            val guildData = plugin.dbManager.getGuildData(guildId) ?: return@Runnable

            // 从配置文件获取当前等级的金库上限
            val maxBank = plugin.config.getDouble("level.${guildData.level}.max-money", 50000.0)

            when (action) {
                "add" -> {
                    // 1. 检查金库是否已满
                    if (guildData.balance + amount > maxBank) {
                        player.sendMessage(lang.get("bank-full", "max" to maxBank.toString()))
                        return@Runnable
                    }

                    // 2. 检查并扣除玩家个人余额 (回到同步线程操作 Vault)
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        if (!econ.has(player, amount)) {
                            player.sendMessage(lang.get("bank-insufficient-player"))
                            return@Runnable
                        }
                        econ.withdrawPlayer(player, amount)

                        // 3. 更新数据库公会余额并记录日志 (回到异步线程)
                        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                            if (plugin.dbManager.updateGuildBalance(guildId, amount)) {
                                plugin.dbManager.logBankTransaction(guildId, player.name, "ADD", amount)
                                player.sendMessage(lang.get("bank-add-success", "amount" to amount.toString()))
                            }
                        })
                    })
                }

                "get" -> {
                    // 1. 权限检查：只有管理层能取钱
                    if (!plugin.dbManager.isStaff(player.uniqueId, guildId)) {
                        player.sendMessage(lang.get("not-staff"))
                        return@Runnable
                    }

                    // 2. 检查公会余额是否足够
                    if (guildData.balance < amount) {
                        player.sendMessage(lang.get("bank-insufficient-guild"))
                        return@Runnable
                    }

                    // 3. 更新数据库扣款
                    if (plugin.dbManager.updateGuildBalance(guildId, -amount)) {
                        plugin.dbManager.logBankTransaction(guildId, player.name, "GET", amount)

                        // 4. 发放金币给玩家 (回到同步线程)
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            econ.depositPlayer(player, amount)
                            player.sendMessage(lang.get("bank-get-success", "amount" to amount.toString()))
                        })
                    }
                }
                else -> player.sendMessage(lang.get("bank-usage"))
            }
        })
    }

    private fun handleKick(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("error-missing-args"))
            player.sendMessage(plugin.langManager.get("kick-usage"))
            return
        }
        plugin.guildService.kickMember(player, args[1]) { result ->
            val lang = plugin.langManager
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("kick-success-sender", "name" to args[1]))
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage("§c${result.message}")
                else -> {}
            }
        }
    }

    private fun handleDelete(player: Player) {
        plugin.guildService.deleteGuild(player) { result ->
            val lang = plugin.langManager
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("delete-success"))
                is OperationResult.NoPermission -> player.sendMessage(lang.get("delete-only-owner"))
                is OperationResult.NotInGuild -> player.sendMessage(lang.get("not-in-guild"))
                is OperationResult.Error -> player.sendMessage("§c${result.message}")
                else -> {}
            }
        }
    }

    private fun handleCreate(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("error-missing-args"))
            player.sendMessage(plugin.langManager.get("create-usage"))
            return
        }
        val name = args[1]
        plugin.guildService.createGuild(player, name) { result ->
            val lang = plugin.langManager
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("create-success", "name" to name))
                is OperationResult.AlreadyInGuild -> player.sendMessage(lang.get("already-in-guild"))
                is OperationResult.NameAlreadyExists -> player.sendMessage(lang.get("name-exists"))
                is OperationResult.InsufficientFunds -> player.sendMessage(lang.get("create-insufficient-funds", "cost" to result.required.toString()))
                is OperationResult.Error -> player.sendMessage("§c${result.message}")
                else -> {}
            }
        }
    }

    private fun handleInfo(player: Player) {
        plugin.guildService.getDetailedInfo(player) { result, info ->
            when (result) {
                is OperationResult.Success -> {
                    val d = info!!.data
                    val lang = plugin.langManager
                    player.sendMessage(lang.get("info-header", "name" to d.name, "id" to d.id.toString()))
                    player.sendMessage(lang.get("info-owner", "owner" to (d.ownerName ?: "Unknown")))
                    player.sendMessage(lang.get("info-level", "level" to d.level.toString(), "exp" to d.exp.toString()))
                    player.sendMessage(lang.get("info-balance", "balance" to d.balance.toString()))
                    player.sendMessage(lang.get("info-members", "current" to info.memberNames.size.toString(), "max" to d.maxMembers.toString(), "online" to info.onlineCount.toString()))
                    player.sendMessage(lang.get("info-announcement", "announcement" to (d.announcement ?: "None")))
                    player.sendMessage(lang.get("info-footer"))
                }
                is OperationResult.NotInGuild -> player.sendMessage(plugin.langManager.get("not-in-guild"))
                else -> player.sendMessage("§cError: ${result.toString()}")
            }
        }
    }

    private fun handleRoleChange(sender: Player, args: Array<out String>, newRole: String, roleDisplay: String) {
        val lang = plugin.langManager
        if (args.size < 2) {
            sender.sendMessage(lang.get("promote-usage", "cmd" to args[0]))
            return
        }
        val targetName = args[1]
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(sender.uniqueId) ?: return@Runnable
            if (plugin.dbManager.getPlayerRole(sender.uniqueId) != "OWNER") {
                sender.sendMessage(lang.get("only-owner-can-manage"))
                return@Runnable
            }
            val targetOffline = plugin.server.getOfflinePlayer(targetName)
            val currentRole = plugin.dbManager.getRoleInGuild(guildId, targetOffline.uniqueId)
            when (currentRole) {
                null -> sender.sendMessage(lang.get("target-not-member", "name" to targetName))
                "OWNER" -> sender.sendMessage(lang.get("promote-cannot-self"))
                newRole -> sender.sendMessage(lang.get("already-has-role", "name" to targetName, "role" to roleDisplay))
                else -> {
                    if (plugin.dbManager.updateMemberRole(guildId, targetOffline.uniqueId, newRole)) {
                        sender.sendMessage(lang.get("promote-success", "name" to targetName, "action" to args[0]))
                        targetOffline.player?.sendMessage(lang.get("role-updated-target", "role" to roleDisplay))
                    }
                }
            }
        })
    }
    private fun handleInvite(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("error-missing-args"))
            player.sendMessage(plugin.langManager.get("invite-usage"))
            return
        }
        plugin.guildService.invitePlayer(player, args[1]) { result ->
            val lang = plugin.langManager
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("invite-success", "player" to args[1]))
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.NotInGuild -> player.sendMessage(lang.get("not-in-guild"))
                else -> {}
            }
        }
    }

    private fun handleJoin(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("error-missing-args"))
            player.sendMessage(plugin.langManager.get("join-usage"))
            return
        }
        plugin.guildService.requestJoin(player, args[1]) { result ->
            val lang = plugin.langManager
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("join-success", "guild" to args[1]))
                is OperationResult.AlreadyInGuild -> player.sendMessage(lang.get("already-in-guild"))
                is OperationResult.Error -> player.sendMessage("§c${result.message}")
                else -> {}
            }
        }
    }

    private fun handleRequests(player: Player) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
                ?: return@Runnable player.sendMessage(plugin.langManager.get("not-in-guild"))

            // 数据库返回的是 List<Pair<UUID, Long>>
            val requests = plugin.dbManager.getRequests(guildId)
            val lang = plugin.langManager

            player.sendMessage(lang.get("requests-header"))
            if (requests.isEmpty()) {
                player.sendMessage(lang.get("requests-none"))
            } else {
                requests.forEach { (uuid, _) -> // 忽略时间戳
                    // 核心修复：手动转换 UUID 为名字
                    val requesterName = plugin.server.getOfflinePlayer(uuid).name ?: "Unknown"
                    // 这样传参就是 Pair<String, String>，不会报错了
                    player.sendMessage(lang.get("requests-format", "name" to requesterName))
                }
            }
            player.sendMessage(lang.get("requests-footer"))
        })
    }

    private fun handleAccept(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("error-missing-args"))
            player.sendMessage(plugin.langManager.get("accept-usage"))
            return
        }
        plugin.guildService.acceptRequest(player, args[1]) { result ->
            val lang = plugin.langManager
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("accept-success-sender", "name" to args[1]))
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage("§c${result.message}")
                else -> {}
            }
        }
    }

    private fun handleDeny(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("error-missing-args"))
            return
        }

        // 修复方案：调用 Service 中的处理方法，不要在 Command 层传字符串给需要 UUID 的 DB 方法
        plugin.guildService.denyRequest(player, args[1]) { result ->
            val lang = plugin.langManager
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("deny-success", "player" to args[1]))
                is OperationResult.Error -> player.sendMessage("§c${result.message}")
                else -> {}
            }
        }
    }


    private fun executeInvite(sender: Player, targetUuid: UUID, guildId: Int, guildName: String) {
        plugin.inviteCache[targetUuid] = guildId
        sender.sendMessage(plugin.langManager.get("invite-sent-sender", "name" to plugin.server.getOfflinePlayer(targetUuid).name!!))
        plugin.server.getPlayer(targetUuid)?.sendMessage(plugin.langManager.get("invite-received-target", "guild" to guildName))
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.inviteCache.remove(targetUuid)
        }, 1200L)
    }


    private fun sendHelp(sender: Player) {
        val lang = plugin.langManager

        // 1. 显式传入 false，调用 get(key, boolean, vararg)
        sender.sendMessage(lang.get("help-header"))

        val cmds = mapOf(
            "create <Name>" to "desc-create", "join <Name>" to "desc-join", "info [Name]" to "desc-info",
            "requests" to "desc-requests", "accept <Player>" to "desc-accept", "promote <Player>" to "desc-promote",
            "demote <Player>" to "desc-demote", "kick <Player>" to "desc-kick", "leave" to "desc-leave", "delete" to "desc-delete"
        )

        cmds.forEach { (u, d) ->
            // 2. 注意这里，第二个参数直接是 false，不再写 "withPrefix = false"
            // 这样编译器就能清晰地把后面的 Pair 识别为 vararg
            sender.sendMessage(
                lang.get(
                    "help-format",
                    "usage" to u,
                    "description" to lang.get(d) // 递归调用也一样
                )
            )
        }

        if (sender.hasPermission("kaguilds.admin")) {
            // 3. Admin reload 同理
            sender.sendMessage(
                lang.get("help-format", "usage" to "reload", "description" to lang.get("desc-reload"))
            )
        }
        sender.sendMessage(lang.get("help-footer"))
    }

    override fun onTabComplete(sender: CommandSender, cmd: Command, alias: String, args: Array<out String>): List<String>? {
        val list = mutableListOf("help", "create", "join", "info", "requests", "accept", "promote", "demote", "leave", "kick", "delete", "chat", "bank","invite")
        if (sender.hasPermission("kaguilds.admin")) list.add("reload")
        return if (args.size == 1) list.filter { it.startsWith(args[0], ignoreCase = true) } else null
    }
}