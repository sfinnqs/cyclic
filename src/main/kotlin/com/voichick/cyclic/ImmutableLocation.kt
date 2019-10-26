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
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.NumberConversions.floor
import org.bukkit.util.NumberConversions.round
import kotlin.math.PI
import kotlin.math.roundToLong

@Immutable
data class ImmutableLocation(val x: Double, val y: Double, val z: Double, val yaw: Byte, val pitch: Byte, val grounded: Boolean) {
    val chunk = ChunkLocation(floor(x) shr 4, floor(z) shr 4)

    constructor(x: Double, y: Double, z: Double, yaw: Float, pitch: Float, grounded: Boolean) : this(x, y, z, angleFloatToByte(yaw), angleFloatToByte(pitch), grounded)
    constructor(location: Location, grounded: Boolean) : this(location.x, location.y, location.z, location.yaw, location.pitch, grounded)
    constructor(player: Player) : this(player.location, player.isOnGround)

    operator fun minus(other: ImmutableLocation): LocationOffset? {
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

    private companion object {
        fun angleFloatToByte(angle: Float) = round(angle * 256.0 / 360.0).toByte()
    }
}
