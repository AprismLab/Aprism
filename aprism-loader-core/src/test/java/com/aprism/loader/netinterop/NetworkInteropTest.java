package com.aprism.loader.netinterop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import com.aprism.api.networking.NetworkDirection;

import org.junit.jupiter.api.Test;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Network interop tests (v26.9-Alpha.6): codec registry limits, envelope
 * immutability, bidirectional negotiation (common protocol/channel
 * intersection, min-limit semantics, fail-closed codes), and the session
 * state machine including send/receive refusals.
 *
 * @author BlockConnect@StarsailsClover
 */
class NetworkInteropTest {

    /** UTF-8 string codec used by the fixtures. */
    private static final NetworkCodec<String> STRING_CODEC = new NetworkCodec<>() {
        @Override
        public String id() {
            return "aprism:string";
        }

        @Override
        public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(byte[] payload) {
            return new String(payload, StandardCharsets.UTF_8);
        }
    };

    /** A codec that must never be invoked: proves inbound limit pre-checks. */
    private static final NetworkCodec<String> TRIPWIRE_CODEC = new NetworkCodec<>() {
        @Override
        public String id() {
            return "aprism:tripwire";
        }

        @Override
        public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(byte[] payload) {
            throw new AssertionError("decode must not run past the limit check");
        }
    };

    private static CodecRegistry registry(NetworkCodec<String> codec, int maxPayload) {
        CodecRegistry registry = new CodecRegistry();
        registry.register(new CodecRegistry.ChannelSpec("test:chan", codec.id(),
                maxPayload), codec);
        return registry;
    }

    @Test
    void envelopeCopiesPayloadAndValidates() {
        byte[] source = {1, 2, 3};
        NetworkEnvelope envelope = new NetworkEnvelope(1, "a",
                NetworkDirection.CLIENT_TO_SERVER, "test:chan", null, source);
        source[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, envelope.payload(),
                "constructor must copy the payload");
        byte[] read = envelope.payload();
        read[1] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, envelope.payload(),
                "accessor must return a copy");
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkEnvelope(0, "a",
                        NetworkDirection.CLIENT_TO_SERVER, "test:chan", null,
                        new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkEnvelope(1, "a",
                        NetworkDirection.CLIENT_TO_SERVER, "noNamespace", null,
                        new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkEnvelope(1, "a",
                        NetworkDirection.CLIENT_TO_SERVER, "test:chan", null,
                        new byte[NetworkEnvelope.DEFAULT_MAX_PAYLOAD + 1]));
    }

    @Test
    void codecRegistryEnforcesLimitsBothWays() {
        CodecRegistry registry = registry(STRING_CODEC, 4);
        assertArrayEquals("ok".getBytes(StandardCharsets.UTF_8),
                registry.encode("test:chan", "ok"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.encode("test:chan", "too long"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.decode("test:chan", new byte[5]));
        assertThrows(IllegalArgumentException.class,
                () -> registry.encode("test:unknown", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new CodecRegistry.ChannelSpec("test:chan", "aprism:string", 0));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new CodecRegistry.ChannelSpec("test:chan",
                        "aprism:other", 1), STRING_CODEC),
                "codec id mismatch and duplicate channel both fail closed");
        // The inbound limit must trip before user decode code runs.
        CodecRegistry tripwire = registry(TRIPWIRE_CODEC, 2);
        assertThrows(IllegalArgumentException.class,
                () -> tripwire.decode("test:chan", new byte[3]));
    }

    private static TransportDiscovery twoSides(int localMax, int remoteMax,
            int remoteProtocol) {
        TransportDiscovery discovery = new TransportDiscovery();
        discovery.advertise(new TransportAdvertisement("client", Set.of(1, 2),
                Map.of("test:chan", new CodecRegistry.ChannelSpec("test:chan",
                        "aprism:string", localMax)), 64));
        discovery.advertise(new TransportAdvertisement("server",
                Set.of(remoteProtocol),
                Map.of("test:chan", new CodecRegistry.ChannelSpec("test:chan",
                        "aprism:string", remoteMax)), 32));
        return discovery;
    }

    @Test
    void negotiationUsesCommonProtocolAndMinLimits() {
        TransportDiscovery discovery = twoSides(16, 8, 2);
        TransportDiscovery.NegotiatedTransport negotiated =
                discovery.negotiate("client", "server");
        assertEquals(2, negotiated.protocol());
        assertEquals(Set.of("test:chan"), negotiated.channels());
        assertEquals(8, negotiated.channelLimit("test:chan"));
        assertEquals(32, negotiated.frameLimit());
        assertTrue(negotiated.toJson().contains("\"protocol\":2"));
    }

    @Test
    void negotiationFailsClosedWithoutCommonGround() {
        TransportDiscovery discovery = twoSides(8, 8, 9);
        TransportDiscovery.HandshakeException noProtocol =
                assertThrows(TransportDiscovery.HandshakeException.class,
                        () -> discovery.negotiate("client", "server"));
        assertEquals(TransportDiscovery.Code.NO_COMMON_PROTOCOL, noProtocol.code());

        TransportDiscovery disjoint = new TransportDiscovery();
        disjoint.advertise(new TransportAdvertisement("client", Set.of(1),
                Map.of("test:a", new CodecRegistry.ChannelSpec("test:a",
                        "aprism:string", 8)), 8));
        disjoint.advertise(new TransportAdvertisement("server", Set.of(1),
                Map.of("test:b", new CodecRegistry.ChannelSpec("test:b",
                        "aprism:string", 8)), 8));
        TransportDiscovery.HandshakeException noChannel =
                assertThrows(TransportDiscovery.HandshakeException.class,
                        () -> disjoint.negotiate("client", "server"));
        assertEquals(TransportDiscovery.Code.NO_COMMON_CHANNEL, noChannel.code());

        assertThrows(TransportDiscovery.HandshakeException.class,
                () -> disjoint.negotiate("client", "ghost"));
        assertTrue(disjoint.sides().contains("client"));
        disjoint.withdraw("client");
        assertFalse(disjoint.sides().contains("client"));
    }

    @Test
    void sessionStateMachineAndRefusals() {
        CodecRegistry codecs = registry(STRING_CODEC, 4);
        TransportDiscovery discovery = twoSides(4, 4, 1);
        NetworkSession session = new NetworkSession("client", codecs);
        assertEquals(NetworkSession.State.DISCONNECTED, session.state());
        assertThrows(IllegalStateException.class, session::beginReconnect);
        // Send before establishment is refused with a stable code.
        assertThrows(NetworkSession.SendRefusedException.class,
                () -> session.send(NetworkDirection.CLIENT_TO_SERVER, "test:chan", "x"));

        session.negotiate(discovery, "server");
        assertEquals(NetworkSession.State.ESTABLISHED, session.state());
        NetworkEnvelope out = session.send(NetworkDirection.CLIENT_TO_SERVER,
                "test:chan", "hi");
        assertEquals("hi", codecs.decode("test:chan", out.payload()));

        // Oversize send and un-negotiated channel both fail closed.
        NetworkSession.SendRefusedException tooLarge =
                assertThrows(NetworkSession.SendRefusedException.class,
                        () -> session.send(NetworkDirection.CLIENT_TO_SERVER,
                                "test:chan", "much too long"));
        assertEquals(NetworkSession.Refusal.PAYLOAD_TOO_LARGE, tooLarge.refusal());
        NetworkSession.SendRefusedException unknown =
                assertThrows(NetworkSession.SendRefusedException.class,
                        () -> session.send(NetworkDirection.CLIENT_TO_SERVER,
                                "test:other", "x"));
        assertEquals(NetworkSession.Refusal.CHANNEL_NOT_NEGOTIATED, unknown.refusal());

        // Round-trip receive works, wrong protocol is refused.
        assertEquals("hi", session.receive(out));
        NetworkEnvelope wrongProtocol = new NetworkEnvelope(2, "server",
                NetworkDirection.SERVER_TO_CLIENT, "test:chan", null,
                "hi".getBytes(StandardCharsets.UTF_8));
        assertEquals(NetworkSession.Refusal.PROTOCOL_MISMATCH,
                assertThrows(NetworkSession.SendRefusedException.class,
                        () -> session.receive(wrongProtocol)).refusal());

        session.beginReconnect();
        assertEquals(NetworkSession.State.NEGOTIATING, session.state());
        assertEquals(NetworkSession.Refusal.NOT_ESTABLISHED,
                assertThrows(NetworkSession.SendRefusedException.class,
                        () -> session.send(NetworkDirection.CLIENT_TO_SERVER,
                                "test:chan", "x")).refusal());
        session.negotiate(discovery, "server");
        session.close();
        assertEquals(NetworkSession.State.CLOSED, session.state());
        assertNull(session.negotiated());
        assertEquals(NetworkSession.Refusal.CLOSED,
                assertThrows(NetworkSession.SendRefusedException.class,
                        () -> session.send(NetworkDirection.CLIENT_TO_SERVER,
                                "test:chan", "x")).refusal());
        assertEquals(NetworkSession.Refusal.CLOSED,
                assertThrows(NetworkSession.SendRefusedException.class,
                        () -> session.receive(out)).refusal());
    }

    @Test
    void failedHandshakeClosesSession() {
        TransportDiscovery discovery = twoSides(4, 4, 42);
        NetworkSession session = new NetworkSession("client", registry(STRING_CODEC, 4));
        assertThrows(TransportDiscovery.HandshakeException.class,
                () -> session.negotiate(discovery, "server"));
        assertEquals(NetworkSession.State.CLOSED, session.state());
    }
}
