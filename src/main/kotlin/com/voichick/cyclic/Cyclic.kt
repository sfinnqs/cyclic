package com.voichick.cyclic

import com.comphenix.protocol.ProtocolLibrary
import net.jcip.annotations.NotThreadSafe
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

@Suppress("unused")
@NotThreadSafe
class Cyclic : JavaPlugin() {

    val manager = PlayerManager(Bukkit.getViewDistance())
    val adapter = CyclicAdapter(this)

    override fun onEnable() {
        ProtocolLibrary.getProtocolManager().addPacketListener(adapter)
        val listener = CyclicListener(this)
        server.pluginManager.registerEvents(listener, this)
        ChunkTask(this).runTaskTimer(this, 0, 2)
        getCommand("unloadall")?.setExecutor(ReloadCommand(this))
    }

    override fun onDisable() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(this)
    }

    override fun getDefaultWorldGenerator(worldName: String, id: String?) = CyclicGenerator()

}