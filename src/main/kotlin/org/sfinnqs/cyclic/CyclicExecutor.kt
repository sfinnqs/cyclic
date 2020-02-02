package org.sfinnqs.cyclic

import net.jcip.annotations.NotThreadSafe
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.sfinnqs.cyclic.manager.WorldManager
import org.sfinnqs.cyclic.world.WorldOffset
import java.util.concurrent.ThreadLocalRandom

@NotThreadSafe
class CyclicExecutor(private val manager: WorldManager) : TabExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) return false
        val offsetX = args.getOrNull(0)?.toInt() ?: 0
        val offsetZ = args.getOrNull(1)?.toInt() ?: 0
        manager.setOffsets(
            mapOf(sender to WorldOffset(offsetX, offsetZ)),
            sender.world
        )
        Bukkit.getScheduler().scheduleSyncDelayedTask(Bukkit.getPluginManager().getPlugin("Cyclic")!!) {
            sender.sendMessage("serverPos: ${sender.location}; offset: ${manager.getWorldAndOffset(sender)}")
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ) = emptyList<String>()
}
