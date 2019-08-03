package com.voichick.cyclic

import org.bukkit.Chunk
import org.bukkit.block.Block

const val X_CHUNKS = 3
const val Z_CHUNKS = 3

const val MAX_X = X_CHUNKS * 16
const val MAX_Z = Z_CHUNKS * 16

val Block.isRepresentative
    get() = x in 0 until MAX_X && z in 0 until MAX_Z

val Chunk.location
    get() = ChunkLocation(x, z)
