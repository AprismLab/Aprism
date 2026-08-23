package net.minecraft.network;

/**
 * Test-sourceset stub of MC FriendlyByteBuf (v26.7-Alpha.4). NOT shipped.
 *
 * <!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
 */
public class FriendlyByteBuf {

    private final byte[] data = new byte[0];

    public FriendlyByteBuf writeByteArray(byte[] bytes) {
        return this;
    }

    public byte[] readByteArray() {
        return data;
    }
}