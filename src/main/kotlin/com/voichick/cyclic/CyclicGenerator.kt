package com.voichick.cyclic

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.generator.ChunkGenerator
import java.lang.Math.floorMod
import java.util.*

class CyclicGenerator : ChunkGenerator() {


    override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData {
        for (localX in 0..15)
            for (localZ in 0..15)
                biome.setBiome(localX, localZ, Biome.PLAINS)
        val result = createChunkData(world)
        if (x !in 0 until X_CHUNKS || z !in 0 until Z_CHUNKS)
            return result
        result.setRegion(0, 0, 0, 16, 1, 16, Material.BEDROCK)
        result.setRegion(0, 1, 0, 16, 60, 16, Material.STONE)
        result.setRegion(0, 60, 0, 16, 63, 16, Material.DIRT)
        result.setRegion(0, 63, 0, 16, 64, 16, Material.GRASS_BLOCK)
        return result
    }

    override fun getDefaultPopulators(world: World) = listOf(ForceLoadPopulator())

    override fun getFixedSpawnLocation(world: World, random: Random) = Location(world, 0.0, 64.0, 0.0)

    override fun isParallelCapable() = true

}