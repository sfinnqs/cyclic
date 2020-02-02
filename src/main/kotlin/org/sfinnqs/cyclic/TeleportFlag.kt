package org.sfinnqs.cyclic

import com.comphenix.protocol.reflect.EquivalentConverter
import net.minecraft.server.v1_15_R1.PacketPlayOutPosition

enum class TeleportFlag {
    X, Y, Z, Y_ROT, X_ROT;

    object Converter : EquivalentConverter<TeleportFlag> {
        override fun getSpecific(generic: Any?) = when (generic) {
            PacketPlayOutPosition.EnumPlayerTeleportFlags.X -> X
            PacketPlayOutPosition.EnumPlayerTeleportFlags.Y -> Y
            PacketPlayOutPosition.EnumPlayerTeleportFlags.Z -> Z
            PacketPlayOutPosition.EnumPlayerTeleportFlags.Y_ROT -> Y_ROT
            PacketPlayOutPosition.EnumPlayerTeleportFlags.X_ROT -> X_ROT
            else -> null
        }

        override fun getSpecificType() = TeleportFlag::class.java

        override fun getGeneric(specific: TeleportFlag?) = when (specific) {
            null -> null
            X -> PacketPlayOutPosition.EnumPlayerTeleportFlags.X
            Y -> PacketPlayOutPosition.EnumPlayerTeleportFlags.Y
            Z -> PacketPlayOutPosition.EnumPlayerTeleportFlags.Z
            Y_ROT -> PacketPlayOutPosition.EnumPlayerTeleportFlags.Y_ROT
            X_ROT -> PacketPlayOutPosition.EnumPlayerTeleportFlags.X_ROT
        }
    }
}
