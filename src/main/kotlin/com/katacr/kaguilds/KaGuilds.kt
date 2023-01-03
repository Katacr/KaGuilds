package com.katacr.kaguilds

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.Plugin

object KaGuilds : Plugin() {

    @Awake(LifeCycle.ENABLE)
      fun loadMess(){
        println("KaGuild插件已成功加载！")
      }
}