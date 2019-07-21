package com.voichick.cyclic

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.generator.ChunkGenerator
import java.util.*

class CyclicGenerator : ChunkGenerator() {


    override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData {
        for (localX in 0..15)
            for (localZ in 0..15)
                biome.setBiome(localX, localZ, Biome.PLAINS)
        val result = createChunkData(world)
        if (x < 0 || x >= X_CHUNKS || z < 0 || z >= Z_CHUNKS)
            return result
        result.setRegion(0, 0, 0, 16, 1, 16, Material.BEDROCK)
        result.setRegion(0, 1, 0, 16, 60, 16, Material.STONE)
        result.setRegion(0, 60, 0, 16, 63, 16, Material.DIRT)
        result.setRegion(0, 63, 0, 16, 64, 16, Material.GRASS_BLOCK)
        for (localX in 0..15)
            for (localZ in 0..15) {
                if (random.nextInt(100) > 0)
                    continue
                result.setBlock(localX, 63, localZ, Material.DIRT)
                result.setRegion(localX, 64, localZ, localX + 1, 65 + random.nextInt(3), localZ + 1, concretes[random.nextInt(concretes.size)])
            }
        return result
    }

    override fun getDefaultPopulators(world: World) = listOf(ForceLoadPopulator())

    override fun getFixedSpawnLocation(world: World, random: Random) = Location(world, 0.0, 4.0, 0.0)

    override fun isParallelCapable() = true

    companion object {
        val concretes = arrayOf(Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE, Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE)
    }

}