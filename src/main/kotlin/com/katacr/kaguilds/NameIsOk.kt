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
                //若是则长度+1，否则长度+2
                1
            } else 2
            x += 1
        }
        return if (leng < 11) {
            sender.tips("创建成功，您已成功创建[${name}]公会。")
        } else
            sender.error("创建失败，您输入的名称长度过长。")
    }
}

//遍历判断每个字符是否为字母数字
fun isAlp(string: String): Boolean {
    val regex = """[a-zA-Z0-9_]+""".toRegex()
    return regex.containsMatchIn(input = string)
}

