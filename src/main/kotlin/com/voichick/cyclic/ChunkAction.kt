package com.voichick.cyclic

data class ChunkAction(val type: Type, val chunk: ChunkLocation, val distance: Double)
    : Comparable<ChunkAction> {

    override fun compareTo(other: ChunkAction): Int {
        when(type) {
            Type.LOAD -> if (other.type == Type.UNLOAD) return -1
            Type.UNLOAD -> if (other.type == Type.LOAD) return 1
        }
        assert(type == other.type)
        val distanceComp = distance.compareTo(other.distance)
        return if (distanceComp == 0)
            chunk.compareTo(other.chunk)
        else when(type) {
            Type.LOAD -> distanceComp
            Type.UNLOAD -> -distanceComp
        }
    }

    enum class Type {
        LOAD, UNLOAD
    }
}