package com.voichick.cyclic

import net.jcip.annotations.Immutable
import java.util.*

@Immutable
data class Duplicate(val uuid: UUID, val offsetX: Int, val offsetZ: Int)