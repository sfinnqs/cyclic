package com.voichick.cyclic

import com.comphenix.protocol.PacketType.Play.Server.BLOCK_CHANGE
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.wrappers.BlockPosition
import com.comphenix.protocol.wrappers.WrappedBlockData
import net.jcip.annotations.NotThreadSafe
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.craftbukkit.v1_14_R1.CraftChunk
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.*
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import java.lang.Math.floorMod
import java.lang.reflect.Field
import java.lang.reflect.Modifier.FINAL

@NotThreadSafe
class CyclicListener(private val plugin: Cyclic) : Listener {

    @EventHandler(priority = LOWEST)
    fun onChunkLoad(event: ChunkLoadEvent) {
        pointChunk(event.chunk)
    }

    @EventHandler(priority = LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        player.teleport(player.location.representative)
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
        player.teleport(player.location.representative)
        plugin.manager.unloadAllChunks(player)
        plugin.manager.setLocation(player.uniqueId, null)
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        // TODO waterlogged
        sendBlockUpdate(block.x, block.y, block.z, Material.AIR.createBlockData())
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        sendBlockUpdate(block.x, block.y, block.z, block.blockData)
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockIgnite(event: BlockIgniteEvent) {
        val block = event.block
        sendBlockUpdate(block.x, block.y, block.z, Material.FIRE.createBlockData())
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block.x, block.y, block.z, Material.AIR.createBlockData())
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockFade(event: BlockFadeEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block.x, block.y, block.z, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block.x, block.y, block.z, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onLeavesDecay(event: LeavesDecayEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block.x, block.y, block.z, Material.AIR.createBlockData())
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockSpread(event: BlockSpreadEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block.x, block.y, block.z, event.source.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockForm(event: BlockFormEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block.x, block.y, block.z, event.newState.blockData)
        else
            event.isCancelled = true
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

    private val Block.isRepresentative
        get() = x in 0 until MAX_X && z in 0 until MAX_Z

    private fun pointChunk(chunk: Chunk) {
        val x = chunk.x
        val z = chunk.z
        if (x in 0 until X_CHUNKS && z in 0 until Z_CHUNKS) return
        val source = chunk.world.getChunkAt(floorMod(x, X_CHUNKS), floorMod(z, Z_CHUNKS))
        val sourceSections = (source as CraftChunk).handle.sections
        if ((chunk as CraftChunk).handle.sections === sourceSections) return

        // https://stackoverflow.com/a/3301720
        val field = net.minecraft.server.v1_14_R1.Chunk::class.java.getDeclaredField("sections")
        val accessible = field.isAccessible
        field.isAccessible = true
        try {
            val modifiersField = Field::class.java.getDeclaredField("modifiers")
            val modifiersAccessible = modifiersField.isAccessible
            modifiersField.isAccessible = true
            try {
                val modifiers = field.modifiers
                modifiersField.setInt(field, modifiers and FINAL.inv())
                try {
                    field.set((chunk as CraftChunk).handle, sourceSections)
                } finally {
                    modifiersField.setInt(field, modifiers)
                }
            } finally {
                modifiersField.isAccessible = modifiersAccessible
            }
        } finally {
            field.isAccessible = accessible
        }
    }

    private fun sendBlockUpdate(x: Int, y: Int, z: Int, data: BlockData) {
        val chunk = ChunkLocation(x shr 4, z shr 4)
        val protocol = ProtocolLibrary.getProtocolManager()
        val packet = protocol.createPacket(BLOCK_CHANGE)
        packet.blockData.write(0, WrappedBlockData.createData(data))
        for (player in plugin.server.onlinePlayers) {
            val chunks = plugin.manager.getLoadedChunks(player)
            for (otherChunk in chunks) {
                if (otherChunk == chunk || !otherChunk.equalsParallel(chunk)) continue
                val newX = x + ((otherChunk.x - chunk.x) shl 4)
                val newZ = z + ((otherChunk.z - chunk.z) shl 4)
                val newPosition = BlockPosition(newX, y, newZ)
                val newPacket = packet.deepClone()
                newPacket.blockPositionModifier.write(0, newPosition)
                protocol.sendServerPacket(player, newPacket)
            }
        }
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