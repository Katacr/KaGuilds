package org.katacr.kaguilds.service

import org.katacr.kaguilds.KaGuilds
import org.katacr.kaguilds.util.NetworkCodec
import org.katacr.kaguilds.util.NetworkOutput
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 跨服消息发送统一入口。
 *
 * 约束：不改动子频道名与字段顺序（KaProxy 透传红线），仅统一发送方式：
 * - 统一经 [KaGuilds.isProxyEnabled] 校验；
 * - 统一借用在线玩家作为发送管道；
 * - 无在线玩家时入队，待有玩家上线时补发（避免空服瞬时丢包）。
 *
 * 消息内容统一由 [NetworkCodec] 编码，发送端不直接操作 writeUTF/writeInt。
 */
class NetworkBus(private val plugin: KaGuilds) {

    /** 空服时暂存的待发消息（有上限，避免极端情况无限增长）。 */
    private val pending = ConcurrentLinkedQueue<ByteArray>()
    private val maxPending = 200

    /**
     * 发送一个已由 [NetworkCodec] 编码好的插件消息。空服时入队等待补发。
     */
    fun send(out: NetworkOutput) {
        if (!plugin.isProxyEnabled()) return
        val bytes = out.toByteArray()
        val messenger = plugin.server.onlinePlayers.firstOrNull()
        if (messenger == null) {
            if (pending.size < maxPending) {
                pending.add(bytes)
                plugin.logger.fine("跨服消息暂存待补发（当前空服），队列 ${pending.size}。")
            } else {
                plugin.logger.warning("跨服消息待发队列已满，丢弃一条消息。")
            }
            return
        }
        try {
            messenger.sendPluginMessage(plugin, NetworkCodec.CHANNEL, bytes)
        } catch (e: Exception) {
            plugin.logger.warning("发送跨服消息失败: ${e.message}")
        }
    }

    /**
     * 玩家上线时调用，补发空服期间暂存的消息。
     */
    fun flushPending() {
        if (!plugin.isProxyEnabled() || pending.isEmpty()) return
        val messenger = plugin.server.onlinePlayers.firstOrNull() ?: return
        var sent = 0
        while (true) {
            val bytes = pending.poll() ?: break
            try {
                messenger.sendPluginMessage(plugin, NetworkCodec.CHANNEL, bytes)
                sent++
            } catch (e: Exception) {
                plugin.logger.warning("补发跨服消息失败: ${e.message}")
                break
            }
        }
        if (sent > 0) plugin.logger.info("已补发 $sent 条跨服消息。")
    }
}
