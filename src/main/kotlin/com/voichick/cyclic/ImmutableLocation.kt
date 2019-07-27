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