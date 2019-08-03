package com.voichick.cyclic

import net.jcip.annotations.Immutable

@Immutable
data class LocationOffset(val deltaX: Short, val deltaY: Short, val deltaZ: Short)