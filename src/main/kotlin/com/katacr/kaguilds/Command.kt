package com.katacr.kaguilds

import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.command

object Command {
    @Awake(LifeCycle.ENABLE)
    fun regCommand(){
        command(name = "KaGuilds", aliases = listOf("kg", "g", "guilds")){
            literal("create"){
                dynamic() {
                    execute<Player> {sender, context, _ ->
                        var name = context.get(1)
                        if (name.length > 6){
                            sender.error("创建失败，您输入的长度过长。")
                        }else
                            sender.tips("创建成功，您已成功创建${name}。")

                    }
                }
            }
        }
    }

}