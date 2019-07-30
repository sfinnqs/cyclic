package com.voichick.cyclic

import com.comphenix.protocol.PacketType.Play.Server.*
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.google.common.collect.MapMaker
import com.voichick.cyclic.TeleportFlag.*
import java.util.*

class CyclicAdapter(private val cyclic: Cyclic) : PacketAdapter(cyclic, MAP_CHUNK, UNLOAD_CHUNK, BLOCK_CHANGE, POSITION) {

    private val customPackets = Collections.newSetFromMap(MapMaker().weakKeys().makeMap<Any, Boolean>())

    override fun onPacketSending(event: PacketEvent) {
        val packet = event.packet
        if (packet.handle in customPackets) return
        val player = event.player ?: return
        when (event.packetType) {
            MAP_CHUNK -> {
                val ints = packet.integers
                val x = ints.read(0)
                val z = ints.read(1)
                cyclic.manager.loadChunk(player, ChunkLocation(x, z))
            }
            UNLOAD_CHUNK -> {
                val ints = packet.integers
                val x = ints.read(0)
                val z = ints.read(1)
                cyclic.manager.unloadChunk(player, ChunkLocation(x, z))
            }
            POSITION -> {
                val flags: Set<TeleportFlag> = packet.getSets(TeleportFlag.Converter).read(0)
                if (X in flags || Y in flags || Z in flags) return
                val doubles = packet.doubles
                val x = doubles.read(0)
                val y = doubles.read(1)
                val z = doubles.read(2)
                val floats = packet.float
                val yaw = floats.read(0)
                val pitch = floats.read(1)
                val location = ImmutableLocation(x, y, z, yaw, pitch, false)
                // TODO this is technically unsafe
                cyclic.manager.setLocation(player.uniqueId, location)
            }
        }
    }
}