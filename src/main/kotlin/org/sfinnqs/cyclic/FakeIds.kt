/**
 * Cyclic - A Bukkit plugin for worlds that wrap around
 * Copyright (C) 2020 sfinnqs
 *
 * This file is part of Cyclic.
 *
 * Cyclic is free software; you can redistribute it and/or modify it under the
 * terms of version 3 of the GNU Affero General Public License as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see <https://www.gnu.org/licenses>.
 *
 * Additional permission under GNU AGPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with the "Minecraft: Java Edition" server (or a modified version of the
 * "Minecraft: Java Edition" server), containing parts covered by the terms of
 * the Minecraft End-User Licence Agreement, the licensor of Cyclic grants you
 * additional permission to support user interaction over a network with the
 * resulting work. In this case, you are still required to provide access to the
 * Corresponding Source of your version (under GNU AGPL version 3 section 13)
 * but you may omit source code from the "Minecraft: Java Edition" server from
 * the available Corresponding Source.
 */
package org.sfinnqs.cyclic

import net.jcip.annotations.NotThreadSafe

@NotThreadSafe
class FakeIds {
    private val entityList = mutableListOf<FakeEntity?>()
    private val entityIds = mutableMapOf<FakeEntity, Int>()

    operator fun get(fake: FakeEntity) = entityIds[fake]

    fun getOrCreate(fake: FakeEntity): Int {
        val existingId = entityIds[fake]
        if (existingId != null) return existingId
        for (i in 0..entityList.size) {
            val storedDuplicate = entityList.getOrNull(i)
            if (storedDuplicate == null) {
                if (i == entityList.size)
                    entityList.add(fake)
                else
                    entityList[i] = fake
                val entityId = Int.MAX_VALUE - i
                entityIds[fake] = entityId
                return entityId
            }
        }
        // TODO more elegant way?
        throw AssertionError()
    }

    fun remove(fake: FakeEntity): Boolean {
        val eid = entityIds.remove(fake) ?: return false
        val formerFake = entityList.set(Int.MAX_VALUE - eid, null)
        assert(formerFake == fake)
        return true
    }
}
