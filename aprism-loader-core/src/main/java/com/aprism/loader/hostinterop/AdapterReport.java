package com.aprism.loader.hostinterop;

import java.util.List;

/**
 * Per-item adapter outcome (v26.9 roadmap Alpha.7): adapters report what
 * they accepted and what they refused, with a stable refusal code, so a
 * host limitation is visible instead of silently swallowed.
 *
 * @param acceptedIds ids the host accepted
 * @param refusals refused ids with their reasons
 * @author BlockConnect@StarsailsClover
 */
public record AdapterReport(List<String> acceptedIds,
        List<Refusal> refusals) {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Stable adapter refusal codes. */
    public enum Code {
        /** The host does not implement the requested capability. */
        CAPABILITY_UNAVAILABLE,
        /** The host's surface is frozen/closed at this lifecycle point. */
        SURFACE_CLOSED,
        /** The id collided with an existing registration. */
        DUPLICATE_ID,
        /** The host rejected the item for host-specific reasons. */
        HOST_REJECTED
    }

    /** One refused item. */
    public record Refusal(String id, Code code, String detail) {
    }

    /** Validates the report. */
    public AdapterReport {
        acceptedIds = acceptedIds == null ? List.of() : List.copyOf(acceptedIds);
        refusals = refusals == null ? List.of() : List.copyOf(refusals);
    }

    /**
     * @return an empty successful report
     */
    public static AdapterReport empty() {
        return new AdapterReport(List.of(), List.of());
    }

    /**
     * @return true when nothing was refused
     */
    public boolean clean() {
        return refusals.isEmpty();
    }
}
