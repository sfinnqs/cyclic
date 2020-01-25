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

import com.google.common.collect.HashMultimap
import com.google.common.collect.SetMultimap
import kotlinx.collections.immutable.toImmutableSet
import net.jcip.annotations.NotThreadSafe
import org.bukkit.entity.Player
import org.sfinnqs.cyclic.collect.WeakMap
import org.sfinnqs.cyclic.collect.WeakSet
import org.sfinnqs.cyclic.world.CyclicChunk
import org.sfinnqs.cyclic.world.RepresentativeChunk
import java.util.*

@NotThreadSafe
class ChunkManager {

    private val all = WeakMap<Player, MutableSet<CyclicChunk>>()
    private val reps =
        WeakMap<Player, SetMultimap<RepresentativeChunk, CyclicChunk>>()

    val viewers: Set<Player>
        get() {
            val result = WeakSet<Player>()
            result.addAll(all.keys)
            return Collections.unmodifiableSet(result)
        }

    operator fun get(viewer: Player, chunk: RepresentativeChunk? = null) =
        if (chunk == null)
            all[viewer].orEmpty().toImmutableSet()
        else
            reps[viewer]?.get(chunk).orEmpty().toImmutableSet()

    fun contains(viewer: Player, chunk: CyclicChunk) =
        all[viewer]?.contains(chunk) ?: false

    fun contains(viewer: Player): Boolean {
        val result = all[viewer] != null
        assert(result == (reps[viewer] != null))
        return result
    }

    fun add(viewer: Player, chunk: CyclicChunk): Boolean {
        val addedToAll = all.computeIfAbsent(viewer) {
            mutableSetOf()
        }.add(chunk)
        val addedToReps = reps.computeIfAbsent(viewer) {
            HashMultimap.create()
        }.put(chunk.representative, chunk)
        assert(addedToAll == addedToReps)
        return addedToAll
    }

    fun remove(viewer: Player, chunk: CyclicChunk? = null): Boolean {
        if (chunk == null) {
            val removedAll = all.remove(viewer) != null
            val removedReps = reps.remove(viewer) != null
            assert(removedAll == removedReps)
            return removedAll
        }
        val seenGroups = reps[viewer]
        if (seenGroups == null) {
            assert(all[viewer]?.contains(chunk) != true)
            return false
        }
        assert(!seenGroups.isEmpty)
        if (seenGroups.remove(chunk.representative, chunk)) {
            if (seenGroups.isEmpty) {
                val removedReps = reps.remove(viewer)
                assert(removedReps == seenGroups)
            }
            val seenChunks = all[viewer]!!
            val removedSeenChunks = seenChunks.remove(chunk)
            assert(removedSeenChunks)
            if (seenChunks.isEmpty()) {
                val removedAll = all.remove(viewer)
                assert(removedAll == seenChunks)
            }
            return true
        } else {
            assert(all[viewer]?.contains(chunk) != true)
            return false
        }
    }

}
