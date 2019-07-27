package com.voichick.cyclic

import com.comphenix.protocol.reflect.EquivalentConverter
import net.minecraft.server.v1_14_R1.PacketPlayOutPosition

enum class TeleportFlag {
    X, Y, Z, Y_ROT, X_ROT;

    object Converter: EquivalentConverter<TeleportFlag> {
        override fun getSpecific(generic: Any?): TeleportFlag? {
            if (generic !is PacketPlayOutPosition.EnumPlayerTeleportFlags)
                return null
            return when(generic) {
                PacketPlayOutPosition.EnumPlayerTeleportFlags.X -> X
                PacketPlayOutPosition.EnumPlayerTeleportFlags.Y -> Y
                PacketPlayOutPosition.EnumPlayerTeleportFlags.Z -> Z
                PacketPlayOutPosition.EnumPlayerTeleportFlags.Y_ROT -> Y_ROT
                PacketPlayOutPosition.EnumPlayerTeleportFlags.X_ROT -> X_ROT
            }
        }

        override fun getSpecificType(): Class<TeleportFlag> {
            return TeleportFlag::class.java
        }

        override fun getGeneric(specific: TeleportFlag?): Any? {
            if (specific == null)
                return null
            return when(specific) {
                X -> PacketPlayOutPosition.EnumPlayerTeleportFlags.X
                Y -> PacketPlayOutPosition.EnumPlayerTeleportFlags.Y
                Z -> PacketPlayOutPosition.EnumPlayerTeleportFlags.Z
                Y_ROT -> PacketPlayOutPosition.EnumPlayerTeleportFlags.Y_ROT
                X_ROT -> PacketPlayOutPosition.EnumPlayerTeleportFlags.X_ROT
            }
        }
    }
}

