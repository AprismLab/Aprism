package com.aprism.loader.logging;

/**
 * Log severity levels for the Aprism structured logging facility
 * (v26.2-Alpha.1, goal #6). Ordered from most to least verbose; a
 * facility-level threshold filters records below the configured level.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum AprismLogLevel {

    /** Fine-grained diagnostic detail. Most verbose. */
    TRACE,

    /** Diagnostic information useful during development. */
    DEBUG,

    /** Normal operational events. */
    INFO,

    /** Potentially harmful situations that do not abort processing. */
    WARN,

    /** Failures that abort a unit of work. Least verbose. */
    ERROR;

    /**
     * Whether a record at this level passes a threshold filter.
     *
     * @param threshold the minimum level that passes
     * @return true when this level is at least as severe as the threshold
     */
    public boolean isEnabledAt(AprismLogLevel threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}
