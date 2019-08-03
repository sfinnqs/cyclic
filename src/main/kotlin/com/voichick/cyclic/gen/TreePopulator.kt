package com.voichick.cyclic.gen

import com.voichick.cyclic.Z_CHUNKS
import com.voichick.cyclic.location
import org.bukkit.Chunk
import org.bukkit.Material.GRASS_BLOCK
import org.bukkit.TreeType.TREE
import org.bukkit.World
import org.bukkit.block.BlockFace.DOWN
import org.bukkit.generator.BlockPopulator
import java.util.*

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
