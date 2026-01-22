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

        // 1. 帮助与控制台基础检查
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            if (sender is Player) sendHelp(sender) else sender.sendMessage(lang.get("console-help"))
            return true
        }

        val subCommand = args[0].lowercase()

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

        if (sender !is Player) {
            sender.sendMessage(lang.get("player-only"))
            return true
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
     * 处理 /kg leave 命令
     */
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

    /*
     * 处理 /kg create 命令
     */
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

    /*
     * 处理 /kg info 命令
     */
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
                    "yes", "no", "settp", "tp", "rename", "buff"
                )
                if (sender.hasPermission("kaguilds.admin")) list.add("reload")
                list.filter { it.startsWith(args[0], ignoreCase = true) }
            }

            // 第二级指令补全: /kg sub <TAB>
            2 -> {
                val subCommand = args[0].lowercase()
                when (subCommand) {
                    // 金库二级指令
                    "bank" -> {
                        listOf("add", "get", "log").filter { it.startsWith(args[1], ignoreCase = true) }
                    }

                    // Buff 二级指令 (从配置文件动态读取)
                    "buff" -> {
                        // 获取当前玩家公会等级 (同步获取，若数据库性能好可直接查，或查缓存)
                        val guildId = plugin.playerGuildCache[sender.uniqueId]
                        if (guildId != null) {
                            // 尝试从数据库获取等级 (注意: 同步操作建议有缓存，否则建议直接展示所有Buff)
                            val guildData = plugin.dbManager.getGuildData(guildId)
                            val level = guildData?.level ?: 1

                            // 只展示该等级允许使用的 Buff
                            val allowedBuffs = plugin.config.getStringList("level.$level.use-buff")
                            if (allowedBuffs.isNotEmpty()) {
                                return allowedBuffs.filter { it.startsWith(args[1], ignoreCase = true) }
                            }
                        }
                        // 如果不在公会，展示所有配置的 Buff 键
                        val section = plugin.config.getConfigurationSection("guild.buffs")
                        section?.getKeys(false)?.filter { it.startsWith(args[1], ignoreCase = true) }?.toList()
                    }

                    // 针对需要玩家名的指令，返回 null 会自动触发 Bukkit 默认的在线玩家补全
                    "kick", "promote", "demote", "invite", "join", "accept" -> null

                    else -> emptyList()
                }
            }

            // 第三级指令 (例如 /kg bank log <TAB>)
            3 -> {
                // 如果有需要可以继续扩展，目前逻辑下返回空
                emptyList()
            }

            else -> emptyList()
        }
    }
}