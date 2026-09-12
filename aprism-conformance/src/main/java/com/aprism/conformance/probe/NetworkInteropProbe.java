package com.aprism.conformance.probe;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import com.aprism.api.networking.NetworkDirection;
import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;
import com.aprism.loader.netinterop.CodecRegistry;
import com.aprism.loader.netinterop.NetworkCodec;
import com.aprism.loader.netinterop.NetworkSession;
import com.aprism.loader.netinterop.TransportAdvertisement;
import com.aprism.loader.netinterop.TransportDiscovery;

/**
 * Network interop probe (v26.9-Alpha.6): codec registration, bidirectional
 * handshake negotiation with min-limit semantics, and the session state
 * machine including fail-closed refusals. Contract-only: the live
 * cross-loader transport proof is the v26.10 milestone.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class NetworkInteropProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    @Override
    public ProbeResult run() {
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("networking",
                "handshake + codec registry + limits", "unit",
                CoverageMatrix.Status.CONTRACT_ONLY,
                "executed by ConformanceKit on every run; live cross-loader "
                        + "milestone: v26.10-Alpha.6");
        try {
            NetworkCodec<String> codec = new NetworkCodec<>() {
                @Override
                public String id() {
                    return "conformance:string";
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
            CodecRegistry codecs = new CodecRegistry();
            codecs.register(new CodecRegistry.ChannelSpec("conformance:chan",
                    codec.id(), 16), codec);

            TransportDiscovery discovery = new TransportDiscovery();
            discovery.advertise(new TransportAdvertisement("conformance-client",
                    Set.of(1, 2), Map.of("conformance:chan",
                            new CodecRegistry.ChannelSpec("conformance:chan",
                                    codec.id(), 16)), 64));
            discovery.advertise(new TransportAdvertisement("conformance-server",
                    Set.of(2), Map.of("conformance:chan",
                            new CodecRegistry.ChannelSpec("conformance:chan",
                                    codec.id(), 8)), 32));

            NetworkSession session = new NetworkSession("conformance-client", codecs);
            session.negotiate(discovery, "conformance-server");
            TransportDiscovery.NegotiatedTransport negotiated = session.negotiated();
            boolean negotiatedOk = negotiated.protocol() == 2
                    && negotiated.channelLimit("conformance:chan") == 8;

            boolean sendRefused = false;
            try {
                session.send(NetworkDirection.CLIENT_TO_SERVER,
                        "conformance:chan", "0123456789ABCDEFG");
            } catch (NetworkSession.SendRefusedException refused) {
                sendRefused = refused.refusal()
                        == NetworkSession.Refusal.PAYLOAD_TOO_LARGE;
            }

            var envelope = session.send(NetworkDirection.CLIENT_TO_SERVER,
                    "conformance:chan", "ok");
            boolean roundTrip = "ok".equals(session.receive(envelope));

            boolean noCommon = false;
            try {
                TransportDiscovery disjoint = new TransportDiscovery();
                disjoint.advertise(new TransportAdvertisement("conformance-client",
                        Set.of(1), Map.of(), 8));
                disjoint.advertise(new TransportAdvertisement("conformance-server",
                        Set.of(9), Map.of(), 8));
                disjoint.negotiate("conformance-client", "conformance-server");
            } catch (TransportDiscovery.HandshakeException failed) {
                noCommon = failed.code()
                        == TransportDiscovery.Code.NO_COMMON_PROTOCOL;
            }

            session.close();
            boolean pass = negotiatedOk && sendRefused && roundTrip && noCommon
                    && session.state() == NetworkSession.State.CLOSED;
            return new ProbeResult(cell, pass, negotiated.toJson()
                    + " sendRefused=" + sendRefused + " roundTrip=" + roundTrip
                    + " noCommonProtocol=" + noCommon);
        } catch (Throwable t) {
            return new ProbeResult(cell, false, t.toString());
        }
    }
}
