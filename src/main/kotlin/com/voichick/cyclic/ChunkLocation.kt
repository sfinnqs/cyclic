package com.voichick.cyclic

import org.bukkit.Chunk
import java.lang.Math.floorMod

data class ChunkLocation(val x: Int, val z: Int) {
    val isRepresentative
        get() = x in 0 until X_CHUNKS && z in 0 until Z_CHUNKS

    fun equalsParallel(other: ChunkLocation): Boolean {
        return floorMod(other.x - x, X_CHUNKS) == 0 && floorMod(other.z - z, Z_CHUNKS) == 0
    }
}