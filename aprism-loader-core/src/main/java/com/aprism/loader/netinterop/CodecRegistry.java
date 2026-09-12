package com.aprism.loader.netinterop;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe channel codec registry with symmetric outbound/inbound payload
 * limits (v26.9-Alpha.6). A channel can be registered once per registry;
 * duplicate registration is rejected to prevent bridge ownership ambiguity.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class CodecRegistry {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Channel declaration negotiated with a peer. */
    public record ChannelSpec(String channel, String codecId, int maxPayload) {
        public ChannelSpec {
            if (channel == null || !channel.matches("[a-z0-9][a-z0-9_-]*:[a-z0-9][a-z0-9_-]*")) {
                throw new IllegalArgumentException("invalid channel: " + channel);
            }
            if (codecId == null || codecId.isBlank()) {
                throw new IllegalArgumentException("codecId required");
            }
            if (maxPayload <= 0 || maxPayload > NetworkEnvelope.DEFAULT_MAX_PAYLOAD) {
                throw new IllegalArgumentException("maxPayload outside 1..1MiB: " + maxPayload);
            }
        }
    }

    private record Entry(ChannelSpec spec, NetworkCodec<?> codec) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** Registers one channel and codec. */
    public void register(ChannelSpec spec, NetworkCodec<?> codec) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(codec, "codec");
        if (!spec.codecId().equals(codec.id())) {
            throw new IllegalArgumentException("codec id mismatch: " + codec.id());
        }
        if (entries.putIfAbsent(spec.channel(), new Entry(spec, codec)) != null) {
            throw new IllegalArgumentException("channel already registered: " + spec.channel());
        }
    }

    /** Encodes a value and enforces the channel limit. */
    @SuppressWarnings("unchecked")
    public byte[] encode(String channel, Object value) {
        Entry entry = require(channel);
        byte[] payload = ((NetworkCodec<Object>) entry.codec()).encode(value);
        return checked(payload, entry.spec().maxPayload());
    }

    /** Decodes bytes and enforces the channel limit before invoking user code. */
    public Object decode(String channel, byte[] payload) {
        Entry entry = require(channel);
        byte[] checked = checked(payload, entry.spec().maxPayload());
        return entry.codec().decode(checked);
    }

    /** @return the negotiated declaration, or null when unknown */
    public ChannelSpec spec(String channel) {
        Entry entry = entries.get(channel);
        return entry == null ? null : entry.spec();
    }

    /** @return immutable snapshot of registered channels */
    public Map<String, ChannelSpec> channels() {
        return entries.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> e.getValue().spec()));
    }

    private Entry require(String channel) {
        Entry entry = entries.get(channel);
        if (entry == null) {
            throw new IllegalArgumentException("unknown network channel: " + channel);
        }
        return entry;
    }

    private static byte[] checked(byte[] payload, int max) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length > max) {
            throw new IllegalArgumentException("payload exceeds channel limit: "
                    + payload.length + " > " + max);
        }
        return Arrays.copyOf(payload, payload.length);
    }
}
