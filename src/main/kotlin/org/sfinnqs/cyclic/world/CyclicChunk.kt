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
package org.sfinnqs.cyclic.world

import net.jcip.annotations.Immutable
import java.lang.Math.floorMod

@Immutable
data class CyclicChunk(val world: CyclicWorld, val coords: ChunkCoords) {

    val representative: RepresentativeChunk

    init {
        val config = world.config
        val repX = floorMod(coords.x, config.xChunks)
        val repZ = floorMod(coords.z, config.zChunks)
        val coords = ChunkCoords(repX, repZ)
        representative = RepresentativeChunk(world, coords)
    }

    val isRepresentative = coords.isRepresentative(world.config)

    operator fun minus(other: CyclicChunk): WorldOffset {
        if (world != other.world)
            throw IllegalArgumentException("other must be from the same world")
        val config = world.config
        val xChunks = config.xChunks
        val zChunks = config.zChunks
        val otherCoords = other.coords
        val deltaX = coords.x - otherCoords.x
        val deltaZ = coords.z - otherCoords.z
        if (deltaX % xChunks != 0 || deltaZ % zChunks != 0)
            throw IllegalArgumentException("these chunks are not equivalent")
        return WorldOffset(deltaX / xChunks, deltaZ / zChunks)
    }

    operator fun plus(offset: WorldOffset): CyclicChunk {
        val config = world.config
        val newX = coords.x + offset.deltaX * config.xChunks
        val newZ = coords.z + offset.deltaZ * config.zChunks
        return copy(coords = ChunkCoords(newX, newZ))
    }

    operator fun minus(offset: WorldOffset) = this + -offset

}
