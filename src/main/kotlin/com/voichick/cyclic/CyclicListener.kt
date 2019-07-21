package com.voichick.cyclic

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN

class CyclicListener(private val plugin: Cyclic) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val location = player.location
        plugin.manager.locations[player] = ClientLocation(location.x, location.z)
    }

//    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
//    fun onPlayerTeleport(event: PlayerTeleportEvent) {
//        val to = event.to ?: return
//        plugin.manager.locations[event.player] = ClientLocation(to.x, to.z)
//    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.manager.locations[event.player] = null
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockPlace(event: BlockPlaceEvent) {
        plugin.logger.info {
            "Event cancelled? ${event.isCancelled}"
        }
    }
}