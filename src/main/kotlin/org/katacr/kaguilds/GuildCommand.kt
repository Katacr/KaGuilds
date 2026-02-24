package org.katacr.kaguilds

import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.katacr.kaguilds.service.OperationResult
import org.katacr.kaguilds.service.GuildService
import kotlin.math.ceil
import kotlin.math.min

class GuildCommand(private val plugin: KaGuilds) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        val lang = plugin.langManager
        if (args.isEmpty()) {
            if (sender is Player) {
                // 默认打开主菜单
                plugin.menuManager.openMenu(sender, "main_menu")
            } else {
                sender.sendMessage(lang.get("help-hint"))
            }
            return true
        }

        val whiteList = listOf("help", "create", "join", "accept", "requests", "reload", "admin", "confirm", "yes", "no", "menu")
        val subCommand = args[0].lowercase()

        // 1. 帮助与控制台基础检查
        if (subCommand == "help") {
            val page = args.getOrNull(1)?.toIntOrNull() ?: 1
            if (sender is Player) sendHelp(sender, page) else sender.sendMessage(lang.get("console-help"))
            return true
        }

        // 2. 处理 /kg reload 命令
        if (subCommand == "reload") {
            if (!sender.hasPermission("kaguilds.admin")) {
                sender.sendMessage(lang.get("no-permission"))
                return true
            }
            plugin.reloadPlugin()
            sender.sendMessage(lang.get("reload-success"))
            return true
        }

        // 3. 检查执行者是否是玩家
        if (sender !is Player) {
            sender.sendMessage(lang.get("player-only"))
            return true
        }

        // 4. 检查玩家是否有公会
        if (!whiteList.contains(subCommand)) {
            // 尝试从缓存或数据库获取玩家公会 ID
            val guildId = plugin.playerGuildCache[sender.uniqueId] ?: plugin.dbManager.getGuildIdByPlayer(sender.uniqueId)

            if (guildId == null) {
                sender.sendMessage(lang.get("error-not-has-guild"))
                return true
            } else {
                // 顺便回填缓存，防止下次还要查数据库
                plugin.playerGuildCache[sender.uniqueId] = guildId
            }
        }
        // 3. 处理 /kg 子命令
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
            "yes" -> handleYes(sender)
            "no" -> handleNo(sender)
            "settp" -> handleSetTp(sender)
            "tp" -> handleTp(sender)
            "rename" -> handleRename(sender, args)
            "buff" -> handleBuff(sender, args)
            "confirm" -> handleConfirm(sender)
            "admin" -> handleAdmin(sender, args)
            "transfer" -> handleTransfer(sender, args)
            "vault" -> handleVault(sender, args)
            "seticon" -> handleSetIcon(sender)
            "motd" -> handleMotd(sender, args)
            "upgrade" -> handleUpgrade(sender)
            "pvp" -> handlePvP(sender, args)
            "menu" -> {
                if (!sender.hasPermission("kaguilds.use") && !sender.hasPermission("kaguilds.command.menu")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return true
                }
                plugin.menuManager.openMenu(sender, "main_menu")
                return true
            }


            else -> {
                sender.sendMessage(lang.get("help-hint"))
            }
        }
        return true
    }

    /*
     * 处理 /kg buff 命令
     */
    private fun handleBuff(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        
        // 权限检查：需要 kaguilds.use 或 kaguilds.command.buff 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.buff")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }
        
        if (args.size < 2) {
            player.sendMessage(lang.get("buff-usage"))
            return
        }

        val buffKey = args[1]
        plugin.guildService.buyBuff(player, buffKey) { result ->
            when (result) {
                is OperationResult.NoPermission -> {
                    player.sendMessage(lang.get("not-staff"))
                }
                is OperationResult.Error -> {
                    player.sendMessage(result.message)
                }
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg chat 命令
     */
    private fun handleChat(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.chat 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.chat")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        // 检查参数：/kg chat <消息...>
        if (args.size < 2) {
            player.sendMessage(lang.get("chat-usage"))
            return
        }

        // 合并消息内容
        val message = args.sliceArray(1 until args.size).joinToString(" ")
        plugin.guildService.sendGuildChat(player, message)
    }

    /*
     * 处理 /kg bank 命令
     */
    private fun handleBank(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.bank 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.bank")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

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
                if (totalPages in 1..<page) {
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
                        player.sendMessage(lang.get("bank-log-next-page", "page" to (page + 1).toString()))
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

        // 使用 toLongOrNull() 强制正整数
        val amountLong = args[2].toLongOrNull()
        if (amountLong == null || amountLong <= 0) {
            player.sendMessage(lang.get("bank-invalid-amount")) // 提示：请输入大于0的正整数
            return
        }

        val amount = amountLong.toDouble()

        val econ = plugin.economy ?: return // 经济系统检查

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId) ?: return@Runnable
            val guildData = plugin.dbManager.getGuildData(guildId) ?: return@Runnable

            // 从配置文件获取当前等级的金库上限
            val maxBank = plugin.levelsConfig.getLong("levels.${guildData.level}.max-money", 50000L).toDouble()

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
                                plugin.guildService.dispatchBankNotification(guildId, player.name, "deposit", amount)
                            }
                        })
                    })
                }

                "get" -> {
                    // 1. 检查公会余额是否足够
                    if (guildData.balance < amount) {
                        player.sendMessage(lang.get("bank-insufficient-guild"))
                        return@Runnable
                    }

                    // 2. 更新数据库扣款
                    if (plugin.dbManager.updateGuildBalance(guildId, -amount)) {
                        plugin.dbManager.logBankTransaction(guildId, player.name, "GET", amount)

                        plugin.server.scheduler.runTask(plugin, Runnable {
                            econ.depositPlayer(player, amount)
                            // 显示给玩家时去除 .0
                            player.sendMessage(lang.get("bank-get-success", "amount" to amountLong.toString()))
                            plugin.guildService.dispatchBankNotification(guildId, player.name, "withdraw", amount)
                        })
                    }
                }
                else -> player.sendMessage(lang.get("bank-usage"))
            }
        })
    }

    /*
     * 处理 /kg kick 命令
     */
    private fun handleKick(player: Player, args: Array<out String>) {
        val lang = plugin.langManager
        // 权限检查：需要 kaguilds.use 或 kaguilds.command.kick 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.kick")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

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

    /*
     * 处理 /kg delete 命令
     */
    private fun handleDelete(player: Player) {
        val lang = plugin.langManager
        val role = plugin.dbManager.getPlayerRole(player.uniqueId)

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.delete 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.delete")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (role != "OWNER") {
            player.sendMessage(plugin.langManager.get("no-permission"))
            return
        }

        plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Delete)
        player.sendMessage(plugin.langManager.get("confirm-delete"))
        player.sendMessage(plugin.langManager.get("confirm-hint"))
    }

    /*
     * 处理 /kg create 命令
     */
    private fun handleCreate(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.create 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.create")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (args.size < 2) {
            player.sendMessage(lang.get("create-usage"))
            return
        }
        val guildName = args[1]

        val min = plugin.config.getInt("guild.name-settings.min-length", 2)
        val max = plugin.config.getInt("guild.name-settings.max-length", 10)
        val regexStr = plugin.config.getString("guild.name-settings.regex") ?: "^[\\u4e00-\\u9fa5a-zA-Z0-9]+$"

        // 1. 长度校验
        if (guildName.length !in min..max) {
            player.sendMessage(lang.get("create-invalid-length",
                "min" to min.toString(), "max" to max.toString()))
            return
        }

        // 2. 正则表达式校验
        if (!guildName.matches(Regex(regexStr))) {
            player.sendMessage(lang.get("create-invalid-name"))
            return
        }

        plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Create(guildName))

        val cost = plugin.config.getDouble("balance.create", 1000.0)
        // 发送确认信息
        player.sendMessage(plugin.langManager.get("confirm-create", "name" to guildName, "cost" to cost.toString()))
        player.sendMessage(plugin.langManager.get("confirm-hint"))
    }

    /*
    * 处理 /kg leave 命令
    */
    private fun handleLeave(player: Player) {
        val lang = plugin.langManager
        val role = plugin.dbManager.getPlayerRole(player.uniqueId)

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.leave 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.leave")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (role == "OWNER") {
            player.sendMessage(plugin.langManager.get("owner-cannot-leave"))
            return
        }

        plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Leave)
        player.sendMessage(plugin.langManager.get("confirm-leave"))
        player.sendMessage(plugin.langManager.get("confirm-hint"))
    }

    /*
     * 处理 /kg info 命令
     */
    private fun handleInfo(player: Player) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.info 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.info")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        plugin.guildService.getDetailedInfo(player) { result, info ->
            when (result) {
                is OperationResult.Success -> {
                    val d = info!!.data
                    val lang = plugin.langManager
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
                is OperationResult.NotInGuild -> player.sendMessage(plugin.langManager.get("not-in-guild"))
                else -> player.sendMessage("§cError: $result")
            }
        }
    }

    /*
     * 处理 /kg promote 命令
     * 处理 /kg demote 命令
     */
    private fun handleRoleChange(sender: Player, args: Array<out String>, newRole: String, roleDisplay: String) {
        val lang = plugin.langManager

        if (args.size < 2) {
            if (args[0] == "promote") {
                if (!sender.hasPermission("kaguilds.use") && !sender.hasPermission("kaguilds.command.promote")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
            }
            if (args[0] == "demote") {
                if (!sender.hasPermission("kaguilds.use") && !sender.hasPermission("kaguilds.command.demote")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
            }
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
                        sender.sendMessage(lang.get("promote-success", "name" to targetName, "action" to lang.get("promote-action-${args[0]}")))
                        targetOffline.player?.sendMessage(lang.get("role-updated-target", "role" to roleDisplay))
                    }
                }
            }
        })
    }

    /*
    * 处理 /kg invite 命令
    */
    private fun handleInvite(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.invite 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.invite")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (args.size < 2) {
            player.sendMessage(lang.get("error-missing-args"))
            player.sendMessage(lang.get("invite-usage"))
            return
        }

        plugin.guildService.invitePlayer(player, args[1]) { result ->
            // 回到主线程执行消息反馈
            plugin.server.scheduler.runTask(plugin, Runnable {
                when (result) {
                    is OperationResult.Success -> {
                        val isProxy = plugin.config.getBoolean("proxy", false)
                        val msgKey = if (isProxy) "invite-success-proxy" else "invite-success"
                        player.sendMessage(lang.get(msgKey, "player" to args[1]))
                    }
                    is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                    is OperationResult.NotInGuild -> player.sendMessage(lang.get("not-in-guild"))
                    is OperationResult.Error -> player.sendMessage(result.message)
                    else -> {}
                }
            })
        }
    }

    /*
     * 处理 /kg join 命令
     */
    private fun handleJoin(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.join 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.join")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (args.size < 2) {
            player.sendMessage(lang.get("join-usage"))
            return
        }

        val input = args[1]

        // 异步执行请求加入公会的操作
        plugin.guildService.requestJoin(player, input) { result ->
            when (result) {
                is OperationResult.Success -> {
                    // 获取用于显示的公会名
                    val displayName = if (input.startsWith("#")) {
                        val id = input.substring(1).toIntOrNull() ?: -1
                        // 从数据库获取真实名称，如果找不到则显示原输入
                        plugin.dbManager.getGuildById(id)?.name ?: input
                    } else {
                        input
                    }

                    player.sendMessage(lang.get("join-success", "guild" to displayName))
                }
                is OperationResult.AlreadyInGuild -> player.sendMessage(lang.get("join-already"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg requests 命令
     */
    private fun handleRequests(player: Player) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.requests 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.requests")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)
                ?: return@Runnable player.sendMessage(lang.get("not-in-guild"))

            // 数据库返回的是 List<Pair<UUID, Long>>
            val requests = plugin.dbManager.getRequests(guildId)

            player.sendMessage(lang.get("requests-header"))
            if (requests.isEmpty()) {
                player.sendMessage(lang.get("requests-none"))
            } else {
                requests.forEach { (uuid, _) ->
                    val requesterName = plugin.server.getOfflinePlayer(uuid).name ?: "Unknown"
                    player.sendMessage(lang.get("requests-format", "name" to requesterName))
                }
            }
            player.sendMessage(lang.get("requests-footer"))
        })
    }

    /*
     * 处理 /kg accept 命令
     */
    private fun handleAccept(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.accept 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.accept")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (args.size < 2) {
            player.sendMessage(lang.get("error-missing-args"))
            player.sendMessage(lang.get("accept-usage"))
            return
        }

        // 调用 Service 层处理申请逻辑 (之前已经写好的 acceptRequest)
        plugin.guildService.acceptRequest(player, args[1]) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("accept-success", "player" to args[1]))
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg deny 命令
     */
    private fun handleDeny(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.deny 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.deny")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (args.size < 2) {
            player.sendMessage(lang.get("error-missing-args"))
            return
        }

        plugin.guildService.denyRequest(player, args[1]) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("deny-success", "player" to args[1]))
                is OperationResult.Error -> player.sendMessage("§c${result.message}")
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg help 命令
     */
    private fun sendHelp(sender: Player, page: Int) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.help 权限
        if (!sender.hasPermission("kaguilds.use") && !sender.hasPermission("kaguilds.command.help")) {
            sender.sendMessage(lang.get("no-permission"))
            return
        }

        // 使用 LinkedHashMap 或 List<Pair> 保持指令显示的顺序
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
            "bank <add|get|log>" to "desc-bank",
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
            // 过滤掉权限不符的指令
            if (it.first == "reload") sender.hasPermission("kaguilds.admin") else true
        }

        val pageSize = 10
        val maxPage = ceil(allCmds.size.toDouble() / pageSize).toInt()

        // 修正页码边界
        val currentPage = when {
            page < 1 -> 1
            page > maxPage -> maxPage
            else -> page
        }

        // 发送页头
        sender.sendMessage(lang.get("help-header"))

        // 计算起始和结束索引
        val start = (currentPage - 1) * pageSize
        val end = min(start + pageSize, allCmds.size)

        // 循环发送该页的指令
        for (i in start until end) {
            val (cmd, descKey) = allCmds[i]
            sender.sendMessage(" §6/guild $cmd §7- §f${lang.get(descKey)}")
        }

        // 发送页脚
        val footer = lang.get("help-footer", "page" to currentPage.toString(), "max" to maxPage.toString())
        sender.sendMessage(footer)

        if (currentPage < maxPage) {
            sender.sendMessage(lang.get("help-next-page", "page" to (currentPage + 1).toString()))
        }
    }
    /*
     * 处理 /kg yes 命令
     */
    private fun handleYes(player: Player) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.yes 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.yes")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        plugin.guildService.acceptInvite(player) { result ->
            when (result) {
                is OperationResult.Success -> {
                    player.sendMessage(lang.get("invite-accepted"))
                    // 播放成功音效
                    player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                }
                is OperationResult.AlreadyInGuild -> player.sendMessage(lang.get("join-already-in-guild"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg no 命令
     */
    private fun handleNo(player: Player) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.no 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.no")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (!plugin.inviteCache.containsKey(player.uniqueId)) {
            player.sendMessage(lang.get("invite-none"))
            return
        }
        plugin.guildService.declineInvite(player)
        player.sendMessage(lang.get("invite-declined"))
    }

    /*
     * 处理 /kg settp 命令
     */
    private fun handleSetTp(player: Player) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.settp 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.settp")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

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

    /*
     * 处理 /kg tp 命令
     */
    private fun handleTp(player: Player) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.tp 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.tp")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        plugin.guildService.teleportToGuild(player) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(lang.get("tp-success"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg rename 命令
     */
    private fun handleRename(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.rename 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.rename")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (args.size < 2) {
            player.sendMessage(lang.get("rename-usage"))
            return
        }

        // 2. 定义变量 newName
        val newName = args[1]

        // 3. 调用 Service
        plugin.guildService.renameGuild(player, newName) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val price = plugin.config.getDouble("balance.rename", 3000.0).toString()
                    player.sendMessage(lang.get("rename-success",
                        "name" to newName,
                        "price" to price
                    ))
                }
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg confirm 命令
     */
    private fun handleConfirm(player: Player) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.confirm 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.confirm")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        val action = plugin.guildService.consumePendingAction(player.uniqueId) ?: run {
            player.sendMessage(lang.get("confirm-no-pending"))
            return
        }

        when (action) {
            is GuildService.PendingAction.Create -> performCreate(player, action.guildName)
            is GuildService.PendingAction.Delete -> performDelete(player)
            is GuildService.PendingAction.Leave -> performLeave(player)
            is GuildService.PendingAction.Transfer -> performTransfer(player, action.targetName)

        }
    }

    /*
     * 执行创建公会的实际逻辑
     */
    private fun performCreate(player: Player, guildName: String) {
        plugin.guildService.createGuild(player, guildName) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(plugin.langManager.get("create-success", "name" to guildName))
                is OperationResult.NameAlreadyExists -> player.sendMessage(plugin.langManager.get("create-name-exists"))
                is OperationResult.InsufficientFunds -> player.sendMessage(plugin.langManager.get("create-insufficient-funds"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    /*
     * 执行删除公会的实际逻辑
     */
    private fun performDelete(player: Player) {
        plugin.guildService.deleteGuild(player) { result ->
            if (result is OperationResult.Success) {
                player.sendMessage(plugin.langManager.get("delete-success"))
            } else if (result is OperationResult.Error) {
                player.sendMessage(result.message)
            }
        }
    }

    /*
     * 执行离开公会的实际逻辑
     */
    private fun performLeave(player: Player) {
        plugin.guildService.leaveGuild(player) { result ->
            if (result is OperationResult.Success) {
                player.sendMessage(plugin.langManager.get("leave-success"))
            } else if (result is OperationResult.Error) {
                player.sendMessage(result.message)
            }
        }
    }

    /*
     * 执行转让确认逻辑
     */
    private fun performTransfer(player: Player, targetName: String) {
        val lang = plugin.langManager

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            // 1. 获取当前公会ID
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId) ?: return@Runnable

            // 2. 调用 Service 层执行逻辑
            plugin.guildService.adminTransferGuild(guildId, targetName) { result ->
                // 回到主线程发消息
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (result is OperationResult.Success) {
                        player.sendMessage(lang.get("transfer-success", "player" to targetName))

                    } else if (result is OperationResult.Error) {
                        player.sendMessage(result.message)
                    }
                })
            }
        })
    }

    /*
     * 处理 /kg transfer 命令
     */
    private fun handleTransfer(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.transfer 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.transfer")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (args.size < 2) {
            player.sendMessage(lang.get("transfer-usage"))
            return
        }
        val targetName = args[1]

        // 1. 异步检查权限（涉及数据库查询）
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val guildId = plugin.dbManager.getGuildIdByPlayer(player.uniqueId)

            // 检查是否在公会中
            if (guildId == null) {
                player.sendMessage(lang.get("not-in-guild"))
                return@Runnable
            }

            val guildData = plugin.dbManager.getGuildData(guildId)

            // 检查是否是会长 (对比 UUID)
            if (guildData?.ownerUuid != player.uniqueId.toString()) {
                player.sendMessage(lang.get("not-staff"))
                return@Runnable
            }

            // 检查目标玩家是否是自己
            if (player.name.equals(targetName, ignoreCase = true)) {
                player.sendMessage(lang.get("error-player-is-yourself"))
                return@Runnable
            }

            // 2. 检查通过，回到主线程设置确认动作
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.sendMessage(lang.get("transfer-confirm-notice", "player" to targetName))
                plugin.guildService.setPendingAction(player.uniqueId, GuildService.PendingAction.Transfer(targetName))
            })
        })
    }

    /*
     * 处理管理员指令 /kg admin
     */
    private fun handleAdmin(sender: CommandSender, args: Array<out String>) {
        val lang = plugin.langManager

        if (args.size < 2) {
            sender.sendMessage(lang.get("admin-usage"))
            return
        }

        when (args[1].lowercase()) {
            "rename" -> {
                // 格式: /kg admin rename #ID [新名称]
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.rename")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                if (args.size < 4) {
                    sender.sendMessage(lang.get("admin-rename-usage"))
                    return
                }

                val idStr = args[2].replace("#", "")
                val guildId = idStr.toIntOrNull()
                if (guildId == null) {
                    sender.sendMessage(lang.get("error-invalid-id"))
                    return
                }

                val newName = args[3]
                val min = plugin.config.getInt("guild.name-settings.min-length", 2)
                val max = plugin.config.getInt("guild.name-settings.max-length", 10)

                if (newName.length !in min..max) {
                    sender.sendMessage(lang.get("create-invalid-length", "min" to min.toString(), "max" to max.toString()))
                    return
                }

                if (!newName.matches(Regex(plugin.config.getString("guild.name-settings.regex") ?: ""))) {
                    sender.sendMessage(lang.get("create-invalid-name"))
                    return
                }

                plugin.guildService.adminRenameGuild(guildId, newName) { result ->
                    when (result) {
                        is OperationResult.Success ->
                            sender.sendMessage(lang.get("admin-rename-success",
                                "id" to guildId.toString(), "name" to newName))
                        is OperationResult.NameAlreadyExists ->
                            sender.sendMessage(lang.get("create-name-exists"))
                        is OperationResult.Error ->
                            sender.sendMessage(result.message)
                        else -> {}
                    }
                }
            }
            "delete" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.delete")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                if (args.size < 3) {
                    sender.sendMessage(lang.get("admin-delete-usage"))
                    return
                }

                val idStr = args[2].replace("#", "")
                val guildId = idStr.toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-id"))

                plugin.guildService.adminDeleteGuild(guildId) { result ->
                    when (result) {
                        is OperationResult.Success ->
                            sender.sendMessage(lang.get("admin-delete-success", "id" to guildId.toString()))
                        is OperationResult.Error ->
                            sender.sendMessage(result.message)
                        else -> {}
                    }
                }
            }
            "info" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.info")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                if (args.size < 3) {
                    sender.sendMessage(lang.get("admin-info-usage"))
                    return
                }

                val idStr = args[2].replace("#", "")
                val guildId = idStr.toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-id"))

                plugin.guildService.getAdminGuildInfo(guildId) { result, info ->
                    if (result is OperationResult.Success && info != null) {

                        val d = info.data
                        val coloredList = renderMemberList(info.memberNames, guildId)

                        sender.sendMessage(lang.get("info-admin-header", "id" to d.id.toString()))
                        sender.sendMessage(lang.get("info-name", "name" to d.name, "id" to d.id.toString()))
                        sender.sendMessage(lang.get("info-owner", "owner" to (d.ownerName ?: "未知")))
                        sender.sendMessage(lang.get("info-level", "level" to d.level.toString(), "exp" to d.exp.toString()))
                        sender.sendMessage(lang.get("info-balance", "balance" to d.balance.toString()))
                        sender.sendMessage(lang.get("info-members", "current" to info.memberNames.size.toString(), "max" to d.maxMembers.toString(), "online" to info.onlineCount.toString()))
                        sender.sendMessage(lang.get("info-list", "list" to coloredList))
                        sender.sendMessage(lang.get("info-footer"))
                    } else if (result is OperationResult.Error) {
                        sender.sendMessage(result.message)
                    }
                }
            }
            "bank" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.bank")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                if (args.size < 4) {

                    sender.sendMessage(lang.get("admin-bank-usage"))
                    return
                }
                val guildId = args[2].replace("#", "").toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-id"))
                when (val action = args[3].lowercase()) {
                    "see" -> {
                        plugin.guildService.getAdminGuildInfo(guildId) { _, info ->
                            val bal = info?.data?.balance ?: 0.0
                            // 使用国际化节点 admin-bank-see
                            sender.sendMessage(lang.get("admin-bank-see",
                                "id" to guildId.toString(),
                                "balance" to bal.toString()
                            ))
                        }
                    }
                    "log" -> {
                        // 默认页码为 1，如果玩家输入了第 5 个参数则尝试解析
                        val page = if (args.size >= 5) args[4].toIntOrNull() ?: 1 else 1

                        plugin.guildService.getAdminBankLogs(guildId, page) { logs ->
                            val lang = plugin.langManager

                            sender.sendMessage(lang.get("admin-bank-log-header",
                                "id" to guildId.toString(),
                                "page" to page.toString()
                            ))

                            if (logs.isEmpty()) {
                                sender.sendMessage(lang.get("admin-bank-no-log"))
                            } else {
                                logs.forEach { sender.sendMessage(it) }
                                // 提示翻页
                                sender.sendMessage(lang.get("admin-bank-log-footer", "page" to (page + 1).toString(), "id" to guildId.toString()))
                            }
                        }
                    }
                    "add", "remove", "set" -> {
                        if (args.size < 5) {
                            sender.sendMessage(lang.get("admin-bank-amount-required"))
                            return
                        }
                        val amount = args[4].toDoubleOrNull() ?: return sender.sendMessage(lang.get("error-invalid-number"))

                        plugin.guildService.adminManageBank(guildId, action, amount) { result, newBal ->
                            if (result is OperationResult.Success) {
                                // 使用国际化节点 admin-bank-success
                                sender.sendMessage(lang.get("admin-bank-success",
                                    "id" to guildId.toString(),
                                    "balance" to newBal.toString()
                                ))
                            } else if (result is OperationResult.Error) {
                                sender.sendMessage(result.message)
                            }
                        }
                    }
                }
            }
            "transfer" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.transfer")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                val lang = plugin.langManager
                if (args.size < 4) return sender.sendMessage(lang.get("admin-transfer-usage"))
                val guildId = args[2].replace("#", "").toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-id"))
                val targetName = args[3]

                plugin.guildService.adminTransferGuild(guildId, targetName) { result ->
                    if (result is OperationResult.Success) {
                        sender.sendMessage(lang.get("admin-transfer-success", "player" to targetName))
                    } else if (result is OperationResult.Error) {
                        sender.sendMessage(result.message)
                    }
                }
            }
            "kick" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.kick")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                if (args.size < 4) return sender.sendMessage(lang.get("admin-kick-usage"))
                val guildId = args[2].replace("#", "").toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-id"))
                val targetName = args[3]

                plugin.guildService.adminKickMember(guildId, targetName) { result ->
                    if (result is OperationResult.Success) {
                        sender.sendMessage(lang.get("admin-kick-member-success", "player" to targetName, "id" to guildId.toString()))
                    } else if (result is OperationResult.Error) {
                        sender.sendMessage(result.message)
                    }
                }
            }
            "join" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.join")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                if (args.size < 4) return sender.sendMessage(lang.get("admin-join-usage"))
                val guildId = args[2].replace("#", "").toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-id"))

                // 强制加入仅支持在线玩家，方便获取最新的 Name 和 UUID
                val targetPlayer = plugin.server.getPlayer(args[3])
                    ?: return sender.sendMessage(lang.get("error-player-not-online"))

                plugin.guildService.adminJoinMember(guildId, targetPlayer) { result ->
                    if (result is OperationResult.Success) {
                        sender.sendMessage(lang.get("admin-join-member-success", "player" to targetPlayer.name, "id" to guildId.toString()))

                    } else if (result is OperationResult.Error) {
                        sender.sendMessage(result.message)
                    }
                }
            }
            "vault" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.vault")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                // 格式: /kg admin vault #ID [编号]
                if (args.size < 3) {
                    sender.sendMessage(lang.get("admin-vault-usage"))
                    return
                }

                val guildId = args[2].replace("#", "").toIntOrNull()
                    ?: return sender.sendMessage(lang.get("error-invalid-id"))

                val index = if (args.size > 3) args[3].toIntOrNull() ?: 1 else 1

                // 调用 Service 层专门给 admin 用的方法
                plugin.guildService.adminOpenVault(sender as Player, guildId, index)
            }
            "unlockall" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.unlockall")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                // 用法: /kg admin unlockall
                // 效果：清空全服务器（及跨服）的所有仓库锁
                plugin.guildService.forceResetAllLocks()
                sender.sendMessage(lang.get("admin-unlockall-success"))
            }
            "setlevel"-> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.setlevel")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                val targetId = args[2].replace("#", "").toIntOrNull() ?: return
                val newLevel = args[3].toIntOrNull() ?: return

                // 获取对应等级的配置信息
                val levelSection = plugin.levelsConfig.getConfigurationSection("levels.$newLevel")
                if (levelSection == null) {
                    sender.sendMessage(lang.get("admin-unknow-level", "level" to newLevel.toString()))
                    return
                }

                val maxMembers = levelSection.getInt("max-members")
                val guild = plugin.dbManager.getGuildById(targetId)
                if (plugin.dbManager.updateGuildLevel(targetId, newLevel, maxMembers)) {
                    if (guild != null) {
                        sender.sendMessage(lang.get("admin-success-modify-level", "name" to guild.name, "level" to newLevel.toString()))
                    }
                }
            }
            "exp" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.exp")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                // /kg admin exp #1 add 100
                if (args.size < 5) {
                    sender.sendMessage(lang.get("admin-exp-usage"))
                    return
                }
                val guildId = args[2].replace("#", "").toIntOrNull() ?: return
                val action = args[3].lowercase()
                val amount = args[4].toIntOrNull() ?: return

                plugin.guildService.adminModifyExp(sender, guildId, action, amount)
            }
            "arena" -> {
                if (!sender.hasPermission("kaguilds.admin") && !sender.hasPermission("kaguilds.admin.arena")) {
                    sender.sendMessage(lang.get("no-permission"))
                    return
                }
                val action = args.getOrNull(2)?.lowercase()
                if (action == null) {
                    sender.sendMessage(lang.get("admin-arena-usage"))
                    return
                }

                val player = sender as? Player ?: return
                val loc = player.location
                val arena = plugin.arenaManager.arena

                when (action) {
                    "setpos" -> {
                        val index = args.getOrNull(3)
                        when (index) {
                            "1" -> {
                                arena.pos1 = loc
                                player.sendMessage(lang.get("admin-arena-set-pos1"))
                            }
                            "2" -> {
                                arena.pos2 = loc
                                player.sendMessage(lang.get("admin-arena-set-pos2"))
                            }
                            else -> {
                                player.sendMessage(lang.get("admin-arena-setpos-usage"))
                                return
                            }
                        }
                    }

                    "setspawn" -> {
                        val team = args.getOrNull(3)?.lowercase()
                        when (team) {
                            "red" -> {
                                arena.redSpawn = loc
                                player.sendMessage(lang.get("admin-arena-set-redspawn"))
                            }
                            "blue" -> {
                                arena.blueSpawn = loc
                                player.sendMessage(lang.get("admin-arena-set-bluespawn"))
                            }
                            else -> {
                                player.sendMessage(lang.get("admin-arena-setspawn-usage"))
                                return
                            }
                        }
                    }

                    "setkit" -> {
                        val team = args.getOrNull(3)?.lowercase()
                        if (team != "red" && team != "blue") {
                            player.sendMessage(lang.get("admin-arena-setkit-usage"))
                            return
                        }

                        plugin.arenaManager.saveKit(player, team)
                        val teamDisplay = if (team == "red") lang.get("arena-pvp-red-team-name") else lang.get("arena-pvp-blue-team-name")
                        player.sendMessage(lang.get("admin-arena-set-kit", "team" to teamDisplay))
                        return
                    }

                    "info" -> {
                        val t = lang.get("admin-arena-info-set")
                        val f = lang.get("admin-arena-info-unset")

                        player.sendMessage(lang.get("admin-arena-info-header"))
                        player.sendMessage("${lang.get("admin-arena-info-pos1")} ${if (arena.pos1 != null) t else f}")
                        player.sendMessage("${lang.get("admin-arena-info-pos2")} ${if (arena.pos2 != null) t else f}")
                        player.sendMessage("${lang.get("admin-arena-info-redspawn")} ${if (arena.redSpawn != null) t else f}")
                        player.sendMessage("${lang.get("admin-arena-info-bluespawn")} ${if (arena.blueSpawn != null) t else f}")

                        val redKitStatus = if (plugin.arenaManager.redKitContents != null) t else f
                        val blueKitStatus = if (plugin.arenaManager.blueKitContents != null) t else f
                        player.sendMessage("${lang.get("admin-arena-info-redkit")} $redKitStatus")
                        player.sendMessage("${lang.get("admin-arena-info-bluekit")} $blueKitStatus")
                        return
                    }

                    else -> {
                        sender.sendMessage(lang.get("admin-arena-usage"))
                        return
                    }
                }

                // 只要有坐标位置的修改就立即保存配置文件
                plugin.arenaManager.saveArena()
            }

            else -> sender.sendMessage(lang.get("admin-usage"))
        }
    }

    /*
     * 处理 /kg vault <index>
     */
    private fun handleVault(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.vault 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.vault")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }


        // 1. 获取玩家公会 ID (从缓存获取最快)
        val guildId = plugin.playerGuildCache[player.uniqueId]
        if (guildId == null) {
            player.sendMessage(lang.get("error-no-guild"))
            return
        }

        // 2. 解析仓库页码 (args[0] 是 "vault"，args[1] 是编号)
        // 如果玩家只输入 /kg vault，则默认打开第 1 页
        val index = if (args.size > 1) {
            val input = args[1].toIntOrNull()
            if (input == null || input < 1 || input > 9) {
                player.sendMessage(lang.get("error-invalid-vault-index"))
                return
            }
            input
        } else {
            1
        }

        // 3. 调用 Service 层执行逻辑 (检查等级、锁定仓库、读取数据、打开 GUI)
        plugin.guildService.openVault(player, index)
    }

    /*
     * 处理 /kg seticon 命令
     */
    private fun handleSetIcon(player: Player) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.seticon 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.seticon")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        val item = player.inventory.itemInMainHand
        if (item.type == org.bukkit.Material.AIR) {
            player.sendMessage(plugin.langManager.get("error-no-item"))
            return
        }

        val materialName = item.type.name

        plugin.guildService.setGuildIcon(player, materialName) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val cost = plugin.config.getDouble("balance.seticon", 1000.0)
                    player.sendMessage(plugin.langManager.get("seticon-success", "cost" to cost.toString(), "material" to materialName))
                }
                is OperationResult.NoPermission -> {
                    player.sendMessage(plugin.langManager.get("not-staff"))
                }
                is OperationResult.Error -> {
                    player.sendMessage(result.message)
                }
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg motd  命令
     */
    private fun handleMotd(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.motd 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.motd")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        if (args.size < 2) {
            player.sendMessage(lang.get("motd-usage"))
            return
        }

        // 1. 合并参数为完整字符串
        val content = args.slice(1 until args.size).joinToString(" ")

        // 2. 长度校验 (64字符)
        if (content.length > 64) {
            player.sendMessage(lang.get("motd-too-long"))
            return
        }

        // 3. 安全过滤 (禁止引号、分号、括号、彩色符号等)
        val forbiddenPattern = Regex("""['";\\<>{}\[\]§&]""")
        if (forbiddenPattern.containsMatchIn(content)) {
            player.sendMessage(lang.get("motd-forbidden-char"))
            return
        }

        // 4. 调用 Service 执行扣费修改
        plugin.guildService.setGuildMotd(player, content) { result ->
            when (result) {
                is OperationResult.Success -> {
                    val cost = plugin.config.getDouble("balance.motd", 100.0)
                    player.sendMessage(lang.get("motd-success", "cost" to cost.toString()))

                }
                is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg upgrade 命令
     */
    private fun handleUpgrade(player: Player) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.upgrade 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.upgrade")) {
            player.sendMessage(lang.get("no-permission"))
            return
        }

        plugin.guildService.upgradeGuild(player) { result ->
            if (result is OperationResult.Error) player.sendMessage(result.message)
            else player.sendMessage(lang.get("menu-upgrade-level-up-msg"))
        }
    }

    /*
     * 处理 /kg pvp 命令
     */
    private fun handlePvP(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        // 权限检查：需要 kaguilds.use 或 kaguilds.command.pvp 权限
        if (!player.hasPermission("kaguilds.use") && !player.hasPermission("kaguilds.command.pvp")) {
            player.sendMessage(lang.get("no-permission"))
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
            "start" -> {
                if (args.size < 3) {
                    player.sendMessage(lang.get("arena-pvp-start-usage"))
                    return
                }

                // 基础检查：是否有正在进行的比赛
                if (plugin.pvpManager.currentMatch != null) {
                    player.sendMessage(lang.get("arena-pvp-error-arena-busy"))
                    return
                }

                // 获取目标公会
                val targetInput = args[2]
                val targetGuild = if (targetInput.startsWith("#")) {
                    plugin.dbManager.getGuildData(targetInput.substring(1).toIntOrNull() ?: -1)
                } else {
                    plugin.dbManager.getGuildByName(targetInput)
                }

                if (targetGuild == null || targetGuild.id == guildId) {
                    player.sendMessage(lang.get("arena-pvp-error-invalid-target"))
                    return
                }

                // 检查对方是否有管理在线 (保持在主线程检查)
                val isTargetStaffOnline = plugin.server.onlinePlayers.any { onlinePlayer ->
                    val onlinePlayerGuildId = plugin.playerGuildCache[onlinePlayer.uniqueId]
                    onlinePlayerGuildId == targetGuild.id && plugin.dbManager.isStaff(onlinePlayer.uniqueId, targetGuild.id)
                }
                if (!isTargetStaffOnline) {
                    player.sendMessage(lang.get("arena-pvp-error-target-offline"))
                    return
                }

                // 调用 Service 层执行扣费与发起逻辑
                plugin.guildService.startPvPChallenge(player, targetGuild) { result ->
                    when (result) {
                        is OperationResult.Success -> {
                            val cost = plugin.config.getDouble("balance.pvp", 300.0)
                            player.sendMessage(lang.get("arena-pvp-challenge-sent", "fee" to cost.toString()))

                            val myGuildName = plugin.dbManager.getGuildData(guildId)?.name ?: "Unknown"
                            plugin.pvpManager.notifyTargetGuild(targetGuild.id, myGuildName)
                        }
                        is OperationResult.NoPermission -> player.sendMessage(lang.get("not-staff"))
                        is OperationResult.Error -> player.sendMessage(result.message)
                        else -> {}
                    }
                }
            }

            "accept" -> {
                if (!plugin.dbManager.isStaff(player.uniqueId, guildId)) {
                    player.sendMessage(lang.get("not-staff"))
                    return
                }

                val senderId = plugin.pvpManager.acceptChallenge(guildId)
                if (senderId != null) {
                    val match = plugin.pvpManager.currentMatch
                    val senderName = plugin.dbManager.getGuildData(senderId)?.name ?: "Opponent"

                    match?.smartBroadcast(lang.get("arena-pvp-accept-broadcast", "player" to player.name, "sender" to senderName))
                    match?.smartBroadcast(lang.get("arena-pvp-ready-hint"))
                } else {
                    player.sendMessage(lang.get("arena-pvp-error-no-invite"))
                }
            }

            "ready" -> {
                val match = plugin.pvpManager.currentMatch ?: run {
                    player.sendMessage(lang.get("arena-pvp-error-no-match"))
                    return
                }

                if (match.isStarted) {
                    player.sendMessage(lang.get("arena-pvp-error-started"))
                    return
                }

                val maxPerTeam = plugin.config.getInt("guild.arena.max-players", 10)
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
                    player.sendMessage(lang.get("arena-arena-pvp-no-spawn"))
                    return
                }

                if (match.players.add(player.uniqueId)) {
                    val guildName = plugin.dbManager.getGuildData(guildId)?.name ?: "Unknown"
                    val isRed = guildId == match.redGuildId
                    val teamDisplay = if (isRed) lang.get("arena-pvp-red-team-name") else lang.get("arena-pvp-blue-team-name")

                    match.smartBroadcast(lang.get("arena-pvp-join-broadcast",
                        "team" to teamDisplay,
                        "guild" to guildName,
                        "player" to player.name))

                    player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
                }
            }

            "exit" -> {
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
            else -> player.sendMessage(lang.get("error-missing-args"))
        }
    }
    /*
     * 处理 /kg 命令的 tab 补全
     */
    override fun onTabComplete(sender: CommandSender, cmd: Command, alias: String, args: Array<out String>): List<String>? {
        if (sender !is Player) return emptyList()

        return when (args.size) {
            1 -> {
                val list = mutableListOf(
                    "help", "create", "join", "info", "requests", "accept", "promote",
                    "demote", "leave", "kick", "delete", "chat", "bank", "invite",
                    "settp", "tp", "rename", "buff", "deny", "transfer", "vault",
                    "menu", "seticon", "motd", "upgrade", "pvp"
                )
                if (sender.hasPermission("kaguilds.admin")) {
                    list.addAll(listOf("reload", "admin"))
                }
                if (plugin.guildService.hasPendingAction(sender.uniqueId)) {
                    list.add("confirm")
                }
                filterList(list, args[0])
            }

            2 -> {
                val sub = args[0].lowercase()
                val list = when (sub) {
                    "pvp" -> listOf("start", "accept", "ready", "exit")
                    "admin" -> if (sender.hasPermission("kaguilds.admin")) {
                        listOf("rename", "delete", "info", "bank", "transfer", "kick", "join", "vault", "unlockall", "setlevel", "exp", "arena")
                    } else emptyList()
                    "bank" -> listOf("add", "get", "log")
                    "vault" -> (1..9).map { it.toString() }
                    "buff" -> getBuffTab(sender)
                    "kick", "promote", "demote", "invite", "join", "accept", "deny", "transfer" -> return null
                    else -> emptyList()
                }
                filterList(list, args[1])
            }

            3 -> {
                val sub = args[0].lowercase()
                val adminAction = args[1].lowercase()

                if (sub == "admin") {
                    // 特殊处理没有公会ID参数的指令
                    if (adminAction == "arena") {
                        return filterList(listOf("setpos", "setspawn", "setkit", "info"), args[2])
                    }
                    if (adminAction == "unlockall") return emptyList()

                    // 其他 admin 指令此处均为 [公会ID]，提示输入 #
                    return filterList(listOf("#"), args[2])
                }

                if (sub == "pvp" && adminAction == "start") return null
                emptyList()
            }

            4 -> handleAdminTab(args)

            5 -> {
                // 处理五段式指令的最后一位数值或玩家名
                val sub = args[0].lowercase()
                val adminAction = args[1].lowercase()
                if (sub == "admin") {
                    when (adminAction) {
                        "bank", "exp" -> return filterList(listOf("<数值>"), args[4])
                        "transfer", "kick", "join" -> return null // 补全玩家名
                    }
                }
                emptyList()
            }

            else -> emptyList()
        }
    }

    /**
     * 专门处理 /kg admin ... 的补全
     */
    private fun handleAdminTab(args: Array<out String>): List<String>? {
        val adminAction = args[1].lowercase()
        val thirdArg = args[2].lowercase() // 可能是 ID，也可能是 arena 的 subAction

        val list = when (adminAction) {
            // 模式 A: /kg admin arena [sub] [val] -> 此时 thirdArg 是 subAction
            "arena" -> when (thirdArg) {
                "setpos" -> listOf("1", "2")
                "setspawn", "setkit" -> listOf("red", "blue")
                else -> emptyList()
            }

            // 模式 B: /kg admin [action] [ID] [sub] -> 此时需要补全 subAction
            "bank" -> listOf("see", "log", "add", "remove", "set")
            "exp" -> listOf("add", "remove", "set")

            // 模式 C: /kg admin [action] [ID] [val] -> 此时直接提示值
            "vault" -> (1..9).map { it.toString() }
            "rename" -> listOf("<name>")
            "setlevel" -> listOf("<level>")

            // 模式 D: /kg admin [action] [ID] [Player] -> 此时补全玩家
            "transfer", "kick", "join" -> return null

            else -> emptyList()
        }

        return filterList(list, args[3])
    }

    /**
     * 辅助方法：过滤列表
     */
    private fun filterList(list: List<String>?, input: String): List<String>? {
        // 如果 list 为 null，直接返回 null（Bukkit 会补全玩家名）
        // 如果 list 存在，过滤后返回
        return list?.filter { it.startsWith(input, ignoreCase = true) }
    }

    /**
     * 辅助方法：获取 Buff 补全
     */
    private fun getBuffTab(player: Player): List<String> {
        val guildId = plugin.playerGuildCache[player.uniqueId] ?: return emptyList()
        val level = plugin.dbManager.getGuildData(guildId)?.level ?: 1
        return plugin.levelsConfig.getStringList("levels.$level.use-buff")
    }
    /**
     * 根据在线状态渲染成员列表颜色
     */
    private fun renderMemberList(memberNames: List<String>, guildId: Int): String {
        // 获取当前公会所有在线玩家的名字集合 (为了性能，先转为 Set)
        val onlineNames = plugin.server.onlinePlayers
            .filter { plugin.playerGuildCache[it.uniqueId] == guildId }
            .map { it.name }
            .toSet()

        return memberNames.joinToString("§7, ") { name ->
            if (onlineNames.contains(name)) "§a$name" else "§f$name"
        }
    }
}