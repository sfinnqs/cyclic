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
package com.voichick.cyclic.gen

import com.voichick.cyclic.Z_CHUNKS
import com.voichick.cyclic.location
import net.jcip.annotations.NotThreadSafe
import org.bukkit.Chunk
import org.bukkit.Material.GRASS_BLOCK
import org.bukkit.TreeType.TREE
import org.bukkit.World
import org.bukkit.block.BlockFace.DOWN
import org.bukkit.generator.BlockPopulator
import java.util.*

@NotThreadSafe
class TreePopulator : BlockPopulator() {
    override fun populate(world: World, random: Random, source: Chunk) {
        if (!source.location.isRepresentative) return
        random.setSeed(world.seed + source.x * Z_CHUNKS + source.z)
        for (localX in 0..15)
            for (localZ in 0..15) {
                if (random.nextInt(200) != 0) continue
                val treeX = (source.x shl 4) + localX
                val treeZ = (source.z shl 4) + localZ
                val block = world.getHighestBlockAt(treeX, treeZ)
                if (block.getRelative(DOWN).type == GRASS_BLOCK)
                    world.generateTree(block.location, TREE)
            }
    }
}
