package com.aprism.api.introspection;

/**
 * A snapshot of the JIT compiler state (v26.4-Alpha.4, JVM introspection
 * API).
 *
 * @param compilerName the JIT compiler name ({@code null} when no
 *                     compiler is available)
 * @param totalCompileTimeMs approximate accumulated compilation time in
 *                           milliseconds ({@code -1} if unsupported)
 * @param jitAvailable whether a JIT compiler is present in this VM
 * @author BlockConnect@StarsailsClover
 */
public record CompilationSummary(String compilerName, long totalCompileTimeMs,
                                 boolean jitAvailable) {
}
