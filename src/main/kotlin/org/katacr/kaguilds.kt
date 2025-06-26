package org.katacr

import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

object KaGuilds : Plugin() {

    override fun onEnable() {
        info("KaGuilds onEnable!")
    };

    override fun onLoad() {
        info("KaGuilds onLoad!")
    };
    override fun onActive() {
        info("KaGuilds onActive!")
    }
    override fun onDisable() {
        info("KaGuilds onDisable!")

    };
}