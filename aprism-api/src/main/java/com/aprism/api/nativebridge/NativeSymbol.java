package com.aprism.api.nativebridge;

/**
 * A native symbol exposed by a loaded native library (v26.4-Alpha.5,
 * native interop bridge). The loader level only knows the symbol's
 * identity; the provider (the FFM backend on AprismJDK) owns the actual
 * address and marshalling.
 *
 * @param library the library name the symbol belongs to
 * @param name the symbol name
 * @param kind the symbol kind
 * @author BlockConnect@StarsailsClover
 */
public record NativeSymbol(String library, String name, Kind kind) {

    /**
     * The kind of native symbol.
     */
    public enum Kind {
        /** A callable function entry point. */
        FUNCTION,
        /** A data object (global variable). */
        DATA
    }

    /**
     * Canonical compact constructor: validates identity fields.
     */
    public NativeSymbol {
        if (library == null || library.isBlank()) {
            throw new IllegalArgumentException("library must be non-blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("symbol name must be non-blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("symbol kind must be non-null");
        }
    }
}
