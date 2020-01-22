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

import kotlinx.collections.immutable.toImmutableSet
import net.jcip.annotations.NotThreadSafe
import org.bukkit.entity.Player
import org.sfinnqs.cyclic.FakeEntity
import java.util.*

@NotThreadSafe
class Visibility {
    private val seenBy = mutableMapOf<FakeEntity, MutableSet<Player>>()
    private val canSee = WeakHashMap<Player, MutableMap<UUID, MutableSet<FakeEntity>>>()

    fun add(viewer: Player, fake: FakeEntity): Boolean {
        val id = fake.entity
        val addedSeenBy = seenBy.getOrPut(fake) {
            Collections.newSetFromMap(WeakHashMap<Player, Boolean>())
        }.add(viewer)
        return if (addedSeenBy) {
            val addedCanSee = canSee.getOrPut(viewer, ::mutableMapOf).getOrPut(id, ::mutableSetOf).add(fake)
            assert(addedCanSee)
            true
        } else {
            assert(canSee[viewer]!![id]!!.contains(fake))
            false
        }
    }

    operator fun get(viewer: Player, entity: UUID) = canSee[viewer]?.get(entity).orEmpty().toImmutableSet()

    operator fun get(fake: FakeEntity) = seenBy[fake].orEmpty().toImmutableSet()

    fun remove(viewer: Player, fake: FakeEntity): Boolean {
        val seenFakes = canSee[viewer]
        if (seenFakes == null) {
            assert(seenBy[fake]?.contains(viewer) != true)
            return false
        }
        assert(seenFakes.isNotEmpty())
        val id = fake.entity
        val seenIdFakes = seenFakes[id]
        if (seenIdFakes == null) {
            assert(seenBy[fake]?.contains(viewer) != true)
            return false
        }
        assert(seenIdFakes.isNotEmpty())
        val removedCanSee = seenIdFakes.remove(fake)
        return if (removedCanSee) {
            if (seenIdFakes.isEmpty())
                if (seenFakes.remove(id) != null && seenFakes.isEmpty())
                    canSee.remove(viewer)
            val viewers = seenBy[fake]!!
            val removedSeenBy = viewers.remove(viewer)
            assert(removedSeenBy)
            if (viewers.isEmpty())
                seenBy.remove(fake)
            true
        } else {
            assert(seenBy[fake]?.contains(viewer) != true)
            false
        }
    }
}
