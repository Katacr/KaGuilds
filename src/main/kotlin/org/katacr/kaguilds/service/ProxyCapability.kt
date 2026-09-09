package org.katacr.kaguilds.service

import org.katacr.kaguilds.KaGuilds
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 代理能力探测。
 *
 * 探测流程：
 *  1. 首个玩家进入后延迟 5 秒发送 ServerHello 探针。
 *  2. 未收到回复则每隔 20 秒重试，最多 3 次。
 *  3. 收到 KaProxy 回复（OnlinePlayersList / ServerIdentify）后停止探针，启用跨服。
 *  4. 不会自动降级、不会写回 config.yml。
 */
class ProxyCapability(private val plugin: KaGuilds) {

    private val desiredProxy: Boolean = plugin.config.getBoolean("proxy", false)

    @Volatile
    private var effectiveProxy: Boolean = false

    private val verified = AtomicBoolean(false)

    /** 已派出的探针任务 ID，收到回复后取消。 */
    @Volatile
    private var probeTaskId: Int = -1

    /** 已派过首次探针（避免多玩家重复触发）。 */
    private val firstProbeScheduled = AtomicBoolean(false)

    /** 最大重试次数。 */
    private val maxRetries = 3

    /** 重试间隔（tick）。 */
    private val retryIntervalTicks = 400L // 20 秒

    fun start() {
        if (!desiredProxy) {
            effectiveProxy = false
            return
        }

        val dbType = plugin.config.getString("database.type", "SQLite") ?: "SQLite"
        if (!dbType.equals("MySQL", ignoreCase = true)) {
            plugin.logger.warning("[KaGuilds] proxy 模式要求使用 MySQL 数据库，当前为 $dbType。" +
                "SQLite 不支持多服并发写入，可能导致数据损坏。请修改 database.type 为 MySQL 后重启。")
            effectiveProxy = false
            return
        }

        effectiveProxy = false
    }

    /**
     * 首个玩家进入时调用，延迟 5 秒后发起首次探针。
     */
    fun onPlayerJoinProbe() {
        if (!desiredProxy || verified.get()) return

        // 只调度一次首次探针
        if (firstProbeScheduled.compareAndSet(false, true)) {
            probeTaskId = plugin.server.scheduler.runTaskLater(plugin, Runnable {
                sendProbe(retryCount = 0)
            }, 100L).taskId // 5 秒
        }
    }

    /**
     * 发送探针并在未收到回复时安排重试。
     */
    private fun sendProbe(retryCount: Int) {
        if (verified.get()) return

        sendServerHello()

        if (retryCount < maxRetries) {
            probeTaskId = plugin.server.scheduler.runTaskLater(plugin, Runnable {
                sendProbe(retryCount + 1)
            }, retryIntervalTicks).taskId
        } else {
            plugin.logger.warning("[KaGuilds] 多次探测后仍未收到 KaProxy 信号，跨服功能暂时不可用。" +
                "请确认 KaProxy 已安装并启用 Guilds 模块（legacy-channel-enabled: true）。")
        }
    }

    /**
     * 由 PluginMessageListener 在收到来自代理的信号时调用。
     */
    fun markProxyReachable() {
        if (desiredProxy && verified.compareAndSet(false, true)) {
            effectiveProxy = true
            // 取消正在进行的重试任务
            if (probeTaskId != -1) {
                plugin.server.scheduler.cancelTask(probeTaskId)
                probeTaskId = -1
            }
            plugin.logger.info("[KaGuilds] 检测到 KaProxy 通讯正常，已启用跨服模式。")
        }
    }

    fun isProxyEnabled(): Boolean = effectiveProxy && desiredProxy

    fun desiredProxy(): Boolean = desiredProxy

    private fun sendServerHello() {
        try {
            val out = java.io.ByteArrayOutputStream()
            java.io.DataOutputStream(out).use { it.writeUTF("ServerHello") }
            plugin.server.onlinePlayers.firstOrNull()
                ?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
                ?: plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    plugin.server.onlinePlayers.firstOrNull()
                        ?.sendPluginMessage(plugin, "kaguilds:chat", out.toByteArray())
                }, 20L)
        } catch (e: Exception) {
            plugin.logger.warning("发送 ServerHello 探针失败: ${e.message}")
        }
    }
}
