package com.aprism.api.introspection;

/**
 * A snapshot of heap and non-heap memory usage (v26.4-Alpha.4, JVM
 * introspection API). All values are in bytes; {@code -1} means the value
 * is undefined for this VM.
 *
 * @param heapUsed heap bytes currently used
 * @param heapCommitted heap bytes committed
 * @param heapMax maximum heap bytes ({@code -1} if undefined)
 * @param nonHeapUsed non-heap bytes currently used
 * @author BlockConnect@StarsailsClover
 */
public record HeapSummary(long heapUsed, long heapCommitted, long heapMax, long nonHeapUsed) {
}
