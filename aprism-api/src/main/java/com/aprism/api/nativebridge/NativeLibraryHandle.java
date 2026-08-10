package com.aprism.api.nativebridge;

/**
 * A loaded native library handle (v26.4-Alpha.5, native interop bridge).
 * The loader level tracks the library's identity and lifecycle state; the
 * provider owns the OS-level handle.
 *
 * @param name the library name (as passed to load)
 * @param loaded whether the library is currently loaded
 * @param symbolCount the number of symbols resolved so far
 * @author BlockConnect@StarsailsClover
 */
public record NativeLibraryHandle(String name, boolean loaded, int symbolCount) {

    /**
     * Canonical compact constructor: validates the name.
     */
    public NativeLibraryHandle {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("library name must be non-blank");
        }
        if (symbolCount < 0) {
            throw new IllegalArgumentException("symbolCount must be >= 0");
        }
    }
}
