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
package org.sfinnqs.cyclic.gen

import org.sfinnqs.cyclic.config.WorldConfig
import net.jcip.annotations.ThreadSafe
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
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.cos
import kotlin.math.sin

@ThreadSafe
class CyclicGenerator(config: WorldConfig) : ChunkGenerator() {

    private val lock = ReentrantReadWriteLock()
    var config = config
        get() = lock.read { field }
        set(value) = lock.write { field = value }


    override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData {
        for (localX in 0..15)
            for (localZ in 0..15)
                biome.setBiome(localX, localZ, Biome.PLAINS)
        val result = createChunkData(world)
        if (x !in 0 until config.xChunks || z !in 0 until config.zChunks)
            return result
        result.setRegion(0, 0, 0, 16, 1, 16, BEDROCK)
        val noise = SimplexNoiseGenerator(world)
        for (localX in 0..15) {
            val worldX = x * 16 + localX
            for (localZ in 0..15) {
                val worldZ = z * 16 + localZ
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

    override fun getDefaultPopulators(world: World) = listOf(TreePopulator(config))

    override fun getFixedSpawnLocation(world: World, random: Random) = Location(world, 0.0, 64.0, 0.0)

    override fun isParallelCapable() = true

    private fun getHeight(noise: SimplexNoiseGenerator, x: Int, z: Int): Double {
        val maxX = config.maxX
        val maxZ = config.maxZ
        val angle1 = floorMod(x, maxX) * 2.0 * PI / maxX
        val angle2 = floorMod(z, maxZ) * 2.0 * PI / maxZ
        val scale = 0.2
        val value = noise.noise(scale * cos(angle1), scale * sin(angle1), scale * cos(angle2), scale * sin(angle2))
        return WATER_LEVEL + 2.0 + 10.0 * value
    }

    private companion object {
        const val WATER_LEVEL = 64
    }

}
