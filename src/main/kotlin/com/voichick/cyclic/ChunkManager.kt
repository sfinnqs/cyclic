package com.voichick.cyclic

import net.jcip.annotations.GuardedBy
import net.jcip.annotations.ThreadSafe
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.min

@ThreadSafe
class ChunkManager(@Volatile var view: Int) {
    private val lock = ReentrantReadWriteLock()
    @GuardedBy("lock")
    private val toLoad = mutableSetOf<ChunkLocation>()
    @GuardedBy("lock")
    private val loaded = mutableSetOf<ChunkLocation>()
    @GuardedBy("lock")
    private val toUnload = mutableSetOf<ChunkLocation>()
    private val location = AtomicReference<ClientLocation?>()
    var clientLocation: ClientLocation?
        get() = location.get()
        set(value) {
            val oldValue = location.getAndSet(value)
            if (value?.clientChunkX == oldValue?.clientChunkX)
                if (value?.clientChunkZ == oldValue?.clientChunkZ)
                    return
            println("Moved chunks: (${oldValue?.clientChunkX}, ${oldValue?.clientChunkZ}) to (${value?.clientChunkX}, ${value?.clientChunkZ})")
            val neighbors = value?.getNeighbors(view)
            lock.write {
                if (neighbors == null) {
                    toLoad.clear()
                    loaded.clear()
                    toUnload.clear()
                    return
                }
                toLoad.retainAll(neighbors)
                toUnload.removeAll(neighbors)
                for (neighbor in neighbors)
                    if (neighbor !in loaded)
                        toLoad.add(neighbor)
                val loadedIterator = loaded.iterator()
                while (loadedIterator.hasNext()) {
                    val chunk = loadedIterator.next()
                    if (chunk !in neighbors) {
                        loadedIterator.remove()
                        toUnload.add(chunk)
                    }
                }
            }
        }

    val bestAction: ChunkAction?
        get() {
            val location = clientLocation ?: return null
            return lock.read {
                if (toLoad.isEmpty())
                    toUnload.map {
                        ChunkAction(ChunkAction.Type.UNLOAD, it, squaredDistance(it, location))
                    }.min()
                else
                    toLoad.map {
                        ChunkAction(ChunkAction.Type.LOAD, it, squaredDistance(it, location))
                    }.min()
            }
        }

    fun loadChunk(chunk: ChunkLocation) {
        lock.write {
            toLoad.remove(chunk)
            loaded.add(chunk)
            toUnload.remove(chunk)
        }
    }

    fun unloadChunk(chunk: ChunkLocation) {
        lock.write {
            toLoad.remove(chunk)
            loaded.remove(chunk)
            toUnload.remove(chunk)
        }
    }

    // loading or loaded
    fun isLoading(chunk: ChunkLocation): Boolean {
        lock.read {
            return chunk in loaded
        }
    }

    private fun squaredDistance(chunk: ChunkLocation, location: ClientLocation): Double {
        val chunkX = chunk.x
        val chunkZ = chunk.z
        val clientX = location.clientX
        val clientZ = location.clientZ
        val dx2 = if (location.clientChunkX == chunkX)
            0.0
        else
            min((clientX - 16 * chunkX).squared, (clientX - 16 * (chunkX + 1)).squared)
        val dz2 = if (location.clientChunkZ == chunkZ)
            0.0
        else
            min((clientZ - 16 * chunkZ).squared, (clientZ - 16 * (chunkZ + 1)).squared)
        return dx2 + dz2
    }

    private companion object {
        val Double.squared
            get() = this * this
    }
}