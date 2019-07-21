package com.voichick.cyclic

import org.bukkit.Bukkit
import org.bukkit.util.NumberConversions.floor
import java.lang.Math.floorMod
import kotlin.math.min

data class ClientLocation(val clientX: Double, val clientZ: Double) {

    val clientBlockX = floor(clientX)
    val clientBlockZ = floor(clientZ)
    val serverBlockX = floorMod(clientBlockX, MAX_X)
    val serverBlockZ = floorMod(clientBlockZ, MAX_Z)
    val offsetX = clientBlockX - serverBlockX
    val offsetZ = clientBlockZ - serverBlockZ
    val chunkOffsetX = offsetX shr 4
    val chunkOffsetZ = offsetZ shr 4
    val serverX = clientX - offsetX
    val serverZ = clientZ - offsetZ
    val clientChunkX = clientBlockX shr 4
    val clientChunkZ = clientBlockZ shr 4
    val serverChunkX = serverBlockX shr 4
    val serverChunkZ = serverBlockZ shr 4
    val chunk
        get() = ChunkLocation(clientChunkX, clientChunkZ)

    fun getNeighbors(view: Int): Set<ChunkLocation> {
        return (-view..view).flatMap { viewOffsetX ->
            (-view..view).map { viewOffsetZ ->
                viewOffsetX to viewOffsetZ
            }
        }.shuffled().sortedBy { (viewOffsetX, viewOffsetZ) ->
            viewOffsetX * viewOffsetX + viewOffsetZ * viewOffsetZ
        }.map { (viewOffsetX, viewOffsetZ) ->
            ChunkLocation(clientChunkX + viewOffsetX, clientChunkZ + viewOffsetZ)
        }.toSet()
    }

}