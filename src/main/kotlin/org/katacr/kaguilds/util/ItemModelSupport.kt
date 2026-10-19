package org.katacr.kaguilds.util

import org.bukkit.Bukkit
import org.bukkit.inventory.meta.ItemMeta

/**
 * ItemModel（1.21.4+）与 CustomModelData 的统一应用逻辑。
 * 收敛原先散落在 MenuManager 中 4 份几乎相同的版本判断 + 反射实现。
 */
object ItemModelSupport {

    /** 是否支持 ItemModel API（1.21.4+）。结果缓存一次，避免反复解析版本。 */
    private val supportsItemModel: Boolean by lazy {
        val parts = Bukkit.getBukkitVersion().split(".")
        val minor = parts.getOrNull(1)?.toIntOrNull()
        val patch = parts.getOrNull(2)?.split("-")?.get(0)?.toIntOrNull()
        when {
            minor == null -> false
            minor > 21 -> true
            minor == 21 -> (patch ?: 0) >= 4
            else -> false
        }
    }

    /**
     * 将 item_model（namespace:key）应用到物品；不支持或格式错误时回退到 customModelData。
     * @param itemModel namespace:key；null/空 表示直接使用 customModelData
     * @param customModelData 回退值
     */
    fun apply(meta: ItemMeta, itemModel: String?, customModelData: Int?) {
        if (!itemModel.isNullOrEmpty() && supportsItemModel) {
            val parts = itemModel.split(":")
            if (parts.size == 2) {
                try {
                    val namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey")
                    val itemMetaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta")
                    val setItemModelMethod = itemMetaClass.getMethod("setItemModel", namespacedKeyClass)
                    val modelKey = namespacedKeyClass.getDeclaredConstructor(
                        String::class.java, String::class.java
                    ).newInstance(parts[0], parts[1])
                    setItemModelMethod.invoke(meta, modelKey)
                    return
                } catch (e: Exception) {
                    // 反射失败，回退
                }
            }
        }
        if (customModelData != null) {
            meta.setCustomModelData(customModelData)
        }
    }
}
