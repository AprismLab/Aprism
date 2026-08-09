package com.aprism.loader.logging;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central structured logging facility for Aprism (v26.2-Alpha.1, goal #6).
 * Every mod, extension, and runtime component obtains a per-unit
 * {@link AprismLogger} via {@link #getLogger(String)}; records flow through
 * the facility's level threshold and fan out to the attached sinks plus the
 * retained {@link AprismLogRingBuffer} (used for crash reports and the load
 * report).
 *
 * <p>The facility is fail-safe: a sink throwing never propagates into the
 * logging call site, so logging can never crash the host game.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismLogging implements Closeable {

    /** Default ring-buffer capacity for retained records. */
    public static final int DEFAULT_RETENTION = 5000;

    private final List<AprismLogSink> sinks = new CopyOnWriteArrayList<>();
    private final AprismLogRingBuffer retained;
    private volatile AprismLogLevel threshold = AprismLogLevel.INFO;
    private volatile boolean closed;

    /**
     * Creates a facility with the default retention capacity.
     */
    public AprismLogging() {
        this(DEFAULT_RETENTION);
    }

    /**
     * @param retentionCapacity the number of records retained in the ring
     *                          buffer
     */
    public AprismLogging(int retentionCapacity) {
        this.retained = new AprismLogRingBuffer(retentionCapacity);
        this.sinks.add(retained);
    }

    /**
     * Attaches a sink. The ring buffer is always attached and cannot be
     * removed; duplicate attaches of the same sink are ignored.
     *
     * @param sink the sink to attach
     */
    public void attachSink(AprismLogSink sink) {
        if (sink == null || sink == retained || sinks.contains(sink)) {
            return;
        }
        sinks.add(sink);
    }

    /**
     * Removes a previously attached sink and closes it.
     *
     * @param sink the sink to detach
     */
    public void detachSink(AprismLogSink sink) {
        if (sink == null || sink == retained) {
            return;
        }
        if (sinks.remove(sink)) {
            sink.close();
        }
    }

    /**
     * @return the currently attached sinks, including the ring buffer
     */
    public List<AprismLogSink> getSinks() {
        return List.copyOf(sinks);
    }

    /**
     * @return the retained-records ring buffer (always attached)
     */
    public AprismLogRingBuffer getRetained() {
        return retained;
    }

    /**
     * Sets the minimum level that passes to sinks.
     *
     * @param threshold the new threshold
     */
    public void setThreshold(AprismLogLevel threshold) {
        if (threshold != null) {
            this.threshold = threshold;
        }
    }

    /**
     * @return the current threshold
     */
    public AprismLogLevel getThreshold() {
        return threshold;
    }

    /**
     * Obtains a per-unit logger.
     *
     * @param unit the unit name (mod id, extension id, or component)
     * @return the logger bound to this facility and unit
     */
    public AprismLogger getLogger(String unit) {
        return new AprismLogger(this, unit == null ? "aprism" : unit);
    }

    /**
     * Emits a record through the threshold filter to every sink. Called by
     * {@link AprismLogger}; not intended for direct use.
     *
     * @param record the record to emit
     */
    void emit(AprismLogRecord record) {
        if (closed || record == null || !record.level().isEnabledAt(threshold)) {
            return;
        }
        for (AprismLogSink sink : sinks) {
            try {
                sink.write(record);
            } catch (RuntimeException ignored) {
                // Fail-safe: a broken sink must never crash the host game.
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        for (AprismLogSink sink : sinks) {
            try {
                sink.flush();
                if (sink != retained) {
                    sink.close();
                }
            } catch (RuntimeException ignored) {
                // Fail-safe flush/close on shutdown.
            }
        }
        sinks.clear();
        sinks.add(retained);
    }

    /**
     * @return whether the facility has been closed
     */
    public boolean isClosed() {
        return closed;
    }
}
