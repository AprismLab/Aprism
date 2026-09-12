package com.aprism.loader.netinterop;

import com.aprism.api.networking.NetworkDirection;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Connection-level session state machine (v26.9 roadmap Alpha.6). Owns no
 * threads: callers drive it from their transport callbacks, which keeps the
 * interop layer free of polling and background timers.
 *
 * <p>Legal graph: DISCONNECTED -> NEGOTIATING -> ESTABLISHED -> CLOSED,
 * with NEGOTIATING -> CLOSED (failed handshake) and ESTABLISHED ->
 * NEGOTIATING (reconnect) allowed. Sending before ESTABLISHED or after
 * CLOSED fails closed with a typed diagnostic.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class NetworkSession {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Session lifecycle states. */
    public enum State {
        DISCONNECTED, NEGOTIATING, ESTABLISHED, CLOSED
    }

    /** Stable send refusal codes. */
    public enum Refusal {
        /** Session is not ESTABLISHED yet. */
        NOT_ESTABLISHED,
        /** Session is closed; no further traffic is allowed. */
        CLOSED,
        /** The channel was not part of the negotiated set. */
        CHANNEL_NOT_NEGOTIATED,
        /** The inbound frame declares a different protocol version. */
        PROTOCOL_MISMATCH,
        /** The payload exceeds the negotiated channel or frame limit. */
        PAYLOAD_TOO_LARGE
    }

    /** A typed send refusal. */
    public static final class SendRefusedException extends RuntimeException {
        private final Refusal refusal;

        SendRefusedException(Refusal refusal, String message) {
            super(message);
            this.refusal = refusal;
        }

        /** @return the stable refusal code */
        public Refusal refusal() {
            return refusal;
        }
    }

    private final String localSide;
    private final CodecRegistry codecs;
    private volatile State state = State.DISCONNECTED;
    private volatile TransportDiscovery.NegotiatedTransport negotiated;

    public NetworkSession(String localSide, CodecRegistry codecs) {
        this.localSide = Objects.requireNonNull(localSide, "localSide");
        this.codecs = Objects.requireNonNull(codecs, "codecs");
    }

    /** @return the current session state */
    public State state() {
        return state;
    }

    /** @return the negotiated transport, or null before establishment */
    public TransportDiscovery.NegotiatedTransport negotiated() {
        return negotiated;
    }

    /**
     * Begins negotiation with the given side.
     *
     * @param discovery the discovery registry
     * @param remoteSide the remote side id
     * @throws TransportDiscovery.HandshakeException fail-closed on no common
     *         protocol/channel (session lands in CLOSED)
     */
    public void negotiate(TransportDiscovery discovery, String remoteSide) {
        if (state != State.DISCONNECTED && state != State.NEGOTIATING) {
            throw new IllegalStateException("negotiate requires DISCONNECTED or "
                    + "NEGOTIATING but session is " + state);
        }
        state = State.NEGOTIATING;
        try {
            negotiated = discovery.negotiate(localSide, remoteSide);
            state = State.ESTABLISHED;
        } catch (TransportDiscovery.HandshakeException failed) {
            state = State.CLOSED;
            throw failed;
        }
    }

    /**
     * Returns to NEGOTIATING for a reconnect (keeps the session object).
     */
    public void beginReconnect() {
        requireState(State.ESTABLISHED, "beginReconnect");
        negotiated = null;
        state = State.NEGOTIATING;
    }

    /** Closes the session; idempotent and terminal. */
    public void close() {
        negotiated = null;
        state = State.CLOSED;
    }

    /**
     * Builds an outbound frame, enforcing negotiated channels and limits.
     *
     * @param direction the travel direction
     * @param channel the channel (must be negotiated)
     * @param value the value to encode with the registered codec
     * @return the validated envelope
     * @throws SendRefusedException fail-closed with a stable refusal code
     */
    public NetworkEnvelope send(NetworkDirection direction, String channel,
            Object value) {
        if (state == State.CLOSED) {
            throw new SendRefusedException(Refusal.CLOSED, "session closed");
        }
        if (state != State.ESTABLISHED || negotiated == null) {
            throw new SendRefusedException(Refusal.NOT_ESTABLISHED,
                    "session not established: " + state);
        }
        if (!negotiated.channels().contains(channel)) {
            throw new SendRefusedException(Refusal.CHANNEL_NOT_NEGOTIATED,
                    "channel not negotiated: " + channel);
        }
        byte[] payload;
        try {
            payload = codecs.encode(channel, value);
        } catch (IllegalArgumentException localLimitOrUnknown) {
            // The registration-time limit is never wider than a negotiated
            // one, but either way the session is the protocol boundary: its
            // refusal vocabulary is authoritative (v26.9-Alpha.6).
            throw new SendRefusedException(Refusal.PAYLOAD_TOO_LARGE,
                    "local codec refused the value: "
                            + localLimitOrUnknown.getMessage());
        }
        int limit = negotiated.channelLimit(channel);
        if (payload.length > limit) {
            throw new SendRefusedException(Refusal.PAYLOAD_TOO_LARGE,
                    "payload " + payload.length + " exceeds negotiated " + limit);
        }
        return new NetworkEnvelope(negotiated.protocol(), localSide, direction,
                channel, Map.of(), payload);
    }

    /**
     * Accepts an inbound frame after the same negotiation checks.
     *
     * @param envelope the received frame
     * @return the decoded value
     * @throws SendRefusedException fail-closed with a stable refusal code
     */
    public Object receive(NetworkEnvelope envelope) {
        //GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        if (state == State.CLOSED) {
            throw new SendRefusedException(Refusal.CLOSED, "session closed");
        }
        if (state != State.ESTABLISHED || negotiated == null) {
            throw new SendRefusedException(Refusal.NOT_ESTABLISHED,
                    "session not established: " + state);
        }
        String channel = envelope.channel();
        if (!negotiated.channels().contains(channel)) {
            throw new SendRefusedException(Refusal.CHANNEL_NOT_NEGOTIATED,
                    "channel not negotiated: " + channel);
        }
        if (envelope.protocol() != negotiated.protocol()) {
            throw new SendRefusedException(Refusal.PROTOCOL_MISMATCH,
                    "protocol mismatch: " + envelope.protocol() + " vs "
                            + negotiated.protocol());
        }
        byte[] payload = envelope.payload();
        int limit = negotiated.channelLimit(channel);
        if (payload.length > limit) {
            throw new SendRefusedException(Refusal.PAYLOAD_TOO_LARGE,
                    "payload " + payload.length + " exceeds negotiated " + limit);
        }
        Object decoded = codecs.decode(channel, payload);
        Arrays.fill(payload, (byte) 0);
        return decoded;
    }

    private void requireState(State expected, String operation) {
        if (state != expected) {
            throw new IllegalStateException(operation + " requires " + expected
                    + " but session is " + state);
        }
    }
}
