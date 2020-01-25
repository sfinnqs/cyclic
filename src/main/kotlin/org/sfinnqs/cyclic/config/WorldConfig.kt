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
package org.sfinnqs.cyclic.config

import kotlinx.collections.immutable.persistentMapOf
import net.jcip.annotations.Immutable
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.InvalidConfigurationException
import org.sfinnqs.cyclic.logger

@Immutable
data class WorldConfig(val maxX: Int, val maxZ: Int) {

    constructor(config: ConfigurationSection, default: WorldConfig) : this(
        getMaxX(config, default),
        getMaxZ(config, default)
    )

    init {
        if (maxX <= 0 || !maxX.isDivisibleBy16)
            throw InvalidConfigurationException("maxX must be a positive multiple of 16")
        if (maxZ <= 0 || !maxX.isDivisibleBy16)
            throw InvalidConfigurationException("maxZ must be a positive multiple of 16")
    }

    val xChunks = maxX / 16
    val zChunks = maxZ / 16

    fun toMap(): Map<String, Any> =
        persistentMapOf("max x" to maxX, "max z" to maxZ)

    private companion object {

        fun getMaxX(config: ConfigurationSection, default: WorldConfig): Int {
            val defaultX = default.maxX
            val result = config.getInt("max x", defaultX)
            if (result <= 0) {
                logger.warning {
                    "max x must be positive but was $result; changing to $defaultX"
                }
                return defaultX
            }
            if (!result.isDivisibleBy16) {
                val rounded = result.roundTo16()
                logger.warning {
                    "max x must be a multiple of 16 but was $result; changing to $rounded"
                }
                return rounded
            }
            return result
        }

        fun getMaxZ(config: ConfigurationSection, default: WorldConfig): Int {
            val defaultZ = default.maxZ
            val result = config.getInt("max z", defaultZ)
            if (result <= 0) {
                logger.warning {
                    "max z must be positive but was $result; changing to $defaultZ"
                }
                return defaultZ
            }
            if (!result.isDivisibleBy16) {
                val rounded = result.roundTo16()
                logger.warning {
                    "max z must be a multiple of 16 but was $result; changing to $rounded"
                }
                return rounded
            }
            return result
        }

        val Int.isDivisibleBy16
            get() = this and 0xf == 0

        fun Int.roundTo16(): Int {
            assert(this > 0)
            val roundDown = this / 16 * 16
            val roundUp = (this + 15) / 16 * 16
            val thisDouble = this.toDouble()
            return if (roundUp / thisDouble < thisDouble / roundDown)
                roundUp
            else
                roundDown
        }

    }

}
