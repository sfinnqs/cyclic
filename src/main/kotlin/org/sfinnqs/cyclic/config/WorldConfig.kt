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

import kotlinx.collections.immutable.toImmutableMap
import net.jcip.annotations.Immutable
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.InvalidConfigurationException
import java.util.*

@Immutable
data class WorldConfig(val maxX: Int, val maxZ: Int) {

    constructor(config: ConfigurationSection) : this(config.maxX, config.maxZ)

    init {
        if (maxX <= 0 || !maxX.isDivisibleBy16)
            throw InvalidConfigurationException("maxX must be a positive multiple of 16")
        if (maxZ <= 0 || !maxX.isDivisibleBy16)
            throw InvalidConfigurationException("maxZ must be a positive multiple of 16")
    }

    val xChunks = maxX / 16
    val zChunks = maxZ / 16

    fun toMap(world: UUID? = null): Map<String, Any> {
        val result = mutableMapOf<String, Any>("max x" to maxX, "max z" to maxZ)
        if (world != null)
            result["id"] = world.toString()
        return result.toImmutableMap()
    }

    private companion object {

        val ConfigurationSection.maxX
            get() = this.getInt("max x", 48)

        val ConfigurationSection.maxZ
            get() = this.getInt("max z", 48)

        val Int.isDivisibleBy16
            get() = this and 0xf == 0

    }

}
