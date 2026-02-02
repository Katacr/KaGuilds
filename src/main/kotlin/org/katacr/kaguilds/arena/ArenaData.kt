package org.katacr.kaguilds.arena

import org.bukkit.Location
import org.bukkit.util.Vector

data class ArenaData(
    var pos1: Location? = null,
    var pos2: Location? = null,
    var redSpawn: Location? = null,
    var blueSpawn: Location? = null
) {
    // 检查竞技场设置是否完整
    fun isComplete(): Boolean = pos1 != null && pos2 != null && redSpawn != null && blueSpawn != null

    // 检查某个坐标是否在 pos1 和 pos2 构成的立方体范围内
    fun isWithin(loc: Location): Boolean {
        if (pos1 == null || pos2 == null) return false
        if (loc.world != pos1!!.world) return false

        val minX = minOf(pos1!!.x, pos2!!.x)
        val maxX = maxOf(pos1!!.x, pos2!!.x)
        val minY = minOf(pos1!!.y, pos2!!.y)
        val maxY = maxOf(pos1!!.y, pos2!!.y)
        val minZ = minOf(pos1!!.z, pos2!!.z)
        val maxZ = maxOf(pos1!!.z, pos2!!.z)

        return loc.x in minX..maxX && loc.y in minY..maxY && loc.z in minZ..maxZ
    }
}