package com.aprism.api.hardware;

/**
 * The full hardware insight exposed to mods (v26.4-Alpha.7, performance
 * &amp; hardware fusion reference). Follows the AprismJDK design (§5):
 * advisory, never mandatory — every consumer must degrade gracefully.
 *
 * <p>Unknown values use {@code -1} (cache line size, NUMA node count):
 * the default loader probe cannot prove them on a stock JVM, and a deeper
 * probe (the AprismJDK native probe) may fill them in later.
 *
 * @param cpuFeatures the detected CPU characteristics
 * @param cacheLineBytes the cache line size in bytes ({@code -1} when
 *                       unknown)
 * @param numaNodeCount the NUMA node count ({@code -1} when unknown)
 * @author BlockConnect@StarsailsClover
 */
public record HardwareInsight(CpuFeatures cpuFeatures, long cacheLineBytes, int numaNodeCount) {

    /**
     * Canonical compact constructor: validates bounds.
     */
    public HardwareInsight {
        if (cpuFeatures == null) {
            throw new IllegalArgumentException("cpuFeatures must be non-null");
        }
        if (cacheLineBytes != -1 && cacheLineBytes < 1) {
            throw new IllegalArgumentException("cacheLineBytes must be -1 or >= 1");
        }
        if (numaNodeCount != -1 && numaNodeCount < 1) {
            throw new IllegalArgumentException("numaNodeCount must be -1 or >= 1");
        }
    }

    /**
     * @return whether the cache line size is known
     */
    public boolean cacheLineKnown() {
        return cacheLineBytes > 0;
    }

    /**
     * @return whether the NUMA topology is known
     */
    public boolean numaKnown() {
        return numaNodeCount > 0;
    }
}
