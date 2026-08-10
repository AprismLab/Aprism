package com.aprism.api.networking;

import java.util.Objects;

/**
 * A packet travelling over a registered {@link PacketChannel}
 * (v26.3-Alpha.3, QA0 gap #4). Payloads are transport-neutral byte arrays;
 * mods own their own (de)serialization on top of the raw bytes.
 *
 * @param channel the channel this packet belongs to
 * @param payload the raw payload bytes (never null; may be empty)
 * @author BlockConnect@StarsailsClover
 */
public record NetworkPacket(PacketChannel channel, byte[] payload) {

    /**
     * Validates the packet fields.
     */
    public NetworkPacket {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(payload, "payload");
    }

    /**
     * Builds an empty-payload packet (handshake / notification style).
     *
     * @param channel the channel
     * @return the packet with an empty payload
     */
    public static NetworkPacket empty(PacketChannel channel) {
        return new NetworkPacket(channel, new byte[0]);
    }

    /**
     * @return the channel id shortcut
     */
    public String channelId() {
        return channel.id();
    }

    /**
     * @return the payload length in bytes
     */
    public int payloadLength() {
        return payload.length;
    }
}
