package com.voichick.cyclic

import com.comphenix.protocol.PacketType.Play.Server.*
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import net.jcip.annotations.ThreadSafe
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

// TODO this is a mess
@ThreadSafe
class VisibilityManager {
    private val lock = ReentrantReadWriteLock()
    private val playerLocs = mutableMapOf<UUID, ImmutableLocation>()
    private val dupList = mutableListOf<Duplicate?>()
    private val entityIds = mutableMapOf<Duplicate, Int>()
    private val dupMap = mutableMapOf<UUID, MutableSet<Duplicate>>()
    private val seenBy = mutableMapOf<Duplicate, MutableSet<Player>>()
    private val loadedChunks = WeakHashMap<Player, MutableSet<ChunkLocation>>()

    fun loadChunk(viewer: Player, chunk: ChunkLocation) {
        // TODO remove lots of overlapping code with unloadChunk
        val packets = mutableListOf<PacketToSend>()
        lock.write {
            val added = loadedChunks.getOrPut(viewer, ::mutableSetOf).add(chunk)
            assert(added)
            for ((uuid, location) in playerLocs) {
                val playerChunk = location.chunk
                if (playerChunk == chunk || !playerChunk.equalsParallel(chunk)) continue
                val offsetX = (chunk.x - playerChunk.x) / X_CHUNKS
                val offsetZ = (chunk.z - playerChunk.z) / Z_CHUNKS
                val duplicate = Duplicate(uuid, offsetX, offsetZ)
                val newX = location.x + offsetX * MAX_X
                val newZ = location.z + offsetZ * MAX_Z
                val newLocation = ImmutableLocation(newX, location.y, newZ, location.yaw, location.pitch, location.grounded)
                packets.add(PacketToSend(viewer, spawnDuplicate(duplicate, viewer, newLocation)))
            }
        }
        for (packet in packets)
            ProtocolLibrary.getProtocolManager().sendServerPacket(packet.viewer, packet.packet)
    }

    fun unloadChunk(viewer: Player, chunk: ChunkLocation) {
        val packets = mutableListOf<PacketToSend>()
        lock.write {
            val removed = loadedChunks[viewer]!!.remove(chunk)
            assert(removed)
            for ((uuid, location) in playerLocs) {
                val playerChunk = location.chunk
                if (playerChunk == chunk || !playerChunk.equalsParallel(chunk)) continue
                val offsetX = (chunk.x - playerChunk.x) / X_CHUNKS
                val offsetZ = (chunk.z - playerChunk.z) / Z_CHUNKS
                val duplicate = Duplicate(uuid, offsetX, offsetZ)
                packets.add(PacketToSend(viewer, despawnDuplicate(duplicate, viewer)))
            }
        }
        for (packet in packets)
            ProtocolLibrary.getProtocolManager().sendServerPacket(packet.viewer, packet.packet)
    }

    fun unloadAllChunks(viewer: Player) {
        // TODO a cleaner solution
        val chunks = loadedChunks[viewer] ?: return
        for (chunk in chunks.toList())
            unloadChunk(viewer, chunk)
    }

    fun getLoadedChunks(viewer: Player): Set<ChunkLocation> {
        return loadedChunks[viewer] ?: emptySet()
    }

    fun setLocation(uuid: UUID, location: ImmutableLocation?) {
        val packets = mutableListOf<PacketToSend>()
        lock.write {
            val oldLoc = if (location == null)
                playerLocs.remove(uuid)
            else
                playerLocs.put(uuid, location)
            // TODO concurrent modification
            for ((viewer, chunks) in loadedChunks) {
                val playerDups = dupMap.getOrDefault(uuid, mutableSetOf())
                if (location != null) {
                    val chunk = location.chunk
                    for (loadedChunk in chunks) {
                        if (loadedChunk != chunk && loadedChunk.equalsParallel(chunk)) {
                            val offsetX = (loadedChunk.x - chunk.x) / X_CHUNKS
                            val offsetZ = (loadedChunk.z - chunk.z) / Z_CHUNKS
                            val duplicate = Duplicate(uuid, offsetX, offsetZ)
                            val newX = location.x + offsetX * MAX_X
                            val newZ = location.z + offsetZ * MAX_Z
                            val newLocation = ImmutableLocation(newX, location.y, newZ, location.yaw, location.pitch, location.grounded)
                            val packet = if (playerDups.remove(duplicate))
                                updateDuplicate(duplicate, newLocation, oldLoc!!)
                            else
                                spawnDuplicate(duplicate, viewer, newLocation)
                            packets.add(PacketToSend(viewer, packet))
                        }

                    }
                }
                packets.addAll(playerDups.map { PacketToSend(viewer, despawnDuplicate(it, viewer)) })
            }
        }
        for (packet in packets)
            ProtocolLibrary.getProtocolManager().sendServerPacket(packet.viewer, packet.packet)
    }

    private fun spawnDuplicate(duplicate: Duplicate, viewer: Player, location: ImmutableLocation): PacketContainer {
        // TODO maybe combine the three methods somehow?
        seenBy.getOrPut(duplicate, ::mutableSetOf).add(viewer)
        val protocol = ProtocolLibrary.getProtocolManager()
        val packet = protocol.createPacket(NAMED_ENTITY_SPAWN)
        packet.integers.write(0, getEntityId(duplicate))
        packet.uuiDs.write(0, duplicate.uuid)
        val doubles = packet.doubles
        doubles.write(0, location.x)
        doubles.write(1, location.y)
        doubles.write(2, location.z)
        val bytes = packet.bytes
        bytes.write(0, location.yaw)
        bytes.write(1, location.pitch)
        // TODO metadata
        return packet
    }

    private fun updateDuplicate(duplicate: Duplicate, location: ImmutableLocation, oldLocation: ImmutableLocation): PacketContainer {
        // TODO velocity
        val protocol = ProtocolLibrary.getProtocolManager()
        val offset = location - oldLocation
        val zeroShort: Short = 0
        return if (offset == null) {
            val packet = protocol.createPacket(ENTITY_TELEPORT)
            packet.integers.write(0, getEntityId(duplicate))
            val doubles = packet.doubles
            doubles.write(0, location.x)
            doubles.write(1, location.y)
            doubles.write(2, location.z)
            val bytes = packet.bytes
            bytes.write(0, location.yaw)
            bytes.write(1, location.pitch)
            packet.booleans.write(0, location.grounded)
            packet
        } else if (offset.deltaX == zeroShort && offset.deltaY == zeroShort && offset.deltaZ == zeroShort) {
            if (location.yaw == oldLocation.yaw) {
                if (location.pitch == oldLocation.pitch) {
                    val packet = protocol.createPacket(ENTITY)
                    packet.integers.write(0, getEntityId(duplicate))
                    packet
                } else {
                    val packet = protocol.createPacket(ENTITY_HEAD_ROTATION)
                    packet.integers.write(0, getEntityId(duplicate))
                    packet.bytes.write(0, location.yaw)
                    packet
                }
            } else {
                val packet = protocol.createPacket(ENTITY_LOOK)
                packet.integers.write(0, getEntityId(duplicate))
                val bytes = packet.bytes
                bytes.write(0, location.yaw)
                bytes.write(1, location.pitch)
                packet.booleans.write(0, location.grounded)
                packet
            }
        } else {
            if (location.yaw == oldLocation.yaw && location.pitch == oldLocation.pitch) {
                val packet = protocol.createPacket(REL_ENTITY_MOVE)
                packet.integers.write(0, getEntityId(duplicate))
                val shorts = packet.shorts
                shorts.write(0, offset.deltaX)
                shorts.write(1, offset.deltaY)
                shorts.write(2, offset.deltaZ)
                packet.booleans.write(0, location.grounded)
                packet
            } else {
                val packet = protocol.createPacket(REL_ENTITY_MOVE_LOOK)
                packet.integers.write(0, getEntityId(duplicate))
                val shorts = packet.shorts
                shorts.write(0, offset.deltaX)
                shorts.write(1, offset.deltaY)
                shorts.write(2, offset.deltaZ)
                val bytes = packet.bytes
                bytes.write(0, location.yaw)
                bytes.write(1, location.pitch)
                packet.booleans.write(0, location.grounded)
                packet
            }
        }
    }

    private fun despawnDuplicate(duplicate: Duplicate, viewer: Player): PacketContainer {
        val protocol = ProtocolLibrary.getProtocolManager()
        val packet = protocol.createPacket(ENTITY_DESTROY)
        packet.integerArrays?.write(0, intArrayOf(getEntityId(duplicate)))
        val viewers = seenBy[duplicate]!!
        val wasSeen = viewers.remove(viewer)
        assert(wasSeen)
        if (viewers.isEmpty()) {
            val entityId = entityIds[duplicate]!!
            dupList[entityId] = null
            val wasInMap = dupMap[duplicate.uuid]!!.remove(duplicate)
            assert(wasInMap)
            seenBy.remove(duplicate)
        }
        return packet
    }

    private fun getEntityId(duplicate: Duplicate): Int {
        val existingId = entityIds[duplicate]
        if (existingId != null) return Int.MAX_VALUE - existingId
        for (possibleId in 0..dupList.size) {
            val storedDuplicate = dupList.getOrNull(possibleId)
            if (storedDuplicate == null) {
                if (possibleId == dupList.size)
                    dupList.add(duplicate)
                else
                    dupList[possibleId] = duplicate
                entityIds[duplicate] = possibleId
                dupMap.getOrPut(duplicate.uuid, ::mutableSetOf).add(duplicate)
                return Int.MAX_VALUE - possibleId
            }
        }
        // TODO more elegant way?
        throw AssertionError()
    }

    private data class PacketToSend(val viewer: Player, val packet: PacketContainer)

}