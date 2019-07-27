package com.voichick.cyclic

import java.lang.Math.floorMod

data class ChunkLocation(val x: Int, val z: Int) {
    fun equalsParallel(other: ChunkLocation): Boolean {
        return floorMod(other.x - x, X_CHUNKS) == 0 && floorMod(other.z - z, Z_CHUNKS) == 0
    }
}