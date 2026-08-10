package com.aprism.api.hardware;

/**
 * A hardware probe seam (v26.4-Alpha.7, performance &amp; hardware fusion
 * reference). The loader core ships a default probe that reports what a
 * stock JVM can prove; a deeper probe — the AprismJDK native probe — can
 * register through {@code HardwareRegistry} and replace the insight with
 * hardware-backed values (cache line size, NUMA topology, extended
 * feature tokens).
 *
 * @author BlockConnect@StarsailsClover
 */
public interface HardwareProbe {

    /**
     * @return the probe name (e.g. {@code default}, {@code aprismjDK-native})
     */
    String name();

    /**
     * @return whether this probe can produce hardware-backed values on the
     *         current platform
     */
    boolean isAvailable();

    /**
     * Produces the hardware insight. Implementations must be fail-safe:
     * a probe that cannot produce a value reports the unknown sentinel
     * ({@code -1}) rather than throwing.
     *
     * @return the probed insight
     */
    HardwareInsight probe();
}
