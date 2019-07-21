package com.voichick.cyclic

import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.generator.BlockPopulator
import java.util.*

class ForceLoadPopulator : BlockPopulator() {
    override fun populate(world: World, random: Random, source: Chunk) {
        val x = source.x
        val z = source.z
        source.isForceLoaded = x in 0 until X_CHUNKS && z in 0 until Z_CHUNKS
    }
}