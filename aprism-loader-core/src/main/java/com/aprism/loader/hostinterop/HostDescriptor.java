package com.aprism.loader.hostinterop;

import com.aprism.loader.livectx.LiveContext;

import java.util.Set;

/**
 * What a discovered host is and can do (v26.9 roadmap Alpha.7).
 *
 * @param hostId stable host id (diagnostics key)
 * @param kind the host family
 * @param side which side this host serves
 * @param capabilities the capability set the host really implements
 * @author BlockConnect@StarsailsClover
 */
public record HostDescriptor(String hostId, Kind kind, LiveContext.Side side,
        Set<HostCapability> capabilities) {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** The host families the loader distinguishes. */
    public enum Kind {
        /** Aprism's own native runtime. */
        APRISM_NATIVE,
        /** A launcher-managed instance (e.g. MDL driven). */
        LAUNCHER_MANAGED,
        /** A dedicated server process. */
        DEDICATED_SERVER,
        /** A test/tooling embedder. */
        EMBEDDER
    }

    /** Validates the descriptor. */
    public HostDescriptor {
        if (hostId == null || hostId.isBlank()) {
            throw new IllegalArgumentException("hostId required");
        }
        if (kind == null || side == null) {
            throw new IllegalArgumentException("kind and side required");
        }
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    /**
     * @param capability the capability to test
     * @return true when this host advertises it
     */
    public boolean supports(HostCapability capability) {
        return capabilities.contains(capability);
    }
}
