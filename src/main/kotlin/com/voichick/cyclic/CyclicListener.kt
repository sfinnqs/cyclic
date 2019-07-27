package com.voichick.cyclic

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import net.jcip.annotations.NotThreadSafe
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.craftbukkit.v1_14_R1.CraftChunk
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.HIGHEST
import org.bukkit.event.EventPriority.LOWEST
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import java.lang.Math.floorMod

@NotThreadSafe
class CyclicListener(private val plugin: Cyclic) : Listener {

    @EventHandler(priority = LOWEST)
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        val x = chunk.x
        val z = chunk.z
        if (x in 0 until X_CHUNKS && z in 0 until Z_CHUNKS)
            return
        val srcChunk = chunk.world.getChunkAt(floorMod(x, X_CHUNKS), floorMod(z, Z_CHUNKS))
        val targetSections = (chunk as? CraftChunk)?.handle?.sections ?: return
        (srcChunk as? CraftChunk)?.handle?.sections?.copyInto(targetSections)
    }

    @EventHandler(priority = LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.manager.setLocation(player.uniqueId, ImmutableLocation(player))
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        plugin.manager.setLocation(player.uniqueId, ImmutableLocation(player))
    }

    @EventHandler(priority = LOWEST, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val newTo = event.to?.representative ?: return
        event.setTo(newTo)
        val player = event.player
        plugin.manager.setLocation(player.uniqueId, ImmutableLocation(newTo, player.isOnGround))
    }

    @EventHandler(priority = HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        event.player.teleport(player.location.representative)
        plugin.manager.unloadAllChunks(player)
        plugin.manager.setLocation(player.uniqueId, null)
    }

    @EventHandler(priority = HIGHEST)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        event.isSaveChunk = chunk.x in 0 until X_CHUNKS && chunk.z in 0 until Z_CHUNKS
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