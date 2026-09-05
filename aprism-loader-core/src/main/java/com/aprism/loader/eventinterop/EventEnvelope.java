package com.aprism.loader.eventinterop;

import com.aprism.loader.livectx.LiveContext;

import java.util.Map;
import java.util.Objects;

/**
 * The normalized event envelope (v26.9 roadmap Alpha.5): one shape every
 * event bridge translates into, regardless of the originating loader API.
 * The payload stays OPAQUE - a Fabric callback or a Forge event object is
 * carried by reference and interpreted only by consumers that know the
 * source; the envelope itself carries the normalized metadata.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class EventEnvelope {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private final String type;
    private final String sourceId;
    private final LiveContext.Side side;
    private final LiveContext.State lifecycle;
    private final boolean cancellable;
    private final Map<String, String> headers;
    private final Object payload;

    private EventEnvelope(Builder builder) {
        this.type = builder.type;
        this.sourceId = builder.sourceId;
        this.side = builder.side;
        this.lifecycle = builder.lifecycle;
        this.cancellable = builder.cancellable;
        this.headers = Map.copyOf(builder.headers);
        this.payload = builder.payload;
    }

    /**
     * @return a new builder for the named envelope type
     */
    public static Builder builder(String type) {
        return new Builder(type);
    }

    public String type() {
        return type;
    }

    public String sourceId() {
        return sourceId;
    }

    public LiveContext.Side side() {
        return side;
    }

    public LiveContext.State lifecycle() {
        return lifecycle;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    /**
     * @return the immutable header map
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * @return the opaque payload reference (bridge-defined)
     */
    public Object payload() {
        return payload;
    }

    /**
     * Marks the envelope cancelled; only meaningful for cancellable
     * envelopes (ignored otherwise).
     */
    public void cancel() {
        if (cancellable) {
            cancelled = true;
        }
    }

    private volatile boolean cancelled;

    /**
     * @return whether a listener cancelled this envelope
     */
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public String toString() {
        return "Envelope{" + type + " side=" + side + " lifecycle=" + lifecycle
                + " cancellable=" + cancellable + " source=" + sourceId + "}";
    }

    /** Builder with fail-closed validation. */
    public static final class Builder {
        private final String type;
        private String sourceId = "aprism";
        private LiveContext.Side side = LiveContext.Side.CLIENT;
        private LiveContext.State lifecycle = LiveContext.State.IN_WORLD;
        private boolean cancellable;
        private final java.util.HashMap<String, String> headers =
                new java.util.HashMap<>();
        private Object payload;

        private Builder(String type) {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("envelope type required");
            }
            if (!type.matches("[a-z]+:[a-zA-Z0-9_.-]+")) {
                throw new IllegalArgumentException(
                        "envelope type must be namespaced (source:path): "
                                + type);
            }
            this.type = type;
        }

        /**
         * Sets the originating bridge/provider id.
         */
        public Builder source(String sourceId) {
            this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
            return this;
        }

        /**
         * Sets the normalized side.
         */
        public Builder side(LiveContext.Side side) {
            this.side = Objects.requireNonNull(side, "side");
            return this;
        }

        /**
         * Sets the normalized lifecycle state.
         */
        public Builder lifecycle(LiveContext.State lifecycle) {
            this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
            return this;
        }

        /**
         * Declares cancellation support.
         */
        public Builder cancellable(boolean cancellable) {
            this.cancellable = cancellable;
            return this;
        }

        /**
         * Adds one normalized header.
         */
        public Builder header(String key, String value) {
            if (key == null || key.isBlank() || value == null) {
                throw new IllegalArgumentException("header requires key+value");
            }
            headers.put(key, value);
            return this;
        }

        /**
         * Sets the opaque payload.
         */
        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        /**
         * @return the immutable envelope
         */
        public EventEnvelope build() {
            return new EventEnvelope(this);
        }
    }
}
