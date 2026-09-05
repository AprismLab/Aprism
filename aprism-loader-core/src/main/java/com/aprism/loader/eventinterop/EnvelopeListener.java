package com.aprism.loader.eventinterop;

/**
 * A listener for normalized event envelopes (v26.9 roadmap Alpha.5).
 *
 * @author BlockConnect@StarsailsClover
 */
@FunctionalInterface
public interface EnvelopeListener {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * Called for each matching envelope unless a higher-priority listener
     * cancelled it.
     *
     * @param envelope the envelope (payload stays opaque)
     */
    void onEnvelope(EventEnvelope envelope);
}
