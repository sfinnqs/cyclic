package com.voichick.cyclic

import net.jcip.annotations.NotThreadSafe
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import java.lang.Math.floorMod

@NotThreadSafe
class CyclicListener(private val plugin: Cyclic) : Listener {

    // TODO priority
    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        val x = chunk.x
        val z = chunk.z
        if (x in 0 until X_CHUNKS && z in 0 until Z_CHUNKS)
            return
        val srcChunk = chunk.world.getChunkAt(floorMod(x, X_CHUNKS), floorMod(z, Z_CHUNKS))
        // TODO faster copy
        for (localX in 0..15)
            for (y in 0..255)
                for (localZ in 0..15)
                    chunk.getBlock(localX, y, localZ).setBlockData(srcChunk.getBlock(localX, y, localZ).blockData, false)
    }

    // TODO priority
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.isCancelled) return
        val block = event.block
        for (other in block.parallelBlocks)
            other.setType(event.itemInHand.type, false)
    }

    // TODO priority
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.isCancelled) return
        for (other in event.block.parallelBlocks)
            other.setType(Material.AIR, false)
    }

    // TODO priority
    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        event.isSaveChunk = chunk.x in 0 until X_CHUNKS && chunk.z in 0 until Z_CHUNKS
    }

    // TODO priority
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        event.player.teleport(event.player.location.representative)
    }

    private val Location.representative: Location
        get() {
            val offsetX = blockX - floorMod(blockX, MAX_X)
            val offsetZ = blockZ - floorMod(blockZ, MAX_Z)
            return subtract(offsetX.toDouble(), 0.0, offsetZ.toDouble())
        }

    private val Block.parallelBlocks: Set<Block>
        get() {
            val localX = this.x and 0xf
            val y = this.y
            val localZ = this.z and 0xf
            val chunk = this.chunk
            val chunkX = chunk.x
            val chunkZ = chunk.z
            val srcX = floorMod(chunkX, X_CHUNKS)
            val srcZ = floorMod(chunkZ, Z_CHUNKS)
            val world = chunk.world
            return world.loadedChunks.filter {
                it != chunk && floorMod(it.x, X_CHUNKS) == srcX && floorMod(it.z, Z_CHUNKS) == srcZ
            }.map {
                it.getBlock(localX, y, localZ)
            }.toSet()
        }

}