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

import net.jcip.annotations.Immutable
import java.lang.Math.floorMod

@Immutable
data class ChunkLocation(val x: Int, val z: Int) {
    val isRepresentative
        get() = x in 0 until X_CHUNKS && z in 0 until Z_CHUNKS

    fun equalsParallel(other: ChunkLocation): Boolean {
        return floorMod(other.x - x, X_CHUNKS) == 0 && floorMod(other.z - z, Z_CHUNKS) == 0
    }
}
