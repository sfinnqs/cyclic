package com.voichick.cyclic

import com.comphenix.protocol.PacketType.Play.Server.*
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.BlockPosition
import com.google.common.collect.MapMaker
import java.lang.Math.floorMod
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class CyclicAdapter(private val cyclic: Cyclic) : PacketAdapter(cyclic, MAP_CHUNK, UNLOAD_CHUNK, BLOCK_CHANGE) {

    private val customPackets = Collections.newSetFromMap(MapMaker().weakKeys().makeMap<Any, Boolean>())

    override fun onPacketSending(event: PacketEvent?) {
        if (event == null) return
        val packet = event.packet ?: return
        if (packet.handle in customPackets) return
        val player = event.player ?: return
        when (event.packetType) {
            MAP_CHUNK -> {
                val ints = packet.integers
                val x = ints.read(0)
                val z = ints.read(1)
                cyclic.loadedChunks.getOrPut(player) {
                    ConcurrentHashMap.newKeySet()
                }.add(ChunkLocation(x, z))
            }
            UNLOAD_CHUNK -> {
                val ints = packet.integers
                val x = ints.read(0)
                val z = ints.read(1)
                cyclic.loadedChunks[player]?.remove(ChunkLocation(x, z))
            }
            BLOCK_CHANGE -> duplicateBlockChange(packet)
        }
    }

    private fun duplicateBlockChange(packet: PacketContainer) {
        // TODO overkill
        val position = packet.blockPositionModifier?.read(0) ?: return
        val x = position.x
        val z = position.z
        val chunkX = x shr 4
        val chunkZ = z shr 4
        val srcX = floorMod(chunkX, X_CHUNKS)
        val srcZ = floorMod(chunkZ, Z_CHUNKS)
        // TODO world-specific
        for (player in cyclic.server.onlinePlayers) {
            val chunks = cyclic.loadedChunks[player] ?: continue
            for (otherChunk in chunks) {
                if (otherChunk.x == chunkX && otherChunk.z == chunkZ) continue
                if (floorMod(otherChunk.x, X_CHUNKS) != srcX) continue
                if (floorMod(otherChunk.z, Z_CHUNKS) != srcZ) continue
                val newX = x + ((otherChunk.x - chunkX) shl 4)
                val newZ = z + ((otherChunk.z - chunkZ) shl 4)
                val newPosition = BlockPosition(newX, position.y, newZ)
                val protocol = ProtocolLibrary.getProtocolManager()
                val newPacket = packet.deepClone()
                packet.blockPositionModifier?.write(0, newPosition)
                customPackets.add(newPacket.handle)
                protocol.sendServerPacket(player, newPacket)
            }
        }
    }

}