package com.aprism.api.foreignlang;

/**
 * A bound foreign function: a native symbol plus its typed signature and
 * ownership convention (v26.4-Alpha.8, Cpp2Java / Rust2Java reference).
 * Bindings are what the generated stubs target: a Cpp2Java or Rust2Java
 * generator emits Java code that looks up a binding by id and invokes it
 * through the cross-language runtime.
 *
 * @param id the binding identifier (e.g. {@code cpp:libfoo.add})
 * @param library the native library the symbol lives in
 * @param symbolName the native symbol name
 * @param signature the typed signature
 * @param ownership the memory ownership convention for pointer/string
 *                  values crossing the boundary
 * @param sourceLanguage the language the symbol was compiled from
 * @author BlockConnect@StarsailsClover
 */
public record ForeignBinding(String id, String library, String symbolName,
                             ForeignSignature signature, OwnershipPolicy ownership,
                             SourceLanguage sourceLanguage) {

    /**
     * The language the native symbol was compiled from.
     */
    public enum SourceLanguage {
        /** C or C++. */
        CPP,
        /** Rust. */
        RUST
    }

    /**
     * Canonical compact constructor: validates identity fields.
     */
    public ForeignBinding {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("binding id must be non-blank");
        }
        if (library == null || library.isBlank()) {
            throw new IllegalArgumentException("library must be non-blank");
        }
        if (symbolName == null || symbolName.isBlank()) {
            throw new IllegalArgumentException("symbol name must be non-blank");
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature must be non-null");
        }
        if (ownership == null) {
            throw new IllegalArgumentException("ownership must be non-null");
        }
        if (sourceLanguage == null) {
            throw new IllegalArgumentException("source language must be non-null");
        }
    }
}
