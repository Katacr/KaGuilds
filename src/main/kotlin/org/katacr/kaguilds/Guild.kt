package org.katacr.kaguilds

import java.util.UUID

data class Guild(
    val id: Int = 0,               // 数据库自动生成
    val name: String,
    val owner: UUID,
    val createTime: Long = System.currentTimeMillis(),
    var level: Int = 1,
    var experience: Int = 0,
    var balance: Double = 0.0,
    var maxMembers: Int = 10,
    var announcement: String = "欢迎来到公会！",
    var iconMaterial: String = "SHIELD" // 存物品 ID 字符串
){
    // 增加一个只读属性，专门用于获取主人名字
    val ownerName: String
        get() = org.bukkit.Bukkit.getOfflinePlayer(owner).name ?: "未知主人"
}