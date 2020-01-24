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
package org.sfinnqs.cyclic.manager

import com.comphenix.protocol.PacketType.Play.Server.*
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.google.common.collect.HashMultimap
import com.google.common.collect.SetMultimap
import net.jcip.annotations.ThreadSafe
import org.bukkit.entity.Player
import org.sfinnqs.cyclic.FakeEntity
import org.sfinnqs.cyclic.collect.WeakMap
import org.sfinnqs.cyclic.world.ChunkCoords
import org.sfinnqs.cyclic.world.CyclicChunk
import org.sfinnqs.cyclic.world.CyclicLocation
import org.sfinnqs.cyclic.world.RepresentativeChunk
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@ThreadSafe
class WorldManager {
    private val lock = ReentrantReadWriteLock()
    private val viewerIds = WeakMap<Player, UUID>()
    private val locations = LocationManager()
    private val fakeIds = FakeIds()
    private val visibility = Visibility()
    private val fakeMap: SetMultimap<UUID, FakeEntity> = HashMultimap.create()
    private val loadedChunks = ChunkManager()

    // the only method in this class that calls Player.getUniqueId()
    fun addPlayer(player: Player, id: UUID): Boolean {
        val readPrev = lock.read { viewerIds[player] }
        if (readPrev == id) return false
        if (readPrev != null)
            throw IllegalArgumentException("player already has id $readPrev")
        lock.write {
            val writePrev1 = viewerIds[player]
            if (writePrev1 == id) return false
            if (writePrev1 != null)
                throw IllegalArgumentException("player already has id $writePrev1")
            val writePrev2 = viewerIds.put(player, id)
            assert(writePrev2 == null)
            return true
        }
    }

    fun loadChunk(viewer: Player, coords: ChunkCoords) {
        // TODO remove lots of overlapping code with unloadChunk
        lock.read {
            val world = getViewerWorld(viewer) ?: return
            val chunk = CyclicChunk(world, coords)
            if (loadedChunks.contains(viewer, chunk)) return
        }
        val packets = mutableListOf<PacketToSend>()
        lock.write {
            val world = getViewerWorld(viewer) ?: return
            val chunk = CyclicChunk(world, coords)
            loadedChunks.add(viewer, chunk)
            for ((uuid, location) in locations[chunk.representative]) {
                val playerChunk = location.chunk
                if (playerChunk == chunk) continue
                val offset = chunk - playerChunk
                val fake = FakeEntity(uuid, offset)
                val newLocation = location + offset
                val newPackets = spawnFake(fake, viewer, newLocation).map {
                    PacketToSend(viewer, it)
                }
                packets.addAll(newPackets)
            }
        }
        val protocol = ProtocolLibrary.getProtocolManager()
        for (packet in packets)
            protocol.sendServerPacket(packet.viewer, packet.packet)
    }

    fun unloadChunk(viewer: Player, coords: ChunkCoords) {
        lock.read {
            val world = getViewerWorld(viewer)!!
            val chunk = CyclicChunk(world, coords)
            if (!loadedChunks.contains(viewer, chunk)) return
        }
        val packets = lock.write {
            val world = getViewerWorld(viewer)!!
            val chunk = CyclicChunk(world, coords)
            unloadPackets(viewer, chunk)
        }
        val protocol = ProtocolLibrary.getProtocolManager()
        for (packet in packets)
            protocol.sendServerPacket(packet.viewer, packet.packet)
    }

    fun unloadAllChunks(viewer: Player) {
        // TODO a cleaner solution
        val packets = lock.write {
            getLoadedChunks(viewer).flatMap { unloadPackets(viewer, it) }
        }
        val protocol = ProtocolLibrary.getProtocolManager()
        for (packet in packets)
            protocol.sendServerPacket(packet.viewer, packet.packet)
    }

    fun getLoadedChunks(viewer: Player, chunk: RepresentativeChunk? = null) =
        lock.read { loadedChunks[viewer, chunk] }

    fun setLocation(entity: UUID, location: CyclicLocation?): CyclicLocation? {
        lock.read {
            locations[entity]
        }?.takeIf { it == location }?.let { return it }

        val packets = mutableListOf<PacketToSend>()
        val oldLoc = lock.write {
            val oldLoc = locations.set(entity, location)
            for (viewer in loadedChunks.viewers) {
                // fake entities that move are removed from fakes, and any left
                // are despawned
                val fakes = visibility[viewer, entity].toMutableSet()
                if (location != null) {
                    packets.addAll(
                        moveFakes(
                            viewer,
                            entity,
                            location,
                            oldLoc,
                            fakes
                        )
                    )
                }
                // TODO despawn multiple fakes in one packet
                for (fake in fakes)
                    packets.add(PacketToSend(viewer, despawnFake(fake, viewer)))
            }
            oldLoc
        }
        val protocol = ProtocolLibrary.getProtocolManager()
        for (packet in packets)
            protocol.sendServerPacket(packet.viewer, packet.packet)
        return oldLoc
    }

    private fun moveFakes(
        viewer: Player,
        entity: UUID,
        location: CyclicLocation,
        oldLoc: CyclicLocation?,
        oldFakes: MutableSet<FakeEntity>
    ) = sequence {
        val chunk = location.chunk
        for (loadedChunk in loadedChunks[viewer, chunk.representative]) {
            if (loadedChunk == chunk) continue
            val offset = loadedChunk - chunk
            val fake = FakeEntity(entity, offset)
            val newLocation = location + offset
            if (oldFakes.remove(fake)) {
                // fake must be moved
                val oldFakeLoc = oldLoc!! + offset
                // TODO function to translate ImmutableLocation
                for (packet in updateFake(fake, newLocation, oldFakeLoc))
                    yield(PacketToSend(viewer, packet))
            } else {
                // fake must be spawned
                for (packet in spawnFake(fake, viewer, newLocation))
                    yield(PacketToSend(viewer, packet))
            }
        }
    }

    fun getViewerWorld(viewer: Player) = lock.read {
        viewerIds[viewer]?.let { locations[it] }
    }?.world

    private fun unloadPackets(
        viewer: Player,
        chunk: CyclicChunk
    ): List<PacketToSend> {
        val result = mutableListOf<PacketToSend>()
        val removed = loadedChunks.remove(viewer, chunk)
        if (!removed) return emptyList()
        for ((entity, location) in locations[chunk.representative]) {
            val entityChunk = location.chunk
            if (entityChunk == chunk) continue
            val fake = FakeEntity(entity, chunk - entityChunk)
            result.add(PacketToSend(viewer, despawnFake(fake, viewer)))
        }
        return result
    }

    private fun spawnFake(
        fake: FakeEntity,
        viewer: Player,
        location: CyclicLocation
    ): List<PacketContainer> {
        // TODO maybe combine the three methods somehow?
        val uuid = fake.entity
        visibility.add(viewer, fake)
        val protocol = ProtocolLibrary.getProtocolManager()
        val packet = protocol.createPacket(NAMED_ENTITY_SPAWN)
        fakeMap.put(uuid, fake)
        val eid = fakeIds.getOrCreate(fake)
        packet.integers.write(0, eid)
        packet.uuiDs.write(0, uuid)
        val doubles = packet.doubles
        doubles.write(0, location.x)
        doubles.write(1, location.y)
        doubles.write(2, location.z)
        val bytes = packet.bytes
        bytes.write(0, location.yawByte)
        bytes.write(1, location.pitchByte)

        // https://www.spigotmc.org/threads/metadata-protocollib.251684/#post-2513571
        val skinPacket = protocol.createPacket(ENTITY_METADATA)
        val watcher = WrappedDataWatcher()
        watcher.entity = viewer
        val byteClass = java.lang.Byte::class.java
        val serializer = WrappedDataWatcher.Registry.get(byteClass)
        val skinParts: Byte = 0x7f
        watcher.setObject(16, serializer, skinParts)
        skinPacket.integers.write(0, eid)
        val watchableCollection = skinPacket.watchableCollectionModifier
        watchableCollection.write(0, watcher.watchableObjects)

        return listOf(packet, skinPacket)
    }

    private fun updateFake(
        fake: FakeEntity,
        location: CyclicLocation,
        oldLocation: CyclicLocation
    ): List<PacketContainer> {
        assert(location.world == oldLocation.world)
        // TODO velocity
        val protocol = ProtocolLibrary.getProtocolManager()
        val movement = oldLocation.moveTo(location)
        val zeroShort: Short = 0
        val result = mutableListOf<PacketContainer>()
        if (movement == null) {
            val packet = protocol.createPacket(ENTITY_TELEPORT)
            packet.integers.write(0, fakeIds[fake]!!)
            val doubles = packet.doubles
            doubles.write(0, location.x)
            doubles.write(1, location.y)
            doubles.write(2, location.z)
            val bytes = packet.bytes
            bytes.write(0, location.yawByte)
            bytes.write(1, location.pitchByte)
            packet.booleans.write(0, location.grounded)
            result.add(packet)
        } else if (movement.deltaX == zeroShort && movement.deltaY == zeroShort && movement.deltaZ == zeroShort) {
            if (location.pitch != oldLocation.pitch) {
                val packet = protocol.createPacket(ENTITY_LOOK)
                packet.integers.write(0, fakeIds[fake]!!)
                val bytes = packet.bytes
                bytes.write(0, location.yawByte)
                bytes.write(1, location.pitchByte)
                packet.booleans.write(0, location.grounded)
                result.add(packet)
            }
        } else {
            if (location.yaw == oldLocation.yaw && location.pitch == oldLocation.pitch) {
                val packet = protocol.createPacket(REL_ENTITY_MOVE)
                packet.integers.write(0, fakeIds[fake]!!)
                val shorts = packet.shorts
                shorts.write(0, movement.deltaX)
                shorts.write(1, movement.deltaY)
                shorts.write(2, movement.deltaZ)
                packet.booleans.write(0, location.grounded)
                result.add(packet)
            } else {
                val packet = protocol.createPacket(REL_ENTITY_MOVE_LOOK)
                packet.integers.write(0, fakeIds[fake]!!)
                val shorts = packet.shorts
                shorts.write(0, movement.deltaX)
                shorts.write(1, movement.deltaY)
                shorts.write(2, movement.deltaZ)
                val bytes = packet.bytes
                bytes.write(0, location.yawByte)
                bytes.write(1, location.pitchByte)
                packet.booleans.write(0, location.grounded)
                result.add(packet)
            }
        }
        if (location.yaw != oldLocation.yaw) {
            val packet = protocol.createPacket(ENTITY_HEAD_ROTATION)
            packet.integers.write(0, fakeIds[fake]!!)
            packet.bytes.write(0, location.yawByte)
            result.add(packet)
        }
        return result
    }

    private fun despawnFake(fake: FakeEntity, viewer: Player): PacketContainer {
        val protocol = ProtocolLibrary.getProtocolManager()
        val packet = protocol.createPacket(ENTITY_DESTROY)
        val entityId = fakeIds[fake]!!
        packet.integerArrays?.write(0, intArrayOf(entityId))
        val wasSeen = visibility.remove(viewer, fake)
        assert(wasSeen)
        val entity = fake.entity
        if (visibility[fake].isEmpty()) {
            val removedId = fakeIds.remove(fake)
            assert(removedId)
            val wasInMap = fakeMap.remove(entity, fake)
            assert(wasInMap)
        }
        return packet
    }

    private data class PacketToSend(
        val viewer: Player,
        val packet: PacketContainer
    )

}
