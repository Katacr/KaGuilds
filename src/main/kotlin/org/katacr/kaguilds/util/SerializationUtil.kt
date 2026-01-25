package org.katacr.kaguilds.util

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object SerializationUtil {

    // 序列化：物品数组 -> 字符串
    fun itemsToBase64(items: Array<ItemStack?>): String {
        val outputStream = ByteArrayOutputStream()
        BukkitObjectOutputStream(outputStream).use { dataOutput ->
            dataOutput.writeInt(items.size)
            for (item in items) {
                dataOutput.writeObject(item)
            }
        }
        return Base64Coder.encodeLines(outputStream.toByteArray())
    }

    // 反序列化：字符串 -> 物品数组
    fun itemStackArrayFromBase64(data: String): Array<ItemStack?> {
        val inputStream = ByteArrayInputStream(Base64Coder.decodeLines(data))
        BukkitObjectInputStream(inputStream).use { dataInput ->
            val size = dataInput.readInt()
            val items = arrayOfNulls<ItemStack>(size)
            for (i in 0 until size) {
                items[i] = dataInput.readObject() as ItemStack?
            }
            return items
        }
    }
}