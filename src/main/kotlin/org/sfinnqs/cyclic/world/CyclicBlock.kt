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

import com.comphenix.protocol.wrappers.BlockPosition
import net.jcip.annotations.Immutable
import org.bukkit.block.Block
import java.lang.Math.floorMod

@Immutable
data class CyclicBlock(
    val world: CyclicWorld,
    val x: Int,
    val y: Int,
    val z: Int
) {

    constructor(world: CyclicWorld, block: Block) : this(
        world,
        block.x,
        block.y,
        block.z
    )

    val chunk = CyclicChunk(world, ChunkCoords(x shr 4, z shr 4))

    val isRepresentative: Boolean
        get() {
            val config = world.config
            return x in 0 until config.maxX && z in 0 until config.maxZ
        }

    val representative: CyclicBlock
        get() {
            val config = world.config
            val newX = floorMod(x, config.maxX)
            val newZ = floorMod(z, config.maxZ)
            return copy(x = newX, z = newZ)
        }

    operator fun plus(offset: WorldOffset): CyclicBlock {
        val config = world.config
        val newX = x + offset.deltaX * config.maxX
        val newZ = z + offset.deltaZ * config.maxZ
        return copy(x = newX, z = newZ)
    }

    fun toBlockPosition() = BlockPosition(x, y, z)

}
