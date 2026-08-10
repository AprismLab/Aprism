package com.aprism.api.introspection;

/**
 * A snapshot of one garbage collector's activity (v26.4-Alpha.4, JVM
 * introspection API).
 *
 * @param name the collector name
 * @param collectionCount the number of collections performed ({@code -1}
 *                        if undefined)
 * @param collectionTimeMs approximate accumulated collection time in
 *                         milliseconds ({@code -1} if undefined)
 * @author BlockConnect@StarsailsClover
 */
public record GcSummary(String name, long collectionCount, long collectionTimeMs) {

    /**
     * Canonical compact constructor: validates the name.
     */
    public GcSummary {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("collector name must be non-blank");
        }
    }
}
