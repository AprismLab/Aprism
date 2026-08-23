package com.aprism.loader.contentbind;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Tests for {@link NetworkStackAdapter} against test-sourceset network
 * stubs (live-connection send lands with the transport discovery seam).
 */
class NetworkStackAdapterTest {

    @Test
    void buildsPayloadWithAprismTypedChannel() {
        NetworkStackAdapter adapter = new NetworkStackAdapter();
        NetworkStackAdapter.McPayload payload =
                adapter.buildPayload("sync_test", new byte[] {1, 2, 3});

        assertNotNull(payload);
        assertNotNull(payload.type());
        var type = (net.minecraft.network.protocol.common.custom
                .CustomPacketPayload.Type<?>) payload.type();
        assertEquals("aprism", type.id().getNamespace());
        assertEquals("sync_test", type.id().getPath());
    }

    @Test
    void payloadProxyImplementsMcInterface() {
        NetworkStackAdapter.McPayload payload =
                new NetworkStackAdapter().buildPayload("ping", new byte[0]);
        assertInstanceOf(net.minecraft.network.protocol.common.custom
                .CustomPacketPayload.class, payload.payload());
        assertSame(payload.type(),
                ((net.minecraft.network.protocol.common.custom.CustomPacketPayload)
                        payload.payload()).type());
    }

    @Test
    void rejectsNullInputs() {
        NetworkStackAdapter adapter = new NetworkStackAdapter();
        assertThrows(NullPointerException.class,
                () -> adapter.buildPayload(null, new byte[0]));
        assertThrows(NullPointerException.class,
                () -> adapter.buildPayload("ch", null));
    }
}
