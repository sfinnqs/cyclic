package com.voichick.cyclic

import com.comphenix.protocol.PacketType.Play.Server.BLOCK_CHANGE
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.wrappers.BlockPosition
import com.comphenix.protocol.wrappers.WrappedBlockData
import net.jcip.annotations.NotThreadSafe
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Material.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace.DOWN
import org.bukkit.block.data.AnaloguePowerable
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Levelled
import org.bukkit.block.data.Waterlogged
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
        val chunk = event.chunk
        val x = chunk.x
        val z = chunk.z
        if (chunk.location.isRepresentative) {
            chunk.isForceLoaded = true
            return
        }
        chunk.isForceLoaded = false
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
        val type = if ((block.blockData as? Waterlogged)?.isWaterlogged == true)
            WATER
        else
            AIR
        sendBlockUpdate(block, type.createBlockData())
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        sendBlockUpdate(block, block.blockData)
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockFromTo(event: BlockFromToEvent) {
        val to = event.toBlock
        val from = event.block
        val fromData = from.blockData
        if (fromData is Levelled) {
            val fromLevel = fromData.level
            val toData = fromData.clone() as Levelled
            toData.level = when {
                event.face == DOWN -> fromLevel % 8 + 8
                fromLevel < 8 -> fromLevel + 1
                else -> 1
            }
            sendBlockUpdate(to, toData)
        } else if (fromData.material == DRAGON_EGG) {
            sendBlockUpdate(from, AIR.createBlockData())
            sendBlockUpdate(to, fromData)
        }
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onFluidLevelChange(event: FluidLevelChangeEvent) {
        val block = event.block
        sendBlockUpdate(block, event.newData)
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockRedstone(event: BlockRedstoneEvent) {
        val block = event.block
        val data = block.blockData.clone() as? AnaloguePowerable ?: return
        data.power = event.newCurrent
        sendBlockUpdate(block, data)
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block, Material.AIR.createBlockData())
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockFade(event: BlockFadeEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onLeavesDecay(event: LeavesDecayEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block, Material.AIR.createBlockData())
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockSpread(event: BlockSpreadEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockForm(event: BlockFormEvent) {
        val block = event.block
        if (block.isRepresentative)
            sendBlockUpdate(block, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        event.isSaveChunk = chunk.location.isRepresentative
    }

    private val Location.representative: Location
        get() {
            val offsetX = blockX - floorMod(blockX, MAX_X)
            val offsetZ = blockZ - floorMod(blockZ, MAX_Z)
            return subtract(offsetX.toDouble(), 0.0, offsetZ.toDouble())
        }

    private fun sendBlockUpdate(block: Block, data: BlockData) {
        val x = block.x
        val z = block.z
        val chunk = ChunkLocation(x shr 4, z shr 4)
        val protocol = ProtocolLibrary.getProtocolManager()
        for (player in plugin.server.onlinePlayers) {
            val chunks = plugin.manager.getLoadedChunks(player)
            for (otherChunk in chunks) {
                if (otherChunk == chunk || !otherChunk.equalsParallel(chunk)) continue
                val packet = protocol.createPacket(BLOCK_CHANGE)
                packet.blockData.write(0, WrappedBlockData.createData(data))
                val newX = x + ((otherChunk.x - chunk.x) shl 4)
                val newZ = z + ((otherChunk.z - chunk.z) shl 4)
                val newPosition = BlockPosition(newX, block.y, newZ)
                packet.blockPositionModifier.write(0, newPosition)
                protocol.sendServerPacket(player, packet)
            }
        }
    }
}