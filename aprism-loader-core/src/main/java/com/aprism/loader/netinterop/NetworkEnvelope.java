package com.aprism.loader.netinterop;

import com.aprism.api.networking.NetworkDirection;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable transport-neutral network frame (v26.9-Alpha.6). The channel
 * remains mod-owned and namespaced; payload bytes are defensively copied so
 * a bridge cannot mutate a frame after validation.
 *
 * @author BlockConnect@StarsailsClover
 */
public record NetworkEnvelope(int protocol, String sourceId, NetworkDirection direction,
        String channel, Map<String, String> headers, byte[] payload) {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Default hard limit for a frame when no negotiated lower limit exists. */
    public static final int DEFAULT_MAX_PAYLOAD = 1 << 20;

    public NetworkEnvelope {
        if (protocol <= 0) {
            throw new IllegalArgumentException("protocol must be positive");
        }
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(payload, "payload");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must be non-blank");
        }
        if (!channel.matches("[a-z0-9][a-z0-9_-]*:[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("channel must be namespace:name: " + channel);
        }
        if (payload.length > DEFAULT_MAX_PAYLOAD) {
            throw new IllegalArgumentException("payload exceeds default limit: " + payload.length);
        }
        headers = Map.copyOf(headers == null ? Map.of() : headers);
        payload = Arrays.copyOf(payload, payload.length);
    }

    /** @return a defensive payload copy */
    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
