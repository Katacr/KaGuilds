package org.katacr.kaguilds


import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.katacr.kaguilds.service.OperationResult
import org.katacr.kaguilds.service.GuildService

class GuildCommand(private val plugin: KaGuilds) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        val lang = plugin.langManager
        val whiteList = listOf("help", "create", "join", "accept", "requests", "reload", "admin", "confirm", "yes", "no")
        val subCommand = args[0].lowercase()

        // 1. 帮助与控制台基础检查
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            if (sender is Player) sendHelp(sender) else sender.sendMessage(lang.get("console-help"))
            return true
        }

        // 2. 处理 /kg reload 命令
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
            else -> {
                sender.sendMessage(lang.get("unknown-command"))
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
        if (args.size < 2) {
            player.sendMessage(lang.get("buff-usage"))
            return
        }
        val buffKey = args[1]
        plugin.guildService.buyBuff(player, buffKey) { result ->

            when (result) {
                is OperationResult.Success -> {} // 成功消息由 dispatchBuff 统一发送
                is OperationResult.InsufficientFunds -> player.sendMessage(lang.get("create-insufficient")) // 复用余额不足
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }


    /*
     * 处理 /kg chat 命令
     */
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

    /*
     * 处理 /kg bank 命令
     */
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

                        // 3. 发放金币给玩家 (回到同步线程)
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            econ.depositPlayer(player, amount)
                            player.sendMessage(lang.get("bank-get-success", "amount" to amount.toString()))
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
        val role = plugin.dbManager.getPlayerRole(player.uniqueId)
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
        val role = plugin.dbManager.getPlayerRole(player.uniqueId)
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
                else -> player.sendMessage("§cError: ${result.toString()}")
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
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("error-missing-args"))
            player.sendMessage(plugin.langManager.get("invite-usage"))
            return
        }

        plugin.guildService.invitePlayer(player, args[1]) { result ->
            val lang = plugin.langManager
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
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("join-usage"))
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

                    player.sendMessage(plugin.langManager.get("join-success", "guild" to displayName))
                }
                is OperationResult.AlreadyInGuild -> player.sendMessage(plugin.langManager.get("join-already"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg requests 命令
     */
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

    /*
     * 处理 /kg accept 命令
     */
    private fun handleAccept(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("error-missing-args"))
            player.sendMessage(plugin.langManager.get("accept-usage"))
            return
        }

        // 调用 Service 层处理申请逻辑 (之前已经写好的 acceptRequest)
        plugin.guildService.acceptRequest(player, args[1]) { result ->
            val lang = plugin.langManager
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

    /*
     * 处理 /kg help 命令
     */
    private fun sendHelp(sender: Player) {
        val lang = plugin.langManager

        // 1. 显式传入 false，调用 get(key, boolean, vararg)
        sender.sendMessage(lang.get("help-header"))

        val cmds = mapOf(
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
            "yes" to "desc-yes",
            "no" to "desc-no",
            "reload" to "desc-reload",
            "buff <BuffName>" to "desc-buff",
            "bank <add|get|log>" to "desc-bank",
            "chat <Message>" to "desc-chat",
            "rename <NewName>" to "desc-rename",
            "settp" to "desc-settp",
            "tp" to "desc-tp",


        )

        cmds.forEach { (u, d) ->

            sender.sendMessage(
                lang.get(
                    "help-format",
                    "usage" to u,
                    "description" to lang.get(d)
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
    /*
     * 处理 /kg yes 命令
     */
    private fun handleYes(player: Player) {
        plugin.guildService.acceptInvite(player) { result ->
            val lang = plugin.langManager
            when (result) {
                is OperationResult.Success -> {
                    player.sendMessage(lang.get("invite-accepted"))
                    // 播放成功音效
                    player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
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
        if (!plugin.inviteCache.containsKey(player.uniqueId)) {
            player.sendMessage(plugin.langManager.get("invite-none"))
            return
        }
        plugin.guildService.declineInvite(player)
        player.sendMessage(plugin.langManager.get("invite-declined"))
    }

    /*
     * 处理 /kg settp 命令
     */
    private fun handleSetTp(player: Player) {
        plugin.guildService.setGuildTP(player) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(plugin.langManager.get("tp-set-success"))
                is OperationResult.NoPermission -> player.sendMessage(plugin.langManager.get("not-staff"))
                else -> player.sendMessage((result as OperationResult.Error).message)
            }
        }
    }

    /*
     * 处理 /kg tp 命令
     */
    private fun handleTp(player: Player) {
        plugin.guildService.teleportToGuild(player) { result ->
            when (result) {
                is OperationResult.Success -> player.sendMessage(plugin.langManager.get("tp-success"))
                is OperationResult.Error -> player.sendMessage(result.message)
                else -> {}
            }
        }
    }

    /*
     * 处理 /kg rename 命令
     */
    private fun handleRename(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.langManager.get("rename-usage"))
            return
        }

        // 2. 定义变量 newName
        val newName = args[1]

        // 3. 调用 Service
        plugin.guildService.renameGuild(player, newName) { result ->
            when (result) {
                is OperationResult.Success -> {
                    // 如果需要显示扣费金额，可以从 config 读取
                    val price = plugin.config.getDouble("balance.rename", 5000.0).toString()
                    player.sendMessage(plugin.langManager.get("rename-success",
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
        val action = plugin.guildService.consumePendingAction(player.uniqueId) ?: run {
            player.sendMessage(plugin.langManager.get("confirm-no-pending"))
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
                is OperationResult.NameAlreadyExists -> player.sendMessage(plugin.langManager.get("create-exists"))
                is OperationResult.InsufficientFunds -> player.sendMessage(plugin.langManager.get("create-insufficient"))
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
                player.sendMessage(lang.get("no-guild-admin")) // 确保语言文件有这个节点
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
        // 权限检查
        if (!sender.hasPermission("kaguilds.admin")) {
            sender.sendMessage(lang.get("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(lang.get("admin-usage"))
            return
        }

        when (args[1].lowercase()) {
            "rename" -> {
                // 格式: /kg admin rename #ID [新名称]
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
                if (args.size < 4) {

                    sender.sendMessage(lang.get("admin-bank-usage"))
                    return
                }
                val guildId = args[2].replace("#", "").toIntOrNull() ?: return sender.sendMessage(lang.get("error-invalid-id"))
                val action = args[3].lowercase()

                when (action) {
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
                                sender.sendMessage("§8§o(输入 /kg admin bank #$guildId log ${page + 1} 查看下一页)")
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
                // 格式: /kg admin vault #ID [编号]
                if (args.size < 3) {
                    sender.sendMessage("§c用法: /kg admin vault #ID [编号]")
                    return
                }

                val guildId = args[2].replace("#", "").toIntOrNull()
                    ?: return sender.sendMessage("§c无效的公会 ID")

                val index = if (args.size > 3) args[3].toIntOrNull() ?: 1 else 1

                // 调用 Service 层专门给 admin 用的方法
                plugin.guildService.adminOpenVault(sender as Player, guildId, index)
            }

            "unlockall" -> {
                // 用法: /kg admin unlockall
                // 效果：清空全服务器（及跨服）的所有仓库锁
                plugin.guildService.forceResetAllLocks()
                sender.sendMessage("§a[管理] 已强制重置所有云库存锁，并广播至全服。")
            }

            else -> sender.sendMessage(lang.get("admin-usage"))
        }
    }

    /*
     * 处理 /kg vault <index>
     */
    private fun handleVault(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

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
                player.sendMessage("§c请输入正确的仓库编号 (1-9)")
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
     * 处理指令自动补全
     */
    override fun onTabComplete(sender: CommandSender, cmd: Command, alias: String, args: Array<out String>): List<String>? {
        if (sender !is Player) return emptyList()

        return when (args.size) {
            // 第一级指令补全: /kg <TAB>
            1 -> {
                val list = mutableListOf(
                    "help", "create", "join", "info", "requests", "accept", "promote",
                    "demote", "leave", "kick", "delete", "chat", "bank", "invite",
                    "yes", "no", "settp", "tp", "rename", "buff", "deny" , "transfer", "vault"
                )

                // 权限指令判断
                if (sender.hasPermission("kaguilds.admin")) {
                    list.add("reload")
                    list.add("admin")
                }

                // 动态补全：如果玩家有待确认操作，显示 confirm
                if (plugin.guildService.hasPendingAction(sender.uniqueId)) {
                    list.add("confirm")
                }

                list.filter { it.startsWith(args[0], ignoreCase = true) }
            }

            // 第二级指令补全: /kg sub <TAB>
            2 -> {
                val subCommand = args[0].lowercase()
                when (subCommand) {
                    "admin" -> {
                        if (!sender.hasPermission("kaguilds.admin")) return emptyList()

                        listOf("info", "rename", "delete", "bank", "transfer", "kick", "join", "vault")
                            .filter { it.startsWith(args[1], ignoreCase = true) }
                    }

                    "bank" -> {
                        listOf("add", "get", "log").filter { it.startsWith(args[1], ignoreCase = true) }
                    }

                    "buff" -> {
                        val guildId = plugin.playerGuildCache[sender.uniqueId]
                        if (guildId != null) {
                            val guildData = plugin.dbManager.getGuildData(guildId)
                            val level = guildData?.level ?: 1
                            val allowedBuffs = plugin.config.getStringList("level.$level.use-buff")
                            if (allowedBuffs.isNotEmpty()) {
                                return allowedBuffs.filter { it.startsWith(args[1], ignoreCase = true) }
                            }
                        }
                        val section = plugin.config.getConfigurationSection("guild.buffs")
                        section?.getKeys(false)?.filter { it.startsWith(args[1], ignoreCase = true) }?.toList() ?: emptyList()
                    }
                    "vault" -> {
                        // 获取玩家公会 ID
                        val guildId = plugin.playerGuildCache[sender.uniqueId]
                        if (guildId != null) {
                            // 异步获取不方便，这里我们可以直接从 config 读取该公会等级允许的最大页数
                            // 或者简单点直接返回 1..9
                            return (1..9).map { it.toString() }.filter { it.startsWith(args[1]) }
                        }
                        emptyList()
                    }
                    "kick", "promote", "demote", "invite", "join", "accept" -> null
                    else -> emptyList()
                }
            }

            3 -> {
                if (args[0].equals("admin", ignoreCase = true)) {
                    // 提示 # 引导输入 ID，或者如果是 reload 等不需要 ID 的子指令则不返回
                    return listOf("#").filter { it.startsWith(args[2]) }
                }
                if (args[0].equals("admin", ignoreCase = true) && args[1].equals("vault", ignoreCase = true)) {
                    // 这里可以返回 "#" 提示管理员输入 ID
                    return listOf("#")
                }
                emptyList()
            }

            4 -> {
                val sub = args[0].lowercase()
                val adminSub = args[1].lowercase()

                if (sub == "admin") {
                    when (adminSub) {
                        "bank" -> {
                            // /kg admin bank #1 <TAB>
                            return listOf("see", "log", "add", "remove", "set").filter { it.startsWith(args[3], ignoreCase = true) }
                        }
                        "kick", "join", "transfer" -> {
                            // /kg admin transfer #1 <TAB> -> 补全在线玩家
                            return null // 返回 null 代表由 Bukkit 默认补全在线玩家名
                        }
                        "rename" -> {
                            // /kg admin rename #1 <TAB> -> 提示输入名称
                            return listOf("<name>")
                        }
                        "vault" -> {
                            return (1..9).map { it.toString() }
                        }
                    }
                }
                emptyList()
            }

            5 -> {
                if (args[0].equals("admin", ignoreCase = true) && args[1].equals("bank", ignoreCase = true)) {
                    val bankAction = args[3].lowercase()
                    if (listOf("add", "remove", "set").contains(bankAction)) {
                        return listOf("<money>")
                    } else if (bankAction == "log") {
                        return listOf("<page>")
                    }
                }
                emptyList()
            }
            else -> emptyList()
        }
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