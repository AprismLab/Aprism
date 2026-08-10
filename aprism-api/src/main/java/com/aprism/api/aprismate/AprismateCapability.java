package com.aprism.api.aprismate;

/**
 * One capability exposed by an AprismateAgent-capable runtime
 * (v26.4-Alpha.6). This is the unit of the capability descriptor defined
 * in the AprismJDK design (§2): Aprism asks the runtime which
 * AprismJDK capabilities it exposes, and the runtime answers with a set
 * of these.
 *
 * @param name the capability name (e.g. {@code class-redefinition},
 *             {@code method-hooks}, {@code jvm-introspection},
 *             {@code native-bridge})
 * @param available whether the capability is usable on the current JVM
 * @param detail a human-readable detail (empty when nothing to add)
 * @author BlockConnect@StarsailsClover
 */
public record AprismateCapability(String name, boolean available, String detail) {

    /**
     * Canonical compact constructor: validates the name.
     */
    public AprismateCapability {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("capability name must be non-blank");
        }
        detail = detail == null ? "" : detail;
    }

    /**
     * @param name the capability name
     * @param available whether it is usable
     * @return a capability with no detail
     */
    public static AprismateCapability of(String name, boolean available) {
        return new AprismateCapability(name, available, "");
    }
}
