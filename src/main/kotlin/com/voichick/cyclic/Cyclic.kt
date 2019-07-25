package com.voichick.cyclic

import net.jcip.annotations.NotThreadSafe
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
@NotThreadSafe
class Cyclic : JavaPlugin() {

    override fun onEnable() {
        server.pluginManager.registerEvents(CyclicListener(this), this)
    }

    override fun getDefaultWorldGenerator(worldName: String, id: String?) = CyclicGenerator()

}