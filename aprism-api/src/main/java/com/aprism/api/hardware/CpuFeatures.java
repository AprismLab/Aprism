package com.aprism.api.hardware;

import java.util.Set;

/**
 * A snapshot of detected CPU characteristics (v26.4-Alpha.7, performance
 * &amp; hardware fusion reference). Values are advisory, never mandatory:
 * a mod reading this surface must degrade gracefully when a feature is
 * absent or the value is unknown.
 *
 * <p>Feature tokens use the canonical instruction-set names (e.g.
 * {@code sse4_2}, {@code avx2}, {@code avx512f}, {@code neon},
 * {@code sve}). Only proven features appear: the default probe reports
 * what the current platform can demonstrate, and a deeper probe (the
 * AprismJDK native probe) may extend the set.
 *
 * @param architecture the {@code os.arch} value (e.g. {@code amd64},
 *                     {@code aarch64})
 * @param osName the {@code os.name} value
 * @param availableProcessors processors visible to the JVM
 * @param featureTokens proven instruction-set feature tokens (lowercase)
 * @author BlockConnect@StarsailsClover
 */
public record CpuFeatures(String architecture, String osName, int availableProcessors,
                          Set<String> featureTokens) {

    /**
     * Canonical compact constructor: defensive copies and validation.
     */
    public CpuFeatures {
        if (architecture == null || architecture.isBlank()) {
            throw new IllegalArgumentException("architecture must be non-blank");
        }
        if (osName == null || osName.isBlank()) {
            throw new IllegalArgumentException("osName must be non-blank");
        }
        if (availableProcessors < 1) {
            throw new IllegalArgumentException("availableProcessors must be >= 1");
        }
        featureTokens = featureTokens == null ? Set.of() : Set.copyOf(featureTokens);
    }

    /**
     * @param token the feature token (case-insensitive)
     * @return whether the feature is proven present
     */
    public boolean hasFeature(String token) {
        return token != null && featureTokens.contains(token.toLowerCase());
    }
}
