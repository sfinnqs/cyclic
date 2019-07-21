package com.voichick.cyclic

import java.util.*

class QueueSet<E> : Queue<E>, Set<E> {

    private val queue = LinkedList<E>()
    private val set = mutableSetOf<E>()

    override fun addAll(elements: Collection<E>): Boolean {
        var result = false
        for (element in elements)
            if (add(element))
                result = true
        return result
    }

    override fun clear() {
        queue.clear()
        set.clear()
    }

    override fun iterator() = queue.iterator()

    override fun removeAll(elements: Collection<E>) = if (set.removeAll(elements))
        queue.removeAll(elements)
    else
        false

    override fun retainAll(elements: Collection<E>) = if (set.retainAll(elements))
        queue.retainAll(elements)
    else
        false

    override val size
        get() = set.size

    override fun contains(element: E) = set.contains(element)

    override fun element(): E = queue.element()

    override fun containsAll(elements: Collection<E>) = set.containsAll(elements)

    override fun isEmpty() = set.isEmpty()

    override fun remove(): E {
        val result = queue.remove()
        set.remove(result)
        return result
    }

    override fun remove(element: E) = if (set.remove(element))
        queue.remove(element)
    else
        false

    override fun add(element: E) = if (set.add(element))
        queue.add(element)
    else
        false

    override fun offer(e: E) = if (set.add(e))
        queue.offer(e)
    else
        false

    override fun peek(): E = queue.peek()

    override fun poll(): E? {
        val result = queue.poll() ?: return null
        set.remove(result)
        return result
    }

    override fun equals(other: Any?) = set == other

    override fun hashCode() = set.hashCode()

}