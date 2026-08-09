package com.aprism.loader.logging;

import java.util.ArrayList;
import java.util.List;

/**
 * A bounded in-memory ring buffer of {@link AprismLogRecord}s
 * (v26.2-Alpha.1, goal #6). The buffer is wired as a sink of
 * {@link AprismLogging} so recent records can be attached to crash reports
 * and the load report without holding unbounded memory. Thread-safe.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismLogRingBuffer implements AprismLogSink {

    private final int capacity;
    private final AprismLogRecord[] buffer;
    private int head;
    private int size;

    /**
     * @param capacity the maximum number of records retained
     */
    public AprismLogRingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.buffer = new AprismLogRecord[capacity];
    }

    @Override
    public synchronized void write(AprismLogRecord record) {
        buffer[head] = record;
        head = (head + 1) % capacity;
        if (size < capacity) {
            size++;
        }
    }

    /**
     * @return the retained records in chronological order (oldest first)
     */
    public synchronized List<AprismLogRecord> snapshot() {
        List<AprismLogRecord> out = new ArrayList<>(size);
        int start = size < capacity ? 0 : head;
        for (int i = 0; i < size; i++) {
            out.add(buffer[(start + i) % capacity]);
        }
        return out;
    }

    /**
     * @return the number of records currently retained
     */
    public synchronized int size() {
        return size;
    }

    /**
     * @return the maximum number of records retained
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Drops all retained records.
     */
    public synchronized void clear() {
        for (int i = 0; i < capacity; i++) {
            buffer[i] = null;
        }
        head = 0;
        size = 0;
    }
}
