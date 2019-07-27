package com.voichick.cyclic

import com.comphenix.protocol.PacketType.Play.Server.*
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import net.jcip.annotations.ThreadSafe
import org.bukkit.entity.Player
import java.util.*
import java.util.Collections.newSetFromMap
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
    private val canSee = WeakHashMap<Player, MutableMap<UUID, MutableSet<Duplicate>>>()
    private val loadedChunks = WeakHashMap<Player, MutableSet<ChunkLocation>>()

    fun loadChunk(viewer: Player, chunk: ChunkLocation) {
        // TODO remove lots of overlapping code with unloadChunk
        val packets = mutableListOf<PacketToSend>()
        lock.write {
            val added = loadedChunks.getOrPut(viewer, ::mutableSetOf).add(chunk)
            if (!added) return
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
            val removed = loadedChunks[viewer]?.remove(chunk) ?: false
            if (!removed) return
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
                val playerDups = mutableSetOf<Duplicate>()//.apply { addAll(dupMap.getOrDefault(uuid, emptySet<Duplicate>())) }
                playerDups.addAll(canSee.getOrDefault(viewer, emptyMap<UUID, Set<Duplicate>>()).getOrDefault(uuid, emptySet<Duplicate>()))
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
                            val oldDupX = oldLoc!!.x + offsetX * MAX_X
                            val oldDupZ = oldLoc.z + offsetZ * MAX_Z
                            // TODO function to translate ImmutableLocation
                            val oldDupLoc = ImmutableLocation(oldDupX, oldLoc.y, oldDupZ, oldLoc.yaw, oldLoc.pitch, oldLoc.grounded)
                            if (playerDups.remove(duplicate))
                                packets.addAll(updateDuplicate(duplicate, newLocation, oldDupLoc).map { PacketToSend(viewer, it) })
                            else
                                packets.add(PacketToSend(viewer, spawnDuplicate(duplicate, viewer, newLocation)))
                        }

                    }
                }
                // TODO despawn multiple duplicates in one packet
                packets.addAll(playerDups.map { PacketToSend(viewer, despawnDuplicate(it, viewer)) })
            }
        }
        for (packet in packets)
            ProtocolLibrary.getProtocolManager().sendServerPacket(packet.viewer, packet.packet)
    }

    private fun spawnDuplicate(duplicate: Duplicate, viewer: Player, location: ImmutableLocation): PacketContainer {
        // TODO maybe combine the three methods somehow?
        val uuid = duplicate.uuid
        seenBy.getOrPut(duplicate) { newSetFromMap(WeakHashMap<Player, Boolean>()) }.add(viewer)
        canSee.getOrPut(viewer, ::mutableMapOf).getOrPut(uuid, ::mutableSetOf).add(duplicate)
        val protocol = ProtocolLibrary.getProtocolManager()
        val packet = protocol.createPacket(NAMED_ENTITY_SPAWN)
        packet.integers.write(0, getOrCreateEntityId(duplicate))
        packet.uuiDs.write(0, uuid)
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

    private fun updateDuplicate(duplicate: Duplicate, location: ImmutableLocation, oldLocation: ImmutableLocation): Set<PacketContainer> {
        // TODO velocity
        val protocol = ProtocolLibrary.getProtocolManager()
        val offset = location - oldLocation
        val zeroShort: Short = 0
        val result = mutableSetOf<PacketContainer>()
        if (location.yaw != oldLocation.yaw) {
            val packet = protocol.createPacket(ENTITY_HEAD_ROTATION)
            packet.integers.write(0, getOrCreateEntityId(duplicate))
            packet.bytes.write(0, location.yaw)
            result.add(packet)
        }
        if (offset == null) {
            val packet = protocol.createPacket(ENTITY_TELEPORT)
            packet.integers.write(0, getOrCreateEntityId(duplicate))
            val doubles = packet.doubles
            doubles.write(0, location.x)
            doubles.write(1, location.y)
            doubles.write(2, location.z)
            val bytes = packet.bytes
            bytes.write(0, location.yaw)
            bytes.write(1, location.pitch)
            packet.booleans.write(0, location.grounded)
            result.add(packet)
        } else if (offset.deltaX == zeroShort && offset.deltaY == zeroShort && offset.deltaZ == zeroShort) {
            if (location.pitch != oldLocation.pitch) {
                val packet = protocol.createPacket(ENTITY_LOOK)
                packet.integers.write(0, getOrCreateEntityId(duplicate))
                val bytes = packet.bytes
                bytes.write(0, location.yaw)
                bytes.write(1, location.pitch)
                packet.booleans.write(0, location.grounded)
                result.add(packet)
            }
        } else {
            if (location.yaw == oldLocation.yaw && location.pitch == oldLocation.pitch) {
                val packet = protocol.createPacket(REL_ENTITY_MOVE)
                packet.integers.write(0, getOrCreateEntityId(duplicate))
                val shorts = packet.shorts
                shorts.write(0, offset.deltaX)
                shorts.write(1, offset.deltaY)
                shorts.write(2, offset.deltaZ)
                packet.booleans.write(0, location.grounded)
                result.add(packet)
            } else {
                val packet = protocol.createPacket(REL_ENTITY_MOVE_LOOK)
                packet.integers.write(0, getOrCreateEntityId(duplicate))
                val shorts = packet.shorts
                shorts.write(0, offset.deltaX)
                shorts.write(1, offset.deltaY)
                shorts.write(2, offset.deltaZ)
                val bytes = packet.bytes
                bytes.write(0, location.yaw)
                bytes.write(1, location.pitch)
                packet.booleans.write(0, location.grounded)
                result.add(packet)
            }
        }
        return result
    }

    private fun despawnDuplicate(duplicate: Duplicate, viewer: Player): PacketContainer {
        val protocol = ProtocolLibrary.getProtocolManager()
        val packet = protocol.createPacket(ENTITY_DESTROY)
        val entityId = entityIds[duplicate]!!
        packet.integerArrays?.write(0, intArrayOf(entityId))
        val viewers = seenBy[duplicate]!!
        val wasSeen = viewers.remove(viewer)
        assert(wasSeen)
        val uuid = duplicate.uuid
        // TODO maybe remove key
        val removedFromCanSee = canSee[viewer]!![uuid]!!.remove(duplicate)
        assert(removedFromCanSee)
        if (viewers.isEmpty()) {
            entityIds.remove(duplicate)
            dupList[Int.MAX_VALUE - entityId] = null
            val wasInMap = dupMap[uuid]!!.remove(duplicate)
            assert(wasInMap)
            val removedFromSeenBy = seenBy.remove(duplicate)
            assert(removedFromSeenBy != null)
        }
        return packet
    }

    private fun getOrCreateEntityId(duplicate: Duplicate): Int {
        val existingId = entityIds[duplicate]
        if (existingId != null) return existingId
        for (i in 0..dupList.size) {
            val storedDuplicate = dupList.getOrNull(i)
            if (storedDuplicate == null) {
                if (i == dupList.size)
                    dupList.add(duplicate)
                else
                    dupList[i] = duplicate
                val entityId = Int.MAX_VALUE - i
                entityIds[duplicate] = entityId
                dupMap.getOrPut(duplicate.uuid, ::mutableSetOf).add(duplicate)
                return entityId
            }
        }
        // TODO more elegant way?
        throw AssertionError()
    }

    private data class PacketToSend(val viewer: Player, val packet: PacketContainer)

}