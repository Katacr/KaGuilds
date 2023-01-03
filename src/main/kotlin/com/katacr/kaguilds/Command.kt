package com.katacr.kaguilds

import org.bukkit.command.CommandSender
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
                dynamic {
                    execute<CommandSender> {sender, context, _ ->
                        val name = context.get(1)
                        if (!checkName(name)){
                            sender.error("创建失败，您输入的名称长度过长。")
                        }else
                            sender.tips("创建成功，您已成功创建[${name}]公会。")

                    }
                }
            }
        }
    }

}