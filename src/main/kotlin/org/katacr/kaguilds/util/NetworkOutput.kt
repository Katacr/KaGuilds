package org.katacr.kaguilds.util

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * 插件消息发送用的字节输出封装。
 * 收敛原先散落在 GuildService / VaultService 的重复实现。
 */
class NetworkOutput {
    private val byteArrayStream = ByteArrayOutputStream()
    val stream = DataOutputStream(byteArrayStream)

    fun toByteArray(): ByteArray = byteArrayStream.toByteArray()

    companion object {
        /** 创建一个已写入子频道名的输出流。 */
        fun of(subChannel: String): NetworkOutput = NetworkOutput().also { it.stream.writeUTF(subChannel) }
    }
}
