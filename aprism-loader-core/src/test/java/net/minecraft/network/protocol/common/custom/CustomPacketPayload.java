package net.minecraft.network.protocol.common.custom;

import net.minecraft.resources.Identifier;

/**
 * Test-sourceset stub of MC CustomPacketPayload (v26.7-Alpha.4). NOT shipped.
 *
 * <!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
 */
public interface CustomPacketPayload {

    Type<? extends CustomPacketPayload> type();

    static <T extends CustomPacketPayload> Type<T> createType(String combined) {
        int sep = combined.indexOf(':');
        return new Type<>(new Identifier(combined.substring(0, sep),
                combined.substring(sep + 1)));
    }

    record Type<T extends CustomPacketPayload>(Identifier id) {
    }
}