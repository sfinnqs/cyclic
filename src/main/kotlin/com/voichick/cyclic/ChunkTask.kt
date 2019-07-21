package com.voichick.cyclic

import com.comphenix.protocol.PacketType.Play.Server.MAP_CHUNK
import com.comphenix.protocol.PacketType.Play.Server.UNLOAD_CHUNK
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.wrappers.nbt.NbtFactory
import io.netty.buffer.Unpooled
import net.jcip.annotations.NotThreadSafe
import net.minecraft.server.v1_14_R1.*
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.lang.Math.floorMod

@NotThreadSafe
class ChunkTask(private val plugin: Cyclic) : BukkitRunnable() {
    override fun run() {
        val (player, bestAction) = plugin.manager.managers.mapValues {
            it.value.bestAction
        }.filterValuesNotNull().minBy {
            it.value
        } ?: return
        when (bestAction.type) {
            ChunkAction.Type.LOAD -> loadChunk(bestAction.chunk, player)
            ChunkAction.Type.UNLOAD -> unloadChunk(bestAction.chunk, player)
        }
    }

    private fun loadChunk(chunk: ChunkLocation, player: Player) {
        val clientChunkX = chunk.x
        val clientChunkZ = chunk.z
        val protocolManager = ProtocolLibrary.getProtocolManager()
        val packet = protocolManager.createPacket(MAP_CHUNK, true)
        val ints = packet.integers
        ints.write(0, clientChunkX)
        ints.write(1, clientChunkZ)
        val sourceX = floorMod(clientChunkX, X_CHUNKS)
        val sourceZ = floorMod(clientChunkZ, Z_CHUNKS)

        packet.booleans?.write(0, true) // full chunk
        val mcChunk = (player.world as? CraftWorld)?.handle?.chunkProvider?.getChunkAt(sourceX, sourceZ, true)
                ?: throw IllegalStateException("Unable to get chunk data")
        val buffer = Unpooled.buffer(1024)
                ?: throw IllegalStateException("Unable to create buffer")
        val newPacket = PacketPlayOutMapChunk(mcChunk, 65535)
        val serializer = PacketDataSerializer(buffer)
        newPacket.a(serializer, mcChunk, 65535)
        val data = ByteArray(serializer.readableBytes())
        serializer.readBytes(data)
        val nbt = NBTTagCompound()
        mcChunk.f().forEach {
            val key = it.key
            if (key.b())
                nbt.set(key.a(), NBTTagLongArray(it.value.a()))
        }
        packet.byteArrays?.write(0, data)
        val wrappedNbt = NbtFactory.fromNMSCompound(nbt)
        packet.nbtModifier?.write(0, wrappedNbt)
        packet.integers?.write(2, mcChunk.mask)

        plugin.adapter.allowPacket(packet)
        plugin.manager.managers[player]?.loadChunk(chunk)
        protocolManager.sendServerPacket(player, packet)
    }

    private fun unloadChunk(chunk: ChunkLocation, player: Player) {
        val protocolManager = ProtocolLibrary.getProtocolManager()
        val packet = protocolManager.createPacket(UNLOAD_CHUNK, true)
        packet.integers?.write(0, chunk.x)?.write(1, chunk.z)
        plugin.adapter.allowPacket(packet)
        plugin.manager.managers[player]?.unloadChunk(chunk)
        protocolManager.sendServerPacket(player, packet)
    }

    private companion object {
        val Chunk.mask: Int
            get() {
                var result = 0
                sections?.forEachIndexed { index, section ->
                    if (section != null && !section.c()) // empty
                        result = result or (1 shl index)
                }
                return result
            }

        fun <K, V> Map<K, V?>.filterValuesNotNull() = this.filterValues { it != null }.mapValues { it.value!! }
    }
}