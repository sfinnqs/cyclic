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

import org.sfinnqs.cyclic.LocationOffset
import net.jcip.annotations.Immutable
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.util.NumberConversions.floor
import org.bukkit.util.NumberConversions.round
import kotlin.math.roundToLong

@Immutable
data class CyclicLocation(val world: CyclicWorld, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float, val grounded: Boolean) {
    val yawByte = angleFloatToByte(yaw)
    val pitchByte = angleFloatToByte(pitch)
    val block = CyclicBlock(world, floor(x), floor(y), floor(z))
    val chunk = block.chunk

    constructor(world: CyclicWorld, location: Location, grounded: Boolean) : this(world, location.x, location.y, location.z, location.yaw, location.pitch, grounded)
    constructor(world: CyclicWorld, entity: Entity) : this(world, entity.location, entity.isOnGround)

    val representative: CyclicLocation
        get() {
            val representativeBlock = block.representative
            val offsetX = block.x - representativeBlock.x
            val offsetZ = block.z - representativeBlock.z
            return copy(x = x - offsetX, z = z - offsetZ)
        }

    operator fun minus(other: CyclicLocation): LocationOffset? {
        val shortRange = Short.MIN_VALUE..Short.MAX_VALUE
        val deltaX = (x * 4096).roundToLong() - (other.x * 4096).roundToLong()
        val deltaXShort = if (deltaX in shortRange)
            deltaX.toShort()
        else
            return null
        val deltaY = (y * 4096).roundToLong() - (other.y * 4096).roundToLong()
        val deltaYShort = if (deltaY in shortRange)
            deltaY.toShort()
        else
            return null
        val deltaZ = (z * 4096).roundToLong() - (other.z * 4096).roundToLong()
        val deltaZShort = if (deltaZ in shortRange)
            deltaZ.toShort()
        else
            return null
        return LocationOffset(deltaXShort, deltaYShort, deltaZShort)
    }

    fun setWorld(world: CyclicWorld) = copy(world = world)

    fun toBukkitLocation(world: World) = Location(world, x, y, z, yaw, pitch)

    private companion object {
        fun angleFloatToByte(angle: Float) = round(angle * 256.0 / 360.0).toByte()
    }
}
