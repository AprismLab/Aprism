package com.aprism.api.introspection;

import java.util.List;

/**
 * A snapshot of a single live thread (v26.4-Alpha.4, JVM introspection
 * API). Provides the loader-level with a typed view of thread state
 * without JMX reflection.
 *
 * @param threadId the unique thread id
 * @param name the thread name
 * @param state the thread state name (e.g. {@code RUNNABLE}, {@code WAITING})
 * @param stackDepth the current stack depth
 * @param topFrames the topmost stack frame descriptions (may be empty)
 * @author BlockConnect@StarsailsClover
 */
public record ThreadInsight(long threadId, String name, String state, int stackDepth,
                            List<String> topFrames) {

    /**
     * Canonical compact constructor: defensive copies.
     */
    public ThreadInsight {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("thread name must be non-blank");
        }
        if (state == null) {
            throw new IllegalArgumentException("thread state must be non-null");
        }
        topFrames = topFrames == null ? List.of() : List.copyOf(topFrames);
    }
}
