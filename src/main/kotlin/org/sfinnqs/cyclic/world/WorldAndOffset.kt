package org.sfinnqs.cyclic.world

import net.jcip.annotations.Immutable

@Immutable
data class WorldAndOffset(val world: CyclicWorld, val offset: WorldOffset)
