package com.voichick.cyclic

import com.comphenix.protocol.ProtocolLibrary
import com.voichick.cyclic.gen.CyclicGenerator
import net.jcip.annotations.NotThreadSafe
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
@NotThreadSafe
class Cyclic : JavaPlugin() {

    val manager = VisibilityManager()

    override fun onEnable() {
        server.pluginManager.registerEvents(CyclicListener(this), this)
        ProtocolLibrary.getProtocolManager().addPacketListener(CyclicAdapter(this))
    }

    override fun getDefaultWorldGenerator(worldName: String, id: String?) = CyclicGenerator()

}