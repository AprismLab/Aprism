package com.aprism.loader.networking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.networking.NetworkDirection;
import com.aprism.api.networking.NetworkListener;
import com.aprism.api.networking.NetworkPacket;
import com.aprism.api.networking.NetworkTransport;
import com.aprism.api.networking.PacketChannel;

/**
 * JUnit 5 + AssertJ tests for {@link NetworkTransportInstaller}
 * (v26.5-Alpha.8).
 *
 * @author BlockConnect@StarsailsClover
 */
class NetworkTransportInstallerTest {

    private NetworkingRegistry registry;
    private NetworkTransportInstaller installer;

    @BeforeEach
    void setUp() {
        registry = new NetworkingRegistry();
        installer = new NetworkTransportInstaller(registry);
    }

    private static class RecordingTransport implements NetworkTransport {
        final AtomicReference<NetworkPacket> sentPacket = new AtomicReference<>();
        volatile NetworkDirection sentDirection;

        @Override
        public boolean send(NetworkPacket packet, NetworkDirection direction) {
            sentPacket.set(packet);
            sentDirection = direction;
            return true;
        }

        @Override
        public void deliver(NetworkPacket packet, NetworkDirection direction) {
            // inbound delivery is handled by the registry, not the transport
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    @Nested
    class TransportAttachment {

        @Test
        void noTransportByDefault() {
            assertThat(installer.isTransportAttached()).isFalse();
        }

        @Test
        void setTransportAttaches() {
            installer.setTransport(new RecordingTransport());
            assertThat(installer.isTransportAttached()).isTrue();
        }

        @Test
        void setNullDetaches() {
            installer.setTransport(new RecordingTransport());
            installer.setTransport(null);
            assertThat(installer.isTransportAttached()).isFalse();
        }
    }

    @Nested
    class InboundDelivery {

        @Test
        void deliverInboundDeliversToListener() {
            PacketChannel channel = PacketChannel.of("mymod:custom");
            registry.registerChannel(channel);
            var received = new AtomicReference<NetworkPacket>();
            registry.addListener(channel.id(), (packet, direction) -> received.set(packet));

            installer.deliverInbound("mymod:custom", new byte[]{1, 2, 3},
                    NetworkDirection.SERVER_TO_CLIENT);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().payload()).containsExactly(1, 2, 3);
        }

        @Test
        void deliverInboundNullChannelIsNoOp() {
            installer.deliverInbound(null, new byte[0], NetworkDirection.CLIENT_TO_SERVER);
        }

        @Test
        void deliverInboundBlankChannelIsNoOp() {
            installer.deliverInbound("", new byte[0], NetworkDirection.CLIENT_TO_SERVER);
        }

        @Test
        void deliverInboundMalformedChannelIsDropped() {
            installer.deliverInbound("not-a-valid-channel", new byte[0],
                    NetworkDirection.CLIENT_TO_SERVER);
        }

        @Test
        void deliverInboundNullPayloadUsesEmptyArray() {
            PacketChannel channel = PacketChannel.of("mod:test");
            registry.registerChannel(channel);
            var received = new AtomicReference<NetworkPacket>();
            registry.addListener(channel.id(), (packet, direction) -> received.set(packet));

            installer.deliverInbound("mod:test", null, NetworkDirection.SERVER_TO_CLIENT);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().payload()).isEmpty();
        }

        @Test
        void deliverInboundUnregisteredChannelIsDropped() {
            // No listener registered, should not throw
            installer.deliverInbound("mymod:custom", new byte[]{1},
                    NetworkDirection.SERVER_TO_CLIENT);
        }
    }

    @Nested
    class ConstructorValidation {

        @Test
        void nullRegistryThrows() {
            try {
                new NetworkTransportInstaller(null);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
            }
        }
    }
}
