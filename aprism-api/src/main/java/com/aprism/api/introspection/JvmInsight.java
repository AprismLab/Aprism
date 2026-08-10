package com.aprism.api.introspection;

import java.util.List;

/**
 * JVM introspection surface (v26.4-Alpha.4): a typed, stable view over
 * thread stacks, class statistics, heap usage, GC activity, JIT state and
 * VM identity. This is the loader-level foundation the AprismateAgent
 * performance work builds on; it intentionally reads through
 * {@code ManagementFactory} MXBeans so it works on any compliant JVM
 * (including AprismJDK, where deeper seams may later replace it).
 *
 * @author BlockConnect@StarsailsClover
 */
public interface JvmInsight {

    /**
     * @return insights for all live threads at this instant
     */
    List<ThreadInsight> threads();

    /**
     * @return current class-loading statistics
     */
    ClassStats classStats();

    /**
     * @return current heap and non-heap usage
     */
    HeapSummary heap();

    /**
     * @return activity snapshots of all registered garbage collectors
     */
    List<GcSummary> gcCollectors();

    /**
     * @return the JIT compiler state
     */
    CompilationSummary compilation();

    /**
     * @return VM uptime in milliseconds
     */
    long uptimeMs();

    /**
     * @return the VM name (e.g. {@code OpenJDK 64-Bit Server VM})
     */
    String vmName();

    /**
     * @return the VM vendor
     */
    String vmVendor();

    /**
     * @return the Java runtime version
     */
    String javaVersion();
}
