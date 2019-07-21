package com.voichick.cyclic

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ReloadCommand(private val plugin: Cyclic) : CommandExecutor {

    @Suppress("RedundantLambdaArrow")
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player)
            return false
        sender.sendMessage(sender.location.toString())

//        val locations = plugin.manager.locations
//        val loc = locations[sender]
//        locations[sender] = null
//        plugin.server.scheduler.runTaskLaterAsynchronously(plugin, { -> locations[sender] = loc }, 100)
        return true
    }

}