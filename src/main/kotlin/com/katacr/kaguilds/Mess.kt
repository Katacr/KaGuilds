package com.katacr.kaguilds

import org.bukkit.command.CommandSender
import taboolib.module.chat.colored

fun CommandSender.tips(vararg info: String) {
    info.forEach {
        this.sendMessage("&7[&9公会&7] &6${it}".colored())
    }
}

fun CommandSender.error(vararg info: String) {
    info.forEach {
        this.sendMessage("&7[&9公会&7] &c${it}".colored())
    }
}