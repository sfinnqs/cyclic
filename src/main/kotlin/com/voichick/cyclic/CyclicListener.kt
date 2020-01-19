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
package com.voichick.cyclic

import com.comphenix.protocol.PacketType.Play.Server.BLOCK_CHANGE
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.wrappers.BlockPosition
import com.comphenix.protocol.wrappers.WrappedBlockData
import com.voichick.cyclic.gen.CyclicGenerator
import com.voichick.cyclic.world.CyclicBlock
import com.voichick.cyclic.world.CyclicChunk
import com.voichick.cyclic.world.CyclicLocation
import com.voichick.cyclic.world.CyclicWorld
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
import java.lang.reflect.Field
import java.lang.reflect.Modifier.FINAL

@NotThreadSafe
class CyclicListener(private val plugin: Cyclic) : Listener {

    @EventHandler(priority = MONITOR)
    fun onPlayerLogin(event: PlayerLoginEvent) {
        plugin.manager.addPlayer(event.player)
    }

    @EventHandler(priority = LOWEST)
    fun onChunkLoad(event: ChunkLoadEvent) {
        val world = event.world
        val cyclicWorld = event.world.cyclicWorld ?: return
        val chunk = event.chunk
        val chunkLocation = CyclicChunk(cyclicWorld, chunk)
        if (chunkLocation.isRepresentative) {
            chunk.isForceLoaded = true
            return
        }
        chunk.isForceLoaded = false
        val sourceLocation = chunkLocation.representative
        val source = world.getChunkAt(sourceLocation.x, sourceLocation.z)
        val sourceSections = (source as CraftChunk).handle.sections
        if ((chunk as CraftChunk).handle.sections === sourceSections) return

        // https://stackoverflow.com/a/3301720
        val field = net.minecraft.server.v1_15_R1.Chunk::class.java.getDeclaredField("sections")
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

    @EventHandler(priority = HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val world = player.world.cyclicWorld ?: return
        plugin.manager.setLocation(player.uniqueId, CyclicLocation(world, player))
    }

    @EventHandler(priority = LOWEST, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val to = event.to
        val world = to?.world
        val cyclicWorld = world?.cyclicWorld
        val player = event.player
        val newTo = if (cyclicWorld == null) {
            null
        } else {
            val cyclicTo = CyclicLocation(cyclicWorld, to, player.isOnGround)
            val newTo = cyclicTo.representative
            event.setTo(newTo.toBukkitLocation(world))
            newTo
        }
        plugin.manager.setLocation(player.uniqueId, newTo)
    }

    @EventHandler(priority = HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val world = player.world
        val cyclicWorld = world.cyclicWorld ?: return
        player.teleport(getRepresentativeLocation(cyclicWorld, player))
        // TODO remove this call after fixing WorldManager
        plugin.manager.unloadAllChunks(player)
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
        event.isSaveChunk = CyclicChunk(world, chunk).isRepresentative
    }

    private fun getRepresentativeLocation(world: CyclicWorld, entity: Entity): Location {
        val bukkitWorld = entity.world
        assert(bukkitWorld.uid == world.id)
        val result = CyclicLocation(world, entity).representative
        return result.toBukkitLocation(bukkitWorld)
    }

    private fun sendBlockUpdate(block: CyclicBlock, data: BlockData) {
        val chunk = block.chunk
        val protocol = ProtocolLibrary.getProtocolManager()
        for (player in plugin.server.onlinePlayers) {
            val chunks = plugin.manager.getLoadedChunks(player)
            for (otherChunk in chunks) {
                if (otherChunk == chunk || !otherChunk.equalsParallel(chunk)) continue
                val packet = protocol.createPacket(BLOCK_CHANGE)
                packet.blockData.write(0, WrappedBlockData.createData(data))
                // TODO consider a ChunkOffset class for simplification
                val newX = block.x + ((otherChunk.x - chunk.x) shl 4)
                val newZ = block.z + ((otherChunk.z - chunk.z) shl 4)
                val newPosition = BlockPosition(newX, block.y, newZ)
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
