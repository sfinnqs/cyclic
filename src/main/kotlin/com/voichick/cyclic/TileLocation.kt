package com.voichick.cyclic

//data class TileLocation(val x: Int, val z: Int) : Iterable<Pair<Int, Int>> {
//    override fun iterator(): Iterator<Pair<Int, Int>> {
//        val xVals = (0 until X_CHUNKS).map {
//            x * X_CHUNKS + it
//        }
//        val zVals = (0 until Z_CHUNKS).map {
//            z * Z_CHUNKS + it
//        }
//        return xVals.flatMap { chunkX -> zVals.map { chunkZ -> chunkX to chunkZ } }.iterator()
//    }
//
//    val neighbors: Set<TileLocation>
//        get() {
//            val result = mutableSetOf<TileLocation>()
//            for (neighborX in (x - RADIUS)..(x + RADIUS))
//                for (neighborZ in (z - RADIUS)..(z + RADIUS))
//                    result.add(TileLocation(neighborX, neighborZ))
//            return result
//        }
//
//}