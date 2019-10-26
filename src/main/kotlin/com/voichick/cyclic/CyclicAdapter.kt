/**
 * Cyclic - A Bukkit plugin for worlds that wrap around
 * Copyright (C) 2019 sfinnqs
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

import com.comphenix.protocol.PacketType.Play.Server.*
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction.ADD_PLAYER
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction.REMOVE_PLAYER
import com.google.common.collect.MapMaker
import net.jcip.annotations.ThreadSafe
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@ThreadSafe
class CyclicAdapter(private val cyclic: Cyclic) : PacketAdapter(cyclic, MAP_CHUNK, UNLOAD_CHUNK, NAMED_ENTITY_SPAWN, PLAYER_INFO) {

    private val customPackets = Collections.newSetFromMap(MapMaker().weakKeys().makeMap<Any, Boolean>())
    private val lock = ReentrantLock()
    private val knownUuids = WeakHashMap<Player, MutableSet<UUID>>()
    private val spawnQueue = WeakHashMap<Player, MutableMap<UUID, Queue<PacketContainer>>>()

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
            NAMED_ENTITY_SPAWN -> {
                val uuid = packet.uuiDs.read(0)
                lock.withLock {
                    if (uuid in knownUuids[player].orEmpty()) return
                    spawnQueue.getOrPut(player, ::mutableMapOf).getOrPut(uuid, ::LinkedList).add(packet)
                    event.isCancelled = true
                }
            }
            PLAYER_INFO -> {
                val uuids = packet.playerInfoDataLists.read(0).map { it.profile.uuid }
                when (packet.playerInfoAction.read(0)) {
                    ADD_PLAYER -> {
                        val packets = mutableListOf<PacketContainer>()
                        lock.withLock {
                            knownUuids.getOrPut(player, ::mutableSetOf).addAll(uuids)
                            val queues = spawnQueue[player] ?: return
                            for (uuid in uuids) {
                                val queue = queues.remove(uuid) ?: continue
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
                    REMOVE_PLAYER -> {
                        lock.withLock {
                            knownUuids[player]?.removeAll(uuids)
                        }
                    }
                    else -> return
                }
            }
        }

    }
}
