package org.sfinnqs.cyclic

import net.jcip.annotations.NotThreadSafe
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.sfinnqs.cyclic.manager.WorldManager
import org.sfinnqs.cyclic.world.WorldOffset
import java.util.concurrent.ThreadLocalRandom

@NotThreadSafe
class CyclicExecutor(private val manager: WorldManager): TabExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) return false
        val random = ThreadLocalRandom.current()
        val randomX = if (random.nextBoolean()) -1 else 1
        val randomZ = if (random.nextBoolean()) -1 else 1
        manager.setOffsets(mapOf(sender to WorldOffset(randomX, randomZ)), sender.world)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ) = emptyList<String>()
}
