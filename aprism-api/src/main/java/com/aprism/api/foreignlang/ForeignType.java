package com.aprism.api.foreignlang;

/**
 * The primitive ABI types supported by the cross-language bridges
 * (v26.4-Alpha.8, Cpp2Java / Rust2Java reference). This is the shared
 * ABI-mapping vocabulary named in the AprismJDK design (§6): both the
 * Cpp2Java and Rust2Java binding generators emit signatures in terms of
 * these types, and the FFM backend maps each one onto the corresponding
 * {@code java.lang.foreign} value layout.
 *
 * <p>Structs are not first-class values in this vocabulary: they cross
 * the boundary as {@link #POINTER} with an agreed layout, keeping the
 * ABI surface small and stable.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum ForeignType {

    /** void (return-only). */
    VOID,
    /** C/C++/Rust {@code bool}. */
    BOOL,
    /** 8-bit signed integer ({@code int8_t} / {@code i8}). */
    I8,
    /** 16-bit signed integer ({@code int16_t} / {@code i16}). */
    I16,
    /** 32-bit signed integer ({@code int32_t} / {@code i32}). */
    I32,
    /** 64-bit signed integer ({@code int64_t} / {@code i64}). */
    I64,
    /** 32-bit IEEE 754 float ({@code float} / {@code f32}). */
    F32,
    /** 64-bit IEEE 754 double ({@code double} / {@code f64}). */
    F64,
    /** Opaque pointer ({@code void*} / {@code *mut c_void}). */
    POINTER,
    /** NUL-terminated C string ({@code const char*} /
     *  {@code *const c_char}). */
    STRING;

    /**
     * @return whether this type may appear as a return type
     */
    public boolean isReturnType() {
        return true;
    }

    /**
     * @return whether this type may appear as a parameter type
     */
    public boolean isParameterType() {
        return this != VOID;
    }
}
