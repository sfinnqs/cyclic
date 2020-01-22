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

import kotlinx.collections.immutable.toImmutableMap
import net.jcip.annotations.NotThreadSafe
import org.sfinnqs.cyclic.world.CyclicLocation
import org.sfinnqs.cyclic.world.RepresentativeChunk
import java.util.*

@NotThreadSafe
class LocationManager {

    private val entityLocs = mutableMapOf<UUID, CyclicLocation>()
    private val chunkEntities = mutableMapOf<RepresentativeChunk, MutableMap<UUID, CyclicLocation>>()

    operator fun get(entity: UUID) = entityLocs[entity]
    operator fun set(entity: UUID, location: CyclicLocation?): CyclicLocation? {
        val old = if (location == null)
            entityLocs.remove(entity)
        else
            entityLocs.put(entity, location)
        if (old != null)
            chunkEntities[old.chunk.representative]!!.remove(entity)
        if (location != null)
            chunkEntities.getOrPut(location.chunk.representative, ::mutableMapOf)[entity] = location
        return old
    }

    operator fun get(chunk: RepresentativeChunk) = chunkEntities[chunk].orEmpty().toImmutableMap()
}
