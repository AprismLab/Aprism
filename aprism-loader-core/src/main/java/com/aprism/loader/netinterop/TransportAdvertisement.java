package com.aprism.loader.netinterop;

import java.util.Map;
import java.util.Set;

/**
 * What one side of a network connection advertises (v26.9-Alpha.6):
 * supported protocol versions, the channels with their codecs and per-channel
 * payload limits. Discovery is bidirectional: each side publishes one
 * advertisement and negotiation joins the two.
 *
 * @author BlockConnect@StarsailsClover
 */
public record TransportAdvertisement(String sideId, Set<Integer> protocols,
        Map<String, CodecRegistry.ChannelSpec> channels, int maxFrameBytes) {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Validates the advertisement. */
    public TransportAdvertisement {
        if (sideId == null || sideId.isBlank()) {
            throw new IllegalArgumentException("sideId required");
        }
        protocols = protocols == null ? Set.of() : Set.copyOf(protocols);
        if (protocols.isEmpty()) {
            throw new IllegalArgumentException("at least one protocol required");
        }
        for (Integer protocol : protocols) {
            if (protocol == null || protocol <= 0) {
                throw new IllegalArgumentException("protocols must be positive");
            }
        }
        channels = channels == null ? Map.of() : Map.copyOf(channels);
        if (maxFrameBytes <= 0 || maxFrameBytes > NetworkEnvelope.DEFAULT_MAX_PAYLOAD) {
            throw new IllegalArgumentException(
                    "maxFrameBytes outside 1..1MiB: " + maxFrameBytes);
        }
    }
}
