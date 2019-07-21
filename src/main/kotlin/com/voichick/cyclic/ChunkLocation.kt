package com.voichick.cyclic

data class ChunkLocation(val x: Int, val z: Int) : Comparable<ChunkLocation> {
    override fun compareTo(other: ChunkLocation): Int {
        val xComp = x.compareTo(other.x)
        if (xComp != 0) return xComp
        return z.compareTo(other.z)
    }
}