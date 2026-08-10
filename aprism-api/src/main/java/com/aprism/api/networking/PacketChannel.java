package com.aprism.api.networking;

import java.util.Objects;

/**
 * A named packet channel that mods use to exchange custom payloads
 * (v26.3-Alpha.3, QA0 gap #4). Channel ids follow the
 * {@code namespace:name} convention (namespace = owning mod id) so that
 * channels from different mods cannot collide. Channels are registered in
 * the networking registry before any packet may be sent on them.
 *
 * @param id          the channel id ({@code namespace:name})
 * @param description a human-readable description of the channel's purpose
 * @author BlockConnect@StarsailsClover
 */
public record PacketChannel(String id, String description) {

    private static final String SEGMENT_PATTERN = "[a-z0-9][a-z0-9_-]*";

    /**
     * Validates the channel id format.
     */
    public PacketChannel {
        Objects.requireNonNull(id, "id");
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            throw new IllegalArgumentException("channel id must be namespace:name, got: " + id);
        }
        String namespace = id.substring(0, colon);
        String name = id.substring(colon + 1);
        if (!namespace.matches(SEGMENT_PATTERN) || !name.matches(SEGMENT_PATTERN)) {
            throw new IllegalArgumentException("invalid channel id segments: " + id);
        }
    }

    /**
     * Builds a channel with an empty description.
     *
     * @param id the channel id
     * @return the channel
     */
    public static PacketChannel of(String id) {
        return new PacketChannel(id, "");
    }
}
