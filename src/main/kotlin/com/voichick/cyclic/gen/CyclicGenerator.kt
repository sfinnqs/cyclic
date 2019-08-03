package com.voichick.cyclic.gen

import com.voichick.cyclic.MAX_X
import com.voichick.cyclic.MAX_Z
import com.voichick.cyclic.X_CHUNKS
import com.voichick.cyclic.Z_CHUNKS
import org.bukkit.Location
import org.bukkit.Material.*
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.generator.ChunkGenerator
import org.bukkit.util.NumberConversions.round
import org.bukkit.util.noise.SimplexNoiseGenerator
import java.lang.Math.PI
import java.lang.Math.floorMod
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class CyclicGenerator : ChunkGenerator() {

    override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData {
        for (localX in 0..15)
            for (localZ in 0..15)
                biome.setBiome(localX, localZ, Biome.PLAINS)
        val result = createChunkData(world)
        if (x !in 0 until X_CHUNKS || z !in 0 until Z_CHUNKS)
            return result
        result.setRegion(0, 0, 0, 16, 1, 16, BEDROCK)
        val noise = SimplexNoiseGenerator(world)
        for (localX in 0..15) {
            val worldX = (x shl 4) + localX
            for (localZ in 0..15) {
                val worldZ = (z shl 4) + localZ
                val rawHeight = getHeight(noise, worldX, worldZ)
                val height = round(rawHeight)
                result.setRegion(localX, 1, localZ, localX + 1, height - 4, localZ + 1, STONE)
                if (rawHeight < WATER_LEVEL + 1) {
                    result.setRegion(localX, height - 4, localZ, localX + 1, height, localZ + 1, SAND)
                    if (height < WATER_LEVEL)
                        result.setRegion(localX, height, localZ, localX + 1, 64, localZ + 1, WATER)
                } else {
                    result.setRegion(localX, height - 4, localZ, localX + 1, height - 1, localZ + 1, DIRT)
                    result.setBlock(localX, height - 1, localZ, GRASS_BLOCK)
                }
            }
        }
        return result
    }

    override fun getDefaultPopulators(world: World) = listOf(TreePopulator())

    override fun getFixedSpawnLocation(world: World, random: Random) = Location(world, 0.0, 64.0, 0.0)

    override fun isParallelCapable() = true

    private fun getHeight(noise: SimplexNoiseGenerator, x: Int, z: Int): Double {
        val angle1 = floorMod(x, MAX_X) * 2.0 * PI / MAX_X
        val angle2 = floorMod(z, MAX_Z) * 2.0 * PI / MAX_Z
        val scale = 0.2
        val value = noise.noise(scale * cos(angle1), scale * sin(angle1), scale * cos(angle2), scale * sin(angle2))
        return WATER_LEVEL + 2.0 + 10.0 * value
    }

    private companion object {
        const val WATER_LEVEL = 64
    }

}