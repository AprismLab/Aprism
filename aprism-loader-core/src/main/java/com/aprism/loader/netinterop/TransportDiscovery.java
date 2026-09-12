package com.aprism.loader.netinterop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bidirectional transport discovery and handshake negotiation
 * (v26.9 roadmap Alpha.6). Each side publishes one advertisement; the
 * negotiator computes the highest common protocol, the channel
 * intersection, and per-channel limits as the MINIMUM of both sides -
 * so a peer can never widen another peer's limits.
 *
 * <p>Fail-closed: no common protocol or no common channel is reported as a
 * typed {@link HandshakeException} with a stable code, never a silent
 * downgrade.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class TransportDiscovery {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG =
            Logger.getLogger(TransportDiscovery.class.getName());

    /** Stable handshake failure codes. */
    public enum Code {
        /** No protocol version is supported by both sides. */
        NO_COMMON_PROTOCOL,
        /** The two sides share no channel at all. */
        NO_COMMON_CHANNEL,
        /** One side is not advertised (discovery not symmetric yet). */
        UNKNOWN_SIDE
    }

    /** A typed handshake failure. */
    public static final class HandshakeException extends RuntimeException {
        private final Code code;

        HandshakeException(Code code, String message) {
            super(message);
            this.code = code;
        }

        /** @return the stable failure code */
        public Code code() {
            return code;
        }
    }

    /** The negotiated result both sides must agree on. */
    public record NegotiatedTransport(String localSide, String remoteSide,
            int protocol, Map<String, Integer> channelLimits, int frameLimit) {

        /** @return the channels this connection may use */
        public java.util.Set<String> channels() {
            return channelLimits.keySet();
        }

        /** @return the effective payload limit for one channel */
        public int channelLimit(String channel) {
            Integer limit = channelLimits.get(channel);
            if (limit == null) {
                throw new IllegalArgumentException("channel not negotiated: " + channel);
            }
            return Math.min(limit, frameLimit);
        }

        /** @return compact JSON for diagnostics */
        public String toJson() {
            return "{\"local\":\"" + localSide + "\",\"remote\":\"" + remoteSide
                    + "\",\"protocol\":" + protocol + ",\"channels\":"
                    + channelLimits.size() + ",\"frameLimit\":" + frameLimit + "}";
        }
    }

    private final Map<String, TransportAdvertisement> advertised =
            new ConcurrentHashMap<>();

    /**
     * Publishes one side's advertisement (idempotent per side id).
     *
     * @param advertisement the advertisement
     */
    public void advertise(TransportAdvertisement advertisement) {
        advertised.put(advertisement.sideId(), advertisement);
        LOG.info("[netinterop] advertised transport side=" + advertisement.sideId()
                + " protocols=" + advertisement.protocols()
                + " channels=" + advertisement.channels().size());
    }

    /** @return the advertised side ids */
    public java.util.Set<String> sides() {
        return java.util.Set.copyOf(advertised.keySet());
    }

    /** Removes one advertisement (leak prevention on disconnect). */
    public void withdraw(String sideId) {
        advertised.remove(sideId);
    }

    /**
     * Negotiates between two advertised sides.
     *
     * @param localSide the local side id
     * @param remoteSide the remote side id
     * @return the negotiated transport
     * @throws HandshakeException fail-closed on unknown side or no common
     *         protocol/channel
     */
    public NegotiatedTransport negotiate(String localSide, String remoteSide) {
        TransportAdvertisement local = advertised.get(localSide);
        TransportAdvertisement remote = advertised.get(remoteSide);
        if (local == null || remote == null) {
            throw new HandshakeException(Code.UNKNOWN_SIDE,
                    "unknown side: local=" + local + " remote=" + remote);
        }
        int protocol = local.protocols().stream()
                .filter(remote.protocols()::contains)
                .max(Integer::compareTo)
                .orElseThrow(() -> new HandshakeException(Code.NO_COMMON_PROTOCOL,
                        "no common protocol between " + localSide + " and "
                                + remoteSide + ": " + local.protocols() + " vs "
                                + remote.protocols()));
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (Map.Entry<String, CodecRegistry.ChannelSpec> entry
                : local.channels().entrySet()) {
            CodecRegistry.ChannelSpec remoteSpec = remote.channels().get(entry.getKey());
            if (remoteSpec == null) {
                continue;
            }
            if (!remoteSpec.codecId().equals(entry.getValue().codecId())) {
                LOG.warning("[netinterop] channel codec mismatch, skipped: "
                        + entry.getKey() + " (" + entry.getValue().codecId()
                        + " vs " + remoteSpec.codecId() + ")");
                continue;
            }
            limits.put(entry.getKey(), Math.min(entry.getValue().maxPayload(),
                    remoteSpec.maxPayload()));
        }
        if (limits.isEmpty()) {
            throw new HandshakeException(Code.NO_COMMON_CHANNEL,
                    "no common channel between " + localSide + " and " + remoteSide);
        }
        int frameLimit = Math.min(local.maxFrameBytes(), remote.maxFrameBytes());
        return new NegotiatedTransport(localSide, remoteSide, protocol,
                Map.copyOf(limits), frameLimit);
    }
}
