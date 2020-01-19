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
package com.voichick.cyclic.config

import com.voichick.cyclic.logger
import net.jcip.annotations.Immutable
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import java.util.*

@Immutable
class WorldsConfig(config: ConfigurationSection, server: Server) {
    private val names: Map<UUID, String>
    private val ids: Map<String, UUID>
    private val idWorlds: Map<UUID, WorldConfig>
    private val nameWorlds: Map<String, WorldConfig>
    private val default = config.getConfigurationSection("default")?.let {
        WorldConfig(it)
    } ?: defaultWorldConfig

    init {
        val tempNames = mutableMapOf<UUID, String>()
        val tempIds = mutableMapOf<String, UUID>()
        for (world in server.worlds) {
            val id = world.uid
            val name = world.name
            tempNames[id] = name
            tempIds[name] = id
        }
        val tempNameWorlds = mutableMapOf<String, WorldConfig>()
        val tempIdWorlds = mutableMapOf<UUID, WorldConfig>()
        for (name in config.getKeys(false)) {
            if (name == "default") continue
            if (!config.isConfigurationSection(name)) continue
            val section = config.getConfigurationSection(name)!!
            val idString = section.getString("id")
            val id = if (idString == null)
                tempIds[name]
            else
                try {
                    UUID.fromString(idString)
                } catch (e: IllegalArgumentException) {
                    logger.warning {
                        "Unrecognized UUID \"$idString\" in config"
                    }
                    null
                }
            val worldConfig = WorldConfig(section)
            tempNameWorlds[name] = worldConfig
            if (id != null) {
                tempNames.putIfAbsent(id, name)
                tempIdWorlds.putIfAbsent(id, worldConfig)
            }
        }
        names = tempNames
        ids = tempIds
        nameWorlds = tempNameWorlds
        idWorlds = tempIdWorlds
    }

    operator fun get(world: UUID) = idWorlds[world] ?: nameWorlds[names[world]]
    ?: default

    operator fun get(name: String) = nameWorlds[name] ?: idWorlds[ids[name]]
    ?: default

    operator fun get(world: World): WorldConfig {
        val id = world.uid
        val name = world.name
        assert(names[id] == name)
        assert(ids[name] == id)
        return idWorlds[id] ?: nameWorlds[name] ?: default
    }

    fun toMap(): Map<String, Map<String, Any>> {
        val result = mutableMapOf<String, Map<String, Any>>()
        for ((id, config) in idWorlds) {
            result[names.getValue(id)] = config.toMap(id)
        }
        for ((name, config) in nameWorlds) {
            if (name in result) continue
            result.computeIfAbsent(name) {
                config.toMap(ids[name])
            }
        }
        result["default"] = default.toMap()
        return result
    }

    private companion object {
        val defaultWorldConfig = WorldConfig(48, 48)
    }
}
