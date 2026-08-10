package com.aprism.loader.networking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.networking.NetworkDirection;
import com.aprism.api.networking.NetworkPacket;
import com.aprism.api.networking.NetworkTransport;
import com.aprism.api.networking.PacketChannel;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the networking API foundation (v26.3-Alpha.3, QA0 gap #4):
 * channel validation, listener delivery, fail-closed sends, transport
 * isolation, and the runtime wiring ({@code AprismRuntime.getNetworking()}).
 *
 * @author BlockConnect@StarsailsClover
 */
class NetworkingRegistryTest {

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    /** Loopback transport: accepts sends and records them; never delivers. */
    static final class LoopbackTransport implements NetworkTransport {
        private volatile boolean available = true;
        final List<NetworkPacket> sent = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean send(NetworkPacket packet, NetworkDirection direction) {
            sent.add(packet);
            return true;
        }

        @Override
        public void deliver(NetworkPacket packet, NetworkDirection direction) {
            // not used in loopback tests
        }
    }

    @Nested
    class ChannelRegistration {

        @Test
        void channelIdMustBeNamespaced() {
            assertThatThrownBy(() -> new PacketChannel("no-colon", ""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PacketChannel(":name", ""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PacketChannel("UPPER:name", ""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new PacketChannel("examplemod:sync", "").id())
                    .isEqualTo("examplemod:sync");
        }

        @Test
        void registerAndQueryChannel() {
            NetworkingRegistry registry = new NetworkingRegistry();
            PacketChannel channel = PacketChannel.of("examplemod:sync");

            registry.registerChannel(channel);

            assertThat(registry.isChannelRegistered("examplemod:sync")).isTrue();
            assertThat(registry.getChannelIds()).containsExactly("examplemod:sync");
        }

        @Test
        void duplicateChannelRejected() {
            NetworkingRegistry registry = new NetworkingRegistry();
            registry.registerChannel(PacketChannel.of("examplemod:sync"));

            assertThatThrownBy(() -> registry.registerChannel(PacketChannel.of("examplemod:sync")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }
    }

    @Nested
    class ListenerDelivery {

        @Test
        void listenersRequireRegisteredChannel() {
            NetworkingRegistry registry = new NetworkingRegistry();

            assertThatThrownBy(() -> registry.addListener("ghost:chan", (p, d) -> { }))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not registered");
        }

        @Test
        void deliverReachesAllListenersWithDirection() {
            NetworkingRegistry registry = new NetworkingRegistry();
            registry.registerChannel(PacketChannel.of("examplemod:sync"));
            List<String> seen = new ArrayList<>();
            registry.addListener("examplemod:sync",
                    (p, d) -> seen.add(d + ":" + new String(p.payload(), StandardCharsets.UTF_8)));
            registry.addListener("examplemod:sync",
                    (p, d) -> seen.add("second:" + d));

            NetworkPacket packet = new NetworkPacket(
                    PacketChannel.of("examplemod:sync"), "hello".getBytes(StandardCharsets.UTF_8));
            registry.deliver(packet, NetworkDirection.SERVER_TO_CLIENT);

            assertThat(seen).containsExactly(
                    "SERVER_TO_CLIENT:hello", "second:SERVER_TO_CLIENT");
        }

        @Test
        void throwingListenerIsIsolated() {
            NetworkingRegistry registry = new NetworkingRegistry();
            registry.registerChannel(PacketChannel.of("examplemod:sync"));
            List<String> seen = new ArrayList<>();
            registry.addListener("examplemod:sync", (p, d) -> {
                throw new RuntimeException("synthetic listener failure");
            });
            registry.addListener("examplemod:sync", (p, d) -> seen.add("reached"));

            registry.deliver(NetworkPacket.empty(PacketChannel.of("examplemod:sync")),
                    NetworkDirection.CLIENT_TO_SERVER);

            assertThat(seen).containsExactly("reached");
        }

        @Test
        void deliverToUnregisteredChannelIsIgnored() {
            NetworkingRegistry registry = new NetworkingRegistry();
            // Must not throw.
            registry.deliver(NetworkPacket.empty(PacketChannel.of("ghost:chan")),
                    NetworkDirection.SERVER_TO_CLIENT);
        }

        @Test
        void removedListenerStopsReceiving() {
            NetworkingRegistry registry = new NetworkingRegistry();
            registry.registerChannel(PacketChannel.of("examplemod:sync"));
            List<String> seen = new ArrayList<>();
            com.aprism.api.networking.NetworkListener listener = (p, d) -> seen.add("hit");
            registry.addListener("examplemod:sync", listener);
            registry.removeListener("examplemod:sync", listener);

            registry.deliver(NetworkPacket.empty(PacketChannel.of("examplemod:sync")),
                    NetworkDirection.SERVER_TO_CLIENT);

            assertThat(seen).isEmpty();
        }
    }

    @Nested
    class FailClosedSends {

        @Test
        void sendWithoutTransportRefused() {
            NetworkingRegistry registry = new NetworkingRegistry();
            registry.registerChannel(PacketChannel.of("examplemod:sync"));

            boolean accepted = registry.send(
                    NetworkPacket.empty(PacketChannel.of("examplemod:sync")),
                    NetworkDirection.CLIENT_TO_SERVER);

            assertThat(accepted).isFalse();
        }

        @Test
        void sendOnUnregisteredChannelRefused() {
            NetworkingRegistry registry = new NetworkingRegistry();
            registry.attachTransport(new LoopbackTransport());

            boolean accepted = registry.send(
                    NetworkPacket.empty(PacketChannel.of("ghost:chan")),
                    NetworkDirection.CLIENT_TO_SERVER);

            assertThat(accepted).isFalse();
        }

        @Test
        void sendWhileTransportUnavailableRefused() {
            NetworkingRegistry registry = new NetworkingRegistry();
            registry.registerChannel(PacketChannel.of("examplemod:sync"));
            LoopbackTransport transport = new LoopbackTransport();
            transport.available = false;
            registry.attachTransport(transport);

            boolean accepted = registry.send(
                    NetworkPacket.empty(PacketChannel.of("examplemod:sync")),
                    NetworkDirection.CLIENT_TO_SERVER);

            assertThat(accepted).isFalse();
            assertThat(transport.sent).isEmpty();
        }

        @Test
        void sendReachesTransportWhenAttachedAndAvailable() {
            NetworkingRegistry registry = new NetworkingRegistry();
            registry.registerChannel(PacketChannel.of("examplemod:sync"));
            LoopbackTransport transport = new LoopbackTransport();
            registry.attachTransport(transport);

            boolean accepted = registry.send(
                    new NetworkPacket(PacketChannel.of("examplemod:sync"),
                            "data".getBytes(StandardCharsets.UTF_8)),
                    NetworkDirection.CLIENT_TO_SERVER);

            assertThat(accepted).isTrue();
            assertThat(transport.sent).hasSize(1);
            assertThat(transport.sent.get(0).payloadLength()).isEqualTo(4);
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesNetworkingRegistry() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");

            NetworkingRegistry networking = runtime.getNetworking();
            assertThat(networking).isNotNull();
            networking.registerChannel(PacketChannel.of("examplemod:sync"));
            assertThat(networking.isChannelRegistered("examplemod:sync")).isTrue();
        }

        @Test
        void networkingClearedOnShutdown() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");
            NetworkingRegistry networking = runtime.getNetworking();
            networking.registerChannel(PacketChannel.of("examplemod:sync"));

            runtime.shutdown();

            assertThat(runtime.getNetworking().getChannelIds()).isEmpty();
            assertThat(runtime.getNetworking().getTransport()).isNull();
        }
    }
}
