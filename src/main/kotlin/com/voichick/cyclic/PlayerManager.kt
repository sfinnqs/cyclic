package com.voichick.cyclic

import com.google.common.collect.MapMaker
import net.jcip.annotations.ThreadSafe
import org.bukkit.entity.Player
import java.lang.Math.min

@ThreadSafe
class PlayerManager(private val view: Int) {
    private val chunkManagers = MapMaker().weakKeys().makeMap<Player, ChunkManager>()
    val locations = Locations()
    val views = Views()
    val managers: Map<Player, ChunkManager>
        get() = chunkManagers

    inner class Locations internal constructor() {
        operator fun get(player: Player) = chunkManagers[player]?.clientLocation
        operator fun set(player: Player, location: ClientLocation?) {
            chunkManagers.getOrPut(player) {
                ChunkManager(view)
            }.clientLocation = location
        }
    }

    inner class Views internal constructor() {
        operator fun get(player: Player) = chunkManagers[player]?.view ?: view
        operator fun set(player: Player, view: Int) {
            val minView = min(view, this@PlayerManager.view)
            chunkManagers.getOrPut(player) {
                ChunkManager(minView)
            }.view = minView
        }
    }
}