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
import org.sfinnqs.cyclic.world.FakeEntity
import java.util.*

@NotThreadSafe
class Visibility {
    private val seenBy = mutableMapOf<FakeEntity, WeakSet<Player>>()
    private val canSee = WeakMap<Player, SetMultimap<UUID, FakeEntity>>()

    fun add(viewer: Player, fake: FakeEntity): Boolean {
        val id = fake.entity
        val addedSeenBy = seenBy.getOrPut(fake, ::WeakSet).add(viewer)
        return if (addedSeenBy) {
            val playerMap = canSee.getOrPut(viewer) { HashMultimap.create() }
            val addedCanSee = playerMap.put(id, fake)
            assert(addedCanSee)
            true
        } else {
            assert(fake in canSee[viewer]!![id]!!)
            false
        }
    }

    operator fun get(viewer: Player, entity: UUID): Set<FakeEntity> =
        canSee[viewer]?.get(entity).orEmpty().toImmutableSet()

    operator fun get(fake: FakeEntity): Set<Player> {
        val contents = seenBy[fake]
        return if (contents.isNullOrEmpty()) {
            emptySet()
        } else {
            val result = WeakSet<Player>()
            result.addAll(contents)
            Collections.unmodifiableSet(result)
        }
    }

    fun remove(viewer: Player, fake: FakeEntity): Boolean {
        val seenFakes = canSee[viewer]
        if (seenFakes == null) {
            assert(seenBy[fake]?.contains(viewer) != true)
            return false
        }
        assert(!seenFakes.isEmpty)
        if (seenFakes.remove(fake.entity, fake)) {
            if (seenFakes.isEmpty) {
                val removedCanSee = canSee.remove(viewer)
                assert(removedCanSee == seenFakes)
            }
            val viewers = seenBy[fake]!!
            val removedViewers = viewers.remove(viewer)
            assert(removedViewers)
            if (viewers.isEmpty()) {
                val removedSeenBy = seenBy.remove(fake)
                assert(removedSeenBy == viewers)
            }
            return true
        } else {
            assert(seenBy[fake]?.contains(viewer) != true)
            return false
        }
    }

    fun remove(viewer: Player): Set<FakeEntity> {
        val seenByIterator = seenBy.values.iterator()
        while (seenByIterator.hasNext()) {
            val viewers = seenByIterator.next()
            if (viewers.remove(viewer) && viewers.isEmpty())
                seenByIterator.remove()
        }
        return canSee.remove(viewer)?.values().orEmpty().toImmutableSet()
    }
}
