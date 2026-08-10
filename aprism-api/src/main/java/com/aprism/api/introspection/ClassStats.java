package com.aprism.api.introspection;

/**
 * A snapshot of class-loading statistics (v26.4-Alpha.4, JVM
 * introspection API).
 *
 * @param loadedClassCount classes currently loaded
 * @param unloadedClassCount classes unloaded since startup
 * @param totalLoadedClassCount classes loaded since startup
 * @author BlockConnect@StarsailsClover
 */
public record ClassStats(long loadedClassCount, long unloadedClassCount,
                         long totalLoadedClassCount) {
}
