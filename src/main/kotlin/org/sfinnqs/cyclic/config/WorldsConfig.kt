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

@Immutable
class WorldsConfig(config: ConfigurationSection) {
    private val worlds: Map<String, WorldConfig>
    private val default: WorldConfig

    init {
        val defaultSection = config.getConfigurationSection("default")
        default = if (defaultSection == null)
            defaultWorldConfig
        else
            WorldConfig(defaultSection, defaultWorldConfig)

        val tempWorlds = mutableMapOf<String, WorldConfig>()
        for (name in config.getKeys(false)) {
            if (name == "default") continue
            val section = config.getConfigurationSection(name) ?: continue
            tempWorlds[name] = WorldConfig(section, default)
        }
        worlds = tempWorlds.toImmutableMap()
    }

    operator fun get(name: String) = worlds[name] ?: default

    fun toMap(): Map<String, Any> {
        val result = mutableMapOf<String, Map<String, Any>>()
        for ((name, config) in worlds)
            result[name] = config.toMap()
        result["default"] = default.toMap()
        return result.toImmutableMap()
    }

    private companion object {
        val defaultWorldConfig = WorldConfig(48, 48)
    }
}
