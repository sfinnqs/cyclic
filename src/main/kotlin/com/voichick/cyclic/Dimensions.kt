package com.voichick.cyclic

import org.bukkit.util.NumberConversions.floor
import java.lang.Math.floorMod

const val X_CHUNKS = 4
const val Z_CHUNKS = 3

const val MAX_X = X_CHUNKS * 16
const val MAX_Z = Z_CHUNKS * 16


infix fun Double.floatMod(y: Int): Double {
    val floored = floor(this)
    return this - (floored - floorMod(floored, y))
}

fun offsetX(clientX: Double) = offset(clientX, MAX_X)

fun offsetZ(clientZ: Double) = offset(clientZ, MAX_Z)

private fun offset(clientCoord: Double, max: Int): Int {
    val floored = floor(clientCoord)
    return floored - floorMod(floored, max)
}
