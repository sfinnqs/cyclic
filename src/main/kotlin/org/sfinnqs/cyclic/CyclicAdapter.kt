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

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.PacketType.Play.Client
import com.comphenix.protocol.PacketType.Play.Client.POSITION_LOOK
import com.comphenix.protocol.PacketType.Play.Server
import com.comphenix.protocol.PacketType.Play.Server.*
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.reflect.StructureModifier
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction.ADD_PLAYER
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction.REMOVE_PLAYER
import net.jcip.annotations.ThreadSafe
import org.bukkit.entity.Player
import org.sfinnqs.cyclic.collect.WeakMap
import org.sfinnqs.cyclic.collect.WeakSet
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.sfinnqs.cyclic.TeleportFlag.X
import org.sfinnqs.cyclic.TeleportFlag.Z
import org.sfinnqs.cyclic.world.*

@ThreadSafe
class CyclicAdapter(cyclic: Cyclic) : PacketAdapter(
    cyclic,
    MAP_CHUNK,
    UNLOAD_CHUNK,
    NAMED_ENTITY_SPAWN,
    PLAYER_INFO,
    Server.POSITION,
    ENTITY_TELEPORT,
    Client.POSITION,
    Client.POSITION_LOOK
) {
    private val manager = cyclic.manager
    private val customPackets = WeakSet<Any>()
    private val lock = ReentrantReadWriteLock()
    private val knownIds = WeakMap<Player, MutableSet<UUID>>()
    private val spawnQueue =
        WeakMap<Player, MutableMap<UUID, Queue<PacketContainer>>>()

    override fun onPacketSending(event: PacketEvent) {
        val packet = event.packet
        if (packet.handle in customPackets) return
        val player = event.player ?: return
        when (event.packetType) {
            MAP_CHUNK -> {
                val ints = packet.integers
                val coords = ChunkCoords(ints.read(0), ints.read(1))
                val (world, offset) = manager.loadChunk(player, coords)
                    ?: return
                val clientCoords = (CyclicChunk(world, coords) + offset).coords
                logger.info {
                    "loading chunk at server $coords, client $clientCoords"
                }
                ints.write(0, clientCoords.x)
                ints.write(1, clientCoords.z)
            }
            UNLOAD_CHUNK -> {
                // TODO merge with MAP_CHUNK somehow
                val ints = packet.integers
                val coords = ChunkCoords(ints.read(0), ints.read(1))
                val (world, offset) = manager.unloadChunk(player, coords)
                    ?: return
                val clientCoords = (CyclicChunk(world, coords) + offset).coords
                logger.info {
                    "UNloading chunk at server $coords, client $clientCoords"
                }
                ints.write(0, clientCoords.x)
                ints.write(1, clientCoords.z)
            }
            NAMED_ENTITY_SPAWN -> {
                val id = packet.uuiDs.read(0)
                offsetServerDoubles(player, packet.doubles)
                if (lock.read { id in knownIds[player].orEmpty() }) return
                lock.write {
                    if (id in knownIds[player].orEmpty()) return
                    spawnQueue.getOrPut(player, ::mutableMapOf)
                        .getOrPut(id, ::LinkedList)
                        .add(packet)
                    event.isCancelled = true
                }
            }
            PLAYER_INFO -> {
                val ids = packet.playerInfoDataLists.read(0).map {
                    it.profile.uuid
                }
                when (packet.playerInfoAction.read(0)) {
                    ADD_PLAYER -> {
                        val packets = mutableListOf<PacketContainer>()
                        lock.write {
                            knownIds.getOrPut(player, ::mutableSetOf)
                                .addAll(ids)
                            val queues = spawnQueue[player] ?: return
                            for (id in ids) {
                                val queue = queues.remove(id) ?: continue
                                packets.addAll(queue)
                                if (queues.isEmpty())
                                    spawnQueue.remove(player)
                            }
                        }
                        if (packets.isEmpty()) return
                        event.isCancelled = true
                        customPackets.add(packet.handle)
                        val protocol = ProtocolLibrary.getProtocolManager()
                        protocol.sendServerPacket(player, packet)
                        for (toSend in packets)
                            protocol.sendServerPacket(player, toSend)
                    }
                    REMOVE_PLAYER -> lock.write {
                        knownIds[player]?.removeAll(ids)
                    }
                    else -> return
                }
            }
            Server.POSITION -> {
                val (world, offset) = manager.getWorldAndOffset(player)
                    ?: return
                val doubles = packet.doubles
                // can't use offsetDoubles because they might be relative
                val x = doubles.read(0)
                val z = doubles.read(2)
                // y, yaw, pitch, and grounded are ignored
                val location =
                    CyclicLocation(world, x, 0.0, z, 0.0F, 0.0F, false)
                val clientLocation = location + offset
                val flags = packet.getSets(TeleportFlag.Converter)!!.read(0)!!
                // absolute x
                if (X !in flags) doubles.write(0, clientLocation.x)
                // absolute z
                if (Z !in flags) doubles.write(2, clientLocation.z)
                logger.info { "server location: ${x to z} converted to $clientLocation" }
            }
            ENTITY_TELEPORT -> offsetServerDoubles(player, packet.doubles)
        }
    }

    override fun onPacketReceiving(event: PacketEvent) {
        val packet = event.packet
        if (packet.handle in customPackets) return
        val player = event.player ?: return
        when (packet.type) {
            Client.POSITION, POSITION_LOOK -> offsetClientDoubles(
                player,
                packet.doubles
            )
        }
    }

    private fun offsetServerDoubles(
        player: Player,
        doubles: StructureModifier<Double>
    ) {
        val (world, offset) = manager.getWorldAndOffset(player) ?: return
        offsetDoubles(doubles, world, offset)
    }

    private fun offsetClientDoubles(
        player: Player,
        doubles: StructureModifier<Double>
    ) {
        val (world, offset) = manager.getWorldAndOffset(player) ?: return
//        logger.info { "offsetting client doubles by ${-offset}" }
        offsetDoubles(doubles, world, -offset)
    }

    private fun offsetDoubles(
        doubles: StructureModifier<Double>,
        world: CyclicWorld,
        offset: WorldOffset
    ) {
        val x = doubles.read(0)
        val z = doubles.read(2)
        // y, yaw, pitch, and grounded are ignored
        val location = CyclicLocation(world, x, 0.0, z, 0.0F, 0.0F, false)
        val clientLocation = location + offset
        doubles.write(0, clientLocation.x)
        doubles.write(2, clientLocation.z)
    }
}
