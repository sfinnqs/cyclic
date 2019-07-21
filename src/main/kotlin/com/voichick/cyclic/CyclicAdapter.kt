package com.voichick.cyclic

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.PacketType.Play.Client.*
import com.comphenix.protocol.PacketType.Play.Server.*
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.BlockPosition
import com.google.common.collect.MapMaker
import com.voichick.cyclic.TeleportFlag.X
import com.voichick.cyclic.TeleportFlag.Z
import org.bukkit.entity.Player
import java.lang.Math.floorMod
import java.util.*

class CyclicAdapter(private val cyclic: Cyclic) : PacketAdapter(cyclic, MAP_CHUNK, UNLOAD_CHUNK, PacketType.Play.Server.POSITION, BLOCK_CHANGE, PacketType.Play.Client.POSITION, POSITION_LOOK, SETTINGS, BLOCK_PLACE) {

    //    private val privateClientLocs = MapMaker().weakKeys().makeMap<Player, ClientLocation>()
//    private val viewDistances = MapMaker().weakKeys().makeMap<Player, Int>().withDefault { view }
    //    private val playerChunks = mutableMapOf<Player, ClientLocation>()
//    private val loadedChunks = mutableMapOf<UUID, QueueSet<Pair<Int, Int>>>()
//    val clientLocs: Map<Player, ClientLocation>
//        get() = privateClientLocs
    private val modifiedPackets = Collections.newSetFromMap(MapMaker().weakKeys().makeMap<Any, Boolean>())

    fun allowPacket(packet: PacketContainer) {
        modifiedPackets.add(packet.handle)
    }

    @Suppress("RedundantLambdaArrow")
    override fun onPacketSending(event: PacketEvent?) {
        if (event == null) return
        val packet = event.packet ?: return
        if (packet.handle in modifiedPackets) return
        val player = event.player ?: return
        when (event.packetType) {
            MAP_CHUNK, UNLOAD_CHUNK -> event.isCancelled = true
            PacketType.Play.Server.POSITION -> translateServerPosition(packet, player)
            BLOCK_CHANGE -> {
                event.isCancelled = true
                duplicateBlockChange(packet, player)
            }
        }
    }

    private fun translateServerPosition(packet: PacketContainer, player: Player) {
        val flags = packet.getSets(TeleportFlag.Converter)?.read(0) ?: return
        val doubles = packet.doubles ?: return
        val locations = cyclic.manager.locations
        val clientLoc = locations[player] ?: return
        val x = doubles.read(0) ?: return
        val newX = if (flags.contains(X)) {
            // x is relative
            val teleportedX = clientLoc.clientX + x
            val offsetX = offsetX(teleportedX)
            if (offsetX != 0) {
                doubles.write(0, x - offsetX)
            }
            teleportedX - offsetX
        } else {
            // x is absolute
            x
        }
        val z = doubles.read(2) ?: return
        val newZ = if (flags.contains(Z)) {
            // z is relative
            val teleportedZ = clientLoc.clientZ + z
            val offsetZ = offsetZ(teleportedZ)
            if (offsetZ != 0) {
                doubles.write(2, z - offsetZ)
            }
            teleportedZ - offsetZ
        } else {
            // z is absolute
            z
        }
        locations[player] = ClientLocation(newX, newZ)
//        if (flags.contains(Z)) {
//            val offsetZ = clientLoc.offsetZ
//            if (offsetZ != 0) {
//                val z = doubles.read(2) ?: return
//                doubles.write(2, z - offsetZ)
//            }
//        }
//        if (flags.remove(X)) {
//            // x is relative
//            cyclic.logger.info {
//                "Converting relative x: $x to absolute ${clientLoc.clientX + x}"
//            }
//            doubles.write(0, clientLoc.clientX + x)
//        } else {
//            // x is absolute
//            if (offsetX != 0) {
//                cyclic.logger.info {
//                    "Converting absolute x: $x to ${x + offsetX}"
//                }
//                doubles.write(0, x + offsetX)
//            }
//        }
//        val z = doubles.read(2) ?: return
//        if (flags.remove(Z)) {
//            // z is relative
//            cyclic.logger.info {
//                "Converting relative z: $z to absolute ${clientLoc.clientZ + z}"
//            }
//            doubles.write(2, clientLoc.clientZ + z)
//        } else {
//            // z is absolute
//            val offsetZ = clientLoc.offsetZ
//            if (offsetZ != 0) {
//                cyclic.logger.info {
//                    "Converting absolute z: $z to ${z + offsetZ}"
//                }
//                doubles.write(2, z + offsetZ)
//            }
//        }
    }

    private fun duplicateBlockChange(packet: PacketContainer, player: Player) {
        val position = packet.blockPositionModifier?.read(0) ?: return
//        cyclic.logger.info {
//            "position: $position"
//        }
        val x = position.x
        val z = position.z
        val serverChunkX = floorMod(x shr 4, X_CHUNKS)
        val serverChunkZ = floorMod(z shr 4, Z_CHUNKS)
//        cyclic.logger.info {
//            "server chunk x/z: $serverChunkX / $serverChunkZ"
//        }
        val manager = cyclic.manager
        val chunks = manager.managers[player] ?: return
        val location = manager.locations[player] ?: return
        for (neighbor in location.getNeighbors(manager.views[player])) {
//            cyclic.logger.info {
//                "neighbor: $neighbor"
//            }
            if (floorMod(neighbor.x, X_CHUNKS) != serverChunkX || floorMod(neighbor.z, Z_CHUNKS) != serverChunkZ)
                continue
//            cyclic.logger.info {
//                "passed mod test"
//            }
            if (!chunks.isLoading(neighbor)) continue
//            cyclic.logger.info {
//                "passed loading test"
//            }
            val translatedX = x + ((neighbor.x - serverChunkX) shl 4)
            val translatedZ = z + ((neighbor.z - serverChunkZ) shl 4)
//            cyclic.logger.info {
//                "translated: $translatedX / $translatedZ"
//            }
            val newPacket = packet.deepClone() ?: continue
            val newPosition = BlockPosition(translatedX, position.y, translatedZ)
            newPacket.blockPositionModifier?.write(0, newPosition)
            allowPacket(newPacket)
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, newPacket)
        }
    }

    @Suppress("RedundantLambdaArrow")
    override fun onPacketReceiving(event: PacketEvent?) {
        if (event == null) return
        val packet = event.packet ?: return
        if (packet.handle in modifiedPackets) return
        val player = event.player ?: return
        when (event.packetType) {
            SETTINGS -> readViewDistance(packet, player)
            PacketType.Play.Client.POSITION, POSITION_LOOK -> {
                translateClientLocation(packet, player)
            }
            BLOCK_PLACE -> translateBlockPlace(packet)
        }
    }

    private fun readViewDistance(packet: PacketContainer, player: Player) {
        assert(packet.type == SETTINGS)
        val viewDistance = packet.integers?.read(0) ?: return
        cyclic.manager.views[player] = viewDistance
    }

    private fun translateClientLocation(packet: PacketContainer, player: Player) {
        assert(packet.type == PacketType.Play.Client.POSITION || packet.type == POSITION_LOOK)
        val doubles = packet.doubles ?: return
        val x = doubles.read(0) ?: return
        val z = doubles.read(2) ?: return
        val clientLoc = ClientLocation(x, z)
        val serverX = clientLoc.serverX
        if (serverX != x)
            doubles.write(0, serverX)
        val serverZ = clientLoc.serverZ
        if (serverZ != z)
            doubles.write(2, serverZ)

        cyclic.manager.locations[player] = clientLoc
    }

    private fun translateBlockPlace(packet: PacketContainer) {
        assert(packet.type == BLOCK_PLACE)
        val locations = packet.blockPositionModifier ?: return
        val location = locations.read(0) ?: return
        val newX = floorMod(location.x, MAX_X)
        val newZ = floorMod(location.z, MAX_Z)
        locations.write(0, BlockPosition(newX, location.y, newZ))
    }

}