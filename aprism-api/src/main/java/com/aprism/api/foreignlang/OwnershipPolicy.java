package com.aprism.api.foreignlang;

/**
 * The lifecycle convention for memory crossing the language boundary
 * (v26.4-Alpha.8, Cpp2Java / Rust2Java reference). Answers the question
 * "who allocates, who frees?" from the AprismJDK design (§6) for each
 * pointer or string that crosses the boundary.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum OwnershipPolicy {

    /**
     * The Java side owns the memory and must free it (through the bridge's
     * allocator) after use.
     */
    CALLER_FREES,

    /**
     * The native side owns the memory; the Java side must not free it and
     * must treat the pointer as borrowed for the duration of the call.
     */
    CALLEE_OWNS,

    /**
     * The memory lives in an arena scope managed by the bridge runtime;
     * neither side frees it explicitly — the arena's close releases it.
     */
    ARENA_SCOPED
}
