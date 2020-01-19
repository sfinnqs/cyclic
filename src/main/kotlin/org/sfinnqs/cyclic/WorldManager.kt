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

import com.comphenix.protocol.PacketType.Play.Server.*
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import kotlinx.collections.immutable.toImmutableSet
import org.sfinnqs.cyclic.world.ChunkCoords
import org.sfinnqs.cyclic.world.CyclicChunk
import org.sfinnqs.cyclic.world.CyclicLocation
import net.jcip.annotations.ThreadSafe
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@ThreadSafe
class WorldManager {
    private val lock = ReentrantReadWriteLock()
    private val viewerIds = WeakHashMap<Player, UUID>()
    private val entityLocs = mutableMapOf<UUID, CyclicLocation>()
    private val fakeIds = FakeIds()
    private val visibility = Visibility()
    private val dupMap = mutableMapOf<UUID, MutableSet<FakeEntity>>()
    // also used as a set of players
    // TODO players should be removed from here
    private val loadedChunks = WeakHashMap<Player, MutableSet<CyclicChunk>>()

//    var config: CyclicConfig = config
//        get() = lock.read { field }
//        set(value) {
//            val worlds = value.worlds
//            fun newWorld(world: CyclicWorld) = world.setConfig(worlds[world.id])
//            lock.read {
//                val usedWorlds = entityLocs.values.map {
//                    it.world
//                }.toSet() + loadedChunks.values.flatMap { set ->
//                    set.map { chunk -> chunk.world }
//                }
//                if (usedWorlds.all { it == newWorld(it) }) return
//            }
//            lock.write {
//                val locIterator = entityLocs.iterator()
//                while (locIterator.hasNext()) {
//                    val next = locIterator.next()
//                    val oldLoc = next.value
//                    next.setValue(oldLoc.setWorld(newWorld(oldLoc.world)))
//                }
//                val setIterator = loadedChunks.iterator()
//                while (setIterator.hasNext()) {
//                    val next = setIterator.next()
//                    val newValue = next.value.map {
//                        it.setWorld(newWorld(it.world))
//                    }.toMutableSet()
//                    next.setValue(newValue)
//                }
//            }
//            field = value
//        }

    // the only method in this class that calls Player.getUniqueId()
    fun addPlayer(player: Player) {
        assert(Bukkit.isPrimaryThread())
        val id = player.uniqueId
        if (id == lock.read { viewerIds[player] }) return
        val prev = lock.write { viewerIds.put(player, id) }
        assert(prev == null || prev == id)
    }

    fun loadChunk(viewer: Player, coords: ChunkCoords) {
        // TODO remove lots of overlapping code with unloadChunk
        lock.read {
            val world = getViewerWorld(viewer) ?: return
            val chunk = CyclicChunk(world, coords)
            if (loadedChunks[viewer]?.contains(chunk) == true) return
        }
        val packets = mutableListOf<PacketToSend>()
        lock.write {
            val world = getViewerWorld(viewer) ?: return
            val chunk = CyclicChunk(world, coords)
            val worldConfig = world.config
            loadedChunks.getOrPut(viewer, ::mutableSetOf).add(chunk)
            // TODO maybe group entities by chunk to make more efficient
            for ((uuid, location) in entityLocs) {
                val playerChunk = location.chunk
                if (playerChunk == chunk || !playerChunk.equalsParallel(chunk))
                    continue
                val offsetX = (chunk.x - playerChunk.x) / worldConfig.xChunks
                val offsetZ = (chunk.z - playerChunk.z) / worldConfig.zChunks
                val duplicate = FakeEntity(uuid, offsetX, offsetZ)
                val newX = location.x + offsetX * worldConfig.maxX
                val newZ = location.z + offsetZ * worldConfig.maxZ
                assert(location.world == world)
                val newLocation = location.copy(x = newX, z = newZ)
                packets.add(PacketToSend(viewer, spawnDuplicate(duplicate, viewer, newLocation)))
            }
        }
        for (packet in packets)
            ProtocolLibrary.getProtocolManager().sendServerPacket(packet.viewer, packet.packet)
    }

    fun unloadChunk(viewer: Player, coords: ChunkCoords) {
        lock.read {
            val world = getViewerWorld(viewer)!!
            val chunk = CyclicChunk(world, coords)
            if (loadedChunks[viewer]?.contains(chunk) != true) return
        }
        val packets = lock.write {
            val world = getViewerWorld(viewer)!!
            val chunk = CyclicChunk(world, coords)
            unloadPackets(viewer, chunk)
        }
        for (packet in packets)
            ProtocolLibrary.getProtocolManager().sendServerPacket(packet.viewer, packet.packet)
    }

    fun unloadAllChunks(viewer: Player) {
        // TODO a cleaner solution
        val packets = lock.write {
            getLoadedChunks(viewer).flatMap { unloadPackets(viewer, it) }
        }
        for (packet in packets)
            ProtocolLibrary.getProtocolManager().sendServerPacket(packet.viewer, packet.packet)
    }

    fun getLoadedChunks(viewer: Player) = lock.read {
        loadedChunks[viewer].orEmpty().toImmutableSet()
    }

    fun setLocation(entity: UUID, location: CyclicLocation?): CyclicLocation? {
        lock.read {
            entityLocs[entity]
        }?.takeIf { it == location }?.let { return it }

        val packets = mutableListOf<PacketToSend>()
        val oldLoc = lock.write {
            val oldLoc = if (location == null)
                entityLocs.remove(entity)
            else
                entityLocs.put(entity, location)
            for ((viewer, chunks) in loadedChunks) {
                // fake entities that move are removed from fakes, and any left
                // are despawned
                val fakes = visibility[viewer, entity].toMutableSet()
                if (location != null) {
                    val chunk = location.chunk
                    val worldConfig = location.world.config
                    for (loadedChunk in chunks) {
                        if (loadedChunk == chunk || !loadedChunk.equalsParallel(chunk))
                            continue
                        val offsetX = (loadedChunk.x - chunk.x) / worldConfig.xChunks
                        val offsetZ = (loadedChunk.z - chunk.z) / worldConfig.zChunks
                        val fake = FakeEntity(entity, offsetX, offsetZ)
                        val maxX = worldConfig.maxX
                        val maxZ = worldConfig.maxZ
                        val newX = location.x + offsetX * maxX
                        val newZ = location.z + offsetZ * maxZ
                        val newLocation = location.copy(x = newX, z = newZ)
                        if (fakes.remove(fake)) {
                            // fake must be moved
                            val oldDupX = oldLoc!!.x + offsetX * maxX
                            val oldDupZ = oldLoc.z + offsetZ * maxZ
                            // TODO function to translate ImmutableLocation
                            val oldDupLoc = oldLoc.copy(x = oldDupX, z = oldDupZ)
                            packets.addAll(updateDuplicate(fake, newLocation, oldDupLoc).map { PacketToSend(viewer, it) })
                        } else {
                            // fake must be spawned
                            packets.add(PacketToSend(viewer, spawnDuplicate(fake, viewer, newLocation)))
                        }
                    }
                }
                // TODO despawn multiple duplicates in one packet
                packets.addAll(fakes.map { PacketToSend(viewer, despawnDuplicate(it, viewer)) })
            }
            oldLoc
        }
        for (packet in packets)
            ProtocolLibrary.getProtocolManager().sendServerPacket(packet.viewer, packet.packet)
        return oldLoc
    }

    fun getViewerWorld(viewer: Player) = lock.read {
        entityLocs[viewerIds[viewer]]
    }?.world

    private fun unloadPackets(viewer: Player, chunk: CyclicChunk): List<PacketToSend> {
        val result = mutableListOf<PacketToSend>()
        val removed = loadedChunks[viewer]?.remove(chunk) ?: false
        if (!removed) return emptyList()
        val world = chunk.world
        val worldConfig = world.config
        for ((entity, location) in entityLocs) {
            val entityChunk = location.chunk
            if (entityChunk == chunk || !entityChunk.equalsParallel(chunk)) continue
            assert(location.world == world)
            val offsetX = (chunk.x - entityChunk.x) / worldConfig.xChunks
            val offsetZ = (chunk.z - entityChunk.z) / worldConfig.zChunks
            val duplicate = FakeEntity(entity, offsetX, offsetZ)
            result.add(PacketToSend(viewer, despawnDuplicate(duplicate, viewer)))
        }
        return result
    }

    private fun spawnDuplicate(fake: FakeEntity, viewer: Player, location: CyclicLocation): PacketContainer {
        // TODO maybe combine the three methods somehow?
        val uuid = fake.entity
        visibility.add(viewer, fake)
        val protocol = ProtocolLibrary.getProtocolManager()
        val packet = protocol.createPacket(NAMED_ENTITY_SPAWN)
        dupMap.getOrPut(uuid, ::mutableSetOf).add(fake)
        packet.integers.write(0, fakeIds.getOrCreate(fake))
        packet.uuiDs.write(0, uuid)
        val doubles = packet.doubles
        doubles.write(0, location.x)
        doubles.write(1, location.y)
        doubles.write(2, location.z)
        val bytes = packet.bytes
        bytes.write(0, location.yawByte)
        bytes.write(1, location.pitchByte)

        // TODO get this part to work
        // https://www.spigotmc.org/threads/metadata-protocollib.251684/#post-2513571
//        val watcher = WrappedDataWatcher()
//        watcher.entity = viewer
//        val serializer = WrappedDataWatcher.Registry.get(java.lang.Byte::class.java)
//        val skinParts: Byte = 0x7f
//        watcher.setObject(15, serializer, skinParts)
//        packet.dataWatcherModifier.write(0, watcher)

        return packet
    }

    private fun updateDuplicate(fake: FakeEntity, location: CyclicLocation, oldLocation: CyclicLocation): List<PacketContainer> {
        assert(location.world == oldLocation.world)
        // TODO velocity
        val protocol = ProtocolLibrary.getProtocolManager()
        val offset = location - oldLocation
        val zeroShort: Short = 0
        val result = mutableListOf<PacketContainer>()
        if (offset == null) {
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
        } else if (offset.deltaX == zeroShort && offset.deltaY == zeroShort && offset.deltaZ == zeroShort) {
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
                shorts.write(0, offset.deltaX)
                shorts.write(1, offset.deltaY)
                shorts.write(2, offset.deltaZ)
                packet.booleans.write(0, location.grounded)
                result.add(packet)
            } else {
                val packet = protocol.createPacket(REL_ENTITY_MOVE_LOOK)
                packet.integers.write(0, fakeIds[fake]!!)
                val shorts = packet.shorts
                shorts.write(0, offset.deltaX)
                shorts.write(1, offset.deltaY)
                shorts.write(2, offset.deltaZ)
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

    private fun despawnDuplicate(fake: FakeEntity, viewer: Player): PacketContainer {
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
            val wasInMap = dupMap[entity]!!.remove(fake)
            assert(wasInMap)
        }
        return packet
    }

    private data class PacketToSend(val viewer: Player, val packet: PacketContainer)

}
