package com.aprism.loader.logging;

/**
 * A destination for structured log records (v26.2-Alpha.1, goal #6). Sinks
 * are attached to {@link AprismLogging} and receive every record that passes
 * the facility-level threshold. Implementations must be safe for concurrent
 * use: the facility may emit records from multiple threads.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface AprismLogSink extends AutoCloseable {

    /**
     * Writes a single record. Implementations must not throw into the
     * caller; sink failures are swallowed by the facility.
     *
     * @param record the record to write
     */
    void write(AprismLogRecord record);

    /**
     * Flushes any buffered output. Default implementation is a no-op.
     */
    default void flush() {
        // default no-op
    }

    /**
     * Closes the sink, releasing any underlying resources (files, streams).
     * Default implementation is a no-op.
     */
    default void close() {
        // default no-op
    }
}
