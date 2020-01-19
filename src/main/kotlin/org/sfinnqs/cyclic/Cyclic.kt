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
package org.sfinnqs.cyclic

import com.comphenix.protocol.ProtocolLibrary
import org.sfinnqs.cyclic.config.CyclicConfig
import org.sfinnqs.cyclic.gen.CyclicGenerator
import net.jcip.annotations.NotThreadSafe
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin

@NotThreadSafe
class Cyclic : JavaPlugin() {

    lateinit var cyclicConfig: CyclicConfig
    val manager = WorldManager()

    override fun onLoad() {
        org.sfinnqs.cyclic.logger = logger
        reload()
    }

    override fun onEnable() {
        server.pluginManager.registerEvents(CyclicListener(this), this)
        ProtocolLibrary.getProtocolManager().addPacketListener(CyclicAdapter(this))
    }

    override fun getDefaultWorldGenerator(worldName: String, id: String?) = CyclicGenerator(cyclicConfig.worlds[worldName])

    fun reload() {
        saveDefaultConfig()
        reloadConfig()
        cyclicConfig = CyclicConfig(config, server)
        for (world in server.worlds) {
            val generator = world.generator as? CyclicGenerator
                    ?: continue
            generator.config = cyclicConfig.worlds[world]
        }
        writeConfigToFile()
    }

    private fun writeConfigToFile() {
        config.setAll(cyclicConfig.toMap())
        saveConfig()
    }

    private companion object {
        fun ConfigurationSection.setAll(map: Map<String, Any>) {
            for (key in map.keys + getKeys(false))
                this[key] = map[key]
        }
    }

}
