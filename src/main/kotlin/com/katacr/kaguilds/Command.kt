package com.katacr.kaguilds

import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.command

object Command {
    @Awake(LifeCycle.ENABLE)
    fun regCommand(){
        // 注册指令头
        command(name = "KaGuilds", aliases = listOf("kg", "g", "guilds")){
            //二级指令
            literal("create"){
                dynamic(comment = "请输入公会名称") {
                    execute<Player> {sender, context, _ ->
                        checkName(context.get(1),sender)
                    }
                }
            }
        }
    }

}