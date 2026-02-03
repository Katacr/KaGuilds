package org.katacr.kaguilds.arena

import org.bukkit.Location

data class ArenaData(
    var pos1: Location? = null,
    var pos2: Location? = null,
    var redSpawn: Location? = null,
    var blueSpawn: Location? = null
)