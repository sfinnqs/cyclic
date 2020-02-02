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

import com.google.common.collect.MapMaker
import net.jcip.annotations.NotThreadSafe
import org.bukkit.entity.Player
import org.sfinnqs.cyclic.collect.WeakMap
import java.util.*

@NotThreadSafe
class ViewerEntities {
    private val viewerEntities = WeakMap<Player, UUID>()
    private val entityViewers: MutableMap<UUID, Player> =
        MapMaker().weakValues().makeMap()

    operator fun get(viewer: Player) = viewerEntities[viewer]
    operator fun get(entity: UUID) = entityViewers[entity]

    fun add(viewer: Player, entity: UUID): Boolean {
        val prevEntity = viewerEntities[viewer]
        if (prevEntity == entity) return false
        if (prevEntity != null)
            throw IllegalArgumentException("viewer already has id $prevEntity")
        val prevViewer = entityViewers[entity]
        assert(prevViewer != viewer)
        if (prevViewer != null)
            throw IllegalArgumentException("id already assigned to viewer $prevViewer")
        val prevEntity2 = viewerEntities.put(viewer, entity)
        assert(prevEntity2 == null)
        val prevViewer2 = entityViewers.put(entity, viewer)
        assert(prevViewer2 == null)
        return true
    }

    fun remove(viewer: Player): UUID? {
        val entity = viewerEntities.remove(viewer) ?: return null
        val prevViewer = entityViewers.remove(entity)
        assert(prevViewer == viewer)
        return entity
    }
}
