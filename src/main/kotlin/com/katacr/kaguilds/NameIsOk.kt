package com.katacr.kaguilds

import org.bukkit.entity.Player


fun checkName(name: String, sender: Player) {
    if (name.length < 2) {
        return sender.tips("创建失败，您至少要输入2个字。")
    } else {
        var x = 0
        var leng = 0
        for (i in name) {
            leng += if (isAlp(name[x].toString())) {
                //若是汉字则长度+2，否则长度+1
                2
            } else 1
            x += 1
        }
        return if (leng <= 12) {
            sender.tips("创建成功，您已成功创建[${name}]公会。")
        } else
            sender.error("创建失败，您输入的名称长度过长。")
    }
}

//遍历判断每个字符是否为汉字
fun isAlp(string: String): Boolean {
    val regex = """[\u4e00-\u9fa5]+""".toRegex()
    return regex.containsMatchIn(input = string)
}

