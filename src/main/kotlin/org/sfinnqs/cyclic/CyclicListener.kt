/**
 * Cyclic - A Bukkit plugin for worlds that wrap around
 * Copyright (C) 2020 sfinnqs
 *
 * This file is part of Cyclic.
 *
 * Cyclic is free software; you can redistribute it and/or modify it under the
 * terms of version 3 of the GNU Affero General Public License as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see <https://www.gnu.org/licenses>.
 *
 * Additional permission under GNU AGPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with the "Minecraft: Java Edition" server (or a modified version of the
 * "Minecraft: Java Edition" server), containing parts covered by the terms of
 * the Minecraft End-User Licence Agreement, the licensor of Cyclic grants you
 * additional permission to support user interaction over a network with the
 * resulting work. In this case, you are still required to provide access to the
 * Corresponding Source of your version (under GNU AGPL version 3 section 13)
 * but you may omit source code from the "Minecraft: Java Edition" server from
 * the available Corresponding Source.
 */
package org.sfinnqs.cyclic

import com.comphenix.protocol.PacketType.Play.Server.BLOCK_CHANGE
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.wrappers.WrappedBlockData
import net.jcip.annotations.NotThreadSafe
import org.bukkit.Location
import org.bukkit.Material.*
import org.bukkit.World
import org.bukkit.block.BlockFace.DOWN
import org.bukkit.block.data.AnaloguePowerable
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Levelled
import org.bukkit.block.data.Waterlogged
import org.bukkit.craftbukkit.v1_15_R1.CraftChunk
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.*
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.player.*
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.sfinnqs.cyclic.gen.CyclicGenerator
import org.sfinnqs.cyclic.world.*
import java.lang.reflect.Field
import java.lang.reflect.Modifier.FINAL

@NotThreadSafe
class CyclicListener(private val plugin: Cyclic) : Listener {

    @EventHandler(priority = MONITOR)
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val player = event.player
        plugin.manager.addPlayerId(player, player.uniqueId)
    }

    @EventHandler(priority = LOWEST)
    fun onChunkLoad(event: ChunkLoadEvent) {
        val world = event.world
        val cyclicWorld = event.world.cyclicWorld ?: return
        val chunk = event.chunk
        val chunkLocation = CyclicChunk(cyclicWorld, ChunkCoords(chunk))
        if (chunkLocation.isRepresentative) {
            chunk.isForceLoaded = true
            return
        }
        chunk.isForceLoaded = false
        val sourceCoords = chunkLocation.representative.coords
        val source = world.getChunkAt(sourceCoords.x, sourceCoords.z)
        val sourceSections = (source as CraftChunk).handle.sections
        if ((chunk as CraftChunk).handle.sections === sourceSections) return

        // https://stackoverflow.com/a/3301720
        val chunkClass = net.minecraft.server.v1_15_R1.Chunk::class.java
        val field = chunkClass.getDeclaredField("sections")
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
        val world = player.world.cyclicWorld ?: return
        player.teleport(getRepresentativeLocation(world, player))
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val to = event.to ?: return
        val world = to.world?.cyclicWorld ?: return
        val player = event.player
        val location = CyclicLocation(world, to, player.isOnGround)
        plugin.manager.setLocation(player.uniqueId, location)
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val to = event.to ?: return
        val world = to.world?.cyclicWorld ?: return
        val player = event.player
        val location = CyclicLocation(world, to, player.isOnGround)
        plugin.manager.setLocation(player.uniqueId, location)
    }

    @EventHandler(priority = HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val world = player.world
        val cyclicWorld = world.cyclicWorld ?: return
        player.teleport(getRepresentativeLocation(cyclicWorld, player))
        plugin.manager.setLocation(player.uniqueId, null)
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val world = block.world.cyclicWorld ?: return
        val type = if ((block.blockData as? Waterlogged)?.isWaterlogged == true)
            WATER
        else
            AIR
        sendBlockUpdate(CyclicBlock(world, block), type.createBlockData())
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        val world = block.world.cyclicWorld ?: return
        sendBlockUpdate(CyclicBlock(world, block), block.blockData)
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockFromTo(event: BlockFromToEvent) {
        val to = event.toBlock
        val from = event.block
        val bukkitWorld = to.world
        assert(bukkitWorld == from.world)
        val world = bukkitWorld.cyclicWorld ?: return
        val cyclicTo = CyclicBlock(world, to)
        val fromData = from.blockData
        if (fromData is Levelled) {
            val fromLevel = fromData.level
            val toData = fromData.clone() as Levelled
            toData.level = when {
                event.face == DOWN -> fromLevel % 8 + 8
                fromLevel < 8 -> fromLevel + 1
                else -> 1
            }
            sendBlockUpdate(cyclicTo, toData)
        } else if (fromData.material == DRAGON_EGG) {
            sendBlockUpdate(CyclicBlock(world, from), AIR.createBlockData())
            sendBlockUpdate(cyclicTo, fromData)
        }
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onFluidLevelChange(event: FluidLevelChangeEvent) {
        val block = event.block
        val world = block.world.cyclicWorld ?: return
        sendBlockUpdate(CyclicBlock(world, block), event.newData)
    }

    @EventHandler(priority = MONITOR, ignoreCancelled = true)
    fun onBlockRedstone(event: BlockRedstoneEvent) {
        val block = event.block
        val world = block.world.cyclicWorld ?: return
        val data = block.blockData.clone() as? AnaloguePowerable ?: return
        data.power = event.newCurrent
        sendBlockUpdate(CyclicBlock(world, block), data)
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        val block = event.block
        val world = block.world.cyclicWorld ?: return
        val cyclicBlock = CyclicBlock(world, block)
        if (cyclicBlock.isRepresentative)
            sendBlockUpdate(cyclicBlock, AIR.createBlockData())
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockFade(event: BlockFadeEvent) {
        val block = event.block
        val world = block.world.cyclicWorld ?: return
        val cyclicBlock = CyclicBlock(world, block)
        if (cyclicBlock.isRepresentative)
            sendBlockUpdate(cyclicBlock, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        val block = event.block
        val world = block.world.cyclicWorld ?: return
        val cyclicBlock = CyclicBlock(world, block)
        if (cyclicBlock.isRepresentative)
            sendBlockUpdate(cyclicBlock, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onLeavesDecay(event: LeavesDecayEvent) {
        val bukkitBlock = event.block
        val world = bukkitBlock.world.cyclicWorld ?: return
        val block = CyclicBlock(world, bukkitBlock)
        if (block.isRepresentative)
            sendBlockUpdate(block, AIR.createBlockData())
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockSpread(event: BlockSpreadEvent) {
        val bukkitBlock = event.block
        val world = bukkitBlock.world.cyclicWorld ?: return
        val block = CyclicBlock(world, bukkitBlock)
        if (block.isRepresentative)
            sendBlockUpdate(block, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onBlockForm(event: BlockFormEvent) {
        val bukkitBlock = event.block
        val world = bukkitBlock.world.cyclicWorld ?: return
        val block = CyclicBlock(world, bukkitBlock)
        if (block.isRepresentative)
            sendBlockUpdate(block, event.newState.blockData)
        else
            event.isCancelled = true
    }

    @EventHandler(priority = HIGHEST)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        val world = event.world.cyclicWorld ?: return
        val cyclicChunk = CyclicChunk(world, ChunkCoords(chunk))
        event.isSaveChunk = cyclicChunk.isRepresentative
    }

    private fun getRepresentativeLocation(
        world: CyclicWorld,
        entity: Entity
    ): Location {
        val bukkitWorld = entity.world
        assert(bukkitWorld.uid == world.id)
        val result = CyclicLocation(world, entity).representative
        return result.toBukkitLocation(bukkitWorld)
    }

    private fun sendBlockUpdate(block: CyclicBlock, data: BlockData) {
        val blockChunk = block.chunk
        val representative = blockChunk.representative
        val protocol = ProtocolLibrary.getProtocolManager()
        for (player in plugin.server.onlinePlayers) {
            val chunks = plugin.manager.getLoadedChunks(player, representative)
            for (otherChunk in chunks) {
                if (otherChunk == blockChunk) continue
                val packet = protocol.createPacket(BLOCK_CHANGE)
                packet.blockData.write(0, WrappedBlockData.createData(data))
                val newBlock = block + (otherChunk - blockChunk)
                val newPosition = newBlock.toBlockPosition()
                packet.blockPositionModifier.write(0, newPosition)
                protocol.sendServerPacket(player, packet)
            }
        }
    }

    private val World.cyclicWorld
        get() = (generator as? CyclicGenerator)?.let {
            CyclicWorld(uid, name, it.config)
        }

}
