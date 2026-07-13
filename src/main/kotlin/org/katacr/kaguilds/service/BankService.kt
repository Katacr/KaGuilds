package org.katacr.kaguilds.service

import org.bukkit.entity.Player
import org.katacr.kaguilds.KaGuilds

/**
 * 公会金库服务，负责玩家金库存取款、日志查看、贡献度和任务触发。
 */
class BankService(private val plugin: KaGuilds) {

    /**
     * 处理玩家金库子命令参数。
     */
    fun handleBank(player: Player, args: Array<out String>) {
        val lang = plugin.langManager

        if (args.size < 2) {
            player.sendMessage(lang.get("bank-usage"))
            return
        }

        val action = args[1].lowercase()

        if (action == "log") {
            val page = if (args.size >= 3) args[2].toIntOrNull() ?: 1 else 1
            if (page < 1) {
                player.sendMessage(lang.get("bank-log-invalid-page"))
                return
            }

            plugin.runAsync {
                val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(player.uniqueId) ?: return@runAsync

                if (!plugin.dbManager.memberRepository.isStaff(player.uniqueId, guildId)) {
                    plugin.runMain {
                        player.sendMessage(lang.get("not-staff"))
                    }
                    return@runAsync
                }

                val totalPages = plugin.dbManager.bankRepository.getBankLogTotalPages(guildId)
                if (totalPages in 1..<page) {
                    plugin.runMain {
                        player.sendMessage(lang.get("bank-log-invalid-page"))
                    }
                    return@runAsync
                }

                val logs = plugin.dbManager.bankRepository.getBankLogs(guildId, page)
                plugin.runMain {
                    player.sendMessage(lang.get("bank-log-header", "page" to page.toString(), "total" to totalPages.toString()))

                    if (logs.isEmpty()) {
                        player.sendMessage(lang.get("bank-log-empty"))
                    } else {
                        logs.forEach { player.sendMessage(it) }
                        if (page < totalPages) {
                            player.sendMessage(lang.get("bank-log-next-page", "page" to (page + 1).toString()))
                        }
                    }
                }
            }
            return
        }

        if (args.size < 3) {
            player.sendMessage(lang.get("bank-usage"))
            return
        }

        val amountLong = args[2].toLongOrNull()
        if (amountLong == null || amountLong <= 0) {
            player.sendMessage(lang.get("bank-invalid-amount"))
            return
        }

        val amount = amountLong.toDouble()
        val econ = plugin.economy ?: return
        val playerUuid = player.uniqueId
        val playerName = player.name

        plugin.runAsync {
            val guildId = plugin.dbManager.memberRepository.getGuildIdByPlayer(playerUuid) ?: return@runAsync
            val guildData = plugin.dbManager.guildRepository.getGuildData(guildId) ?: return@runAsync
            val maxBank = plugin.levelsConfig.getLong("levels.${guildData.level}.max-money", 50000L).toDouble()

            when (action) {
                "add" -> deposit(player, playerUuid, playerName, guildId, guildData.balance, amount, amountLong, maxBank, econ)
                "take" -> withdraw(player, playerUuid, playerName, guildId, guildData.balance, amount, amountLong, econ)
                else -> plugin.runMain {
                    player.sendMessage(lang.get("bank-usage"))
                }
            }
        }
    }

    private fun deposit(
        player: Player,
        playerUuid: java.util.UUID,
        playerName: String,
        guildId: Int,
        currentBalance: Double,
        amount: Double,
        amountLong: Long,
        maxBank: Double,
        econ: net.milkbowl.vault.economy.Economy
    ) {
        val lang = plugin.langManager

        if (currentBalance + amount > maxBank) {
            plugin.runMain {
                player.sendMessage(lang.get("bank-full", "max" to maxBank.toString()))
            }
            return
        }

        plugin.runMain {
            if (!econ.has(player, amount)) {
                player.sendMessage(lang.get("bank-insufficient-player"))
                return@runMain
            }

            val withdrawResult = econ.withdrawPlayer(player, amount)
            if (!withdrawResult.transactionSuccess()) {
                player.sendMessage(withdrawResult.errorMessage ?: lang.get("error-vault"))
                return@runMain
            }

            plugin.runAsync {
                if (plugin.dbManager.bankRepository.depositGuildBalanceIfWithinLimit(guildId, amount, maxBank)) {
                    plugin.dbManager.bankRepository.logBankTransaction(guildId, playerName, "ADD", amount)

                    val contributionEnabled = plugin.config.getBoolean("contribution.enabled", true)
                    if (contributionEnabled) {
                        val bankDepositRatio = plugin.config.getDouble("contribution.bank-deposit-ratio", 1.0)
                        val earnedContribution = (amount * bankDepositRatio).toInt()
                        plugin.dbManager.memberRepository.addContribution(playerUuid, earnedContribution)
                    }

                    plugin.runMain {
                        player.sendMessage(lang.get("bank-add-success", "amount" to amountLong.toString()))
                        plugin.guildService.dispatchBankNotification(guildId, playerName, "deposit", amount)
                        plugin.taskListener.onGuildDonate(player, amount)
                    }
                } else {
                    plugin.runMain {
                        econ.depositPlayer(player, amount)
                        player.sendMessage(lang.get("bank-full", "max" to maxBank.toString()))
                    }
                }
            }
        }
    }

    private fun withdraw(
        player: Player,
        playerUuid: java.util.UUID,
        playerName: String,
        guildId: Int,
        currentBalance: Double,
        amount: Double,
        amountLong: Long,
        econ: net.milkbowl.vault.economy.Economy
    ) {
        val lang = plugin.langManager

        if (currentBalance < amount) {
            plugin.runMain {
                player.sendMessage(lang.get("bank-insufficient-guild"))
            }
            return
        }

        val contributionEnabled = plugin.config.getBoolean("contribution.enabled", true)
        if (contributionEnabled) {
            val contributionRatio = plugin.config.getDouble("contribution.bank-withdraw-ratio", 1.0)
            val requiredContribution = (amount * contributionRatio).toInt()
            val currentContribution = plugin.dbManager.memberRepository.getPlayerContribution(playerUuid)

            if (currentContribution < requiredContribution) {
                plugin.runMain {
                    player.sendMessage(lang.get(
                        "bank-insufficient-contribution",
                        "required" to requiredContribution.toString(),
                        "current" to currentContribution.toString()
                    ))
                }
                return
            }
        }

        if (plugin.dbManager.bankRepository.withdrawGuildBalanceIfEnough(guildId, amount)) {
            plugin.dbManager.bankRepository.logBankTransaction(guildId, playerName, "GET", amount)

            if (contributionEnabled) {
                val contributionRatio = plugin.config.getDouble("contribution.bank-withdraw-ratio", 1.0)
                val requiredContribution = (amount * contributionRatio).toInt()
                plugin.dbManager.memberRepository.addContribution(playerUuid, -requiredContribution)
            }

            plugin.runMain {
                econ.depositPlayer(player, amount)
                player.sendMessage(lang.get("bank-get-success", "amount" to amountLong.toString()))
                plugin.guildService.dispatchBankNotification(guildId, playerName, "withdraw", amount)
            }
        } else {
            plugin.runMain {
                player.sendMessage(lang.get("bank-insufficient-guild"))
            }
        }
    }
}
