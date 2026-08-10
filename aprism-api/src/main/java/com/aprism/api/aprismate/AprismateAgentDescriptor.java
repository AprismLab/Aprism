package com.aprism.api.aprismate;

import java.util.List;

/**
 * The answer to "which AprismJDK capabilities does this runtime expose?"
 * (v26.4-Alpha.6). Follows the capability-descriptor contract of the
 * AprismJDK design (§2): Aprism detects whether it runs on an
 * AprismateAgent-capable runtime and upgrades its deep-API behaviour
 * accordingly; on stock JVMs the descriptor reports {@code present=false}
 * with the stock-JVM capability fallbacks.
 *
 * @param present whether an AprismateAgent-capable runtime was detected
 * @param runtimeName the detected runtime name ({@code "AprismJDK"} or
 *                    {@code "stock"})
 * @param capabilities the capability set
 * @author BlockConnect@StarsailsClover
 */
public record AprismateAgentDescriptor(boolean present, String runtimeName,
                                       List<AprismateCapability> capabilities) {

    /**
     * Canonical compact constructor: defensive copies and validation.
     */
    public AprismateAgentDescriptor {
        if (runtimeName == null || runtimeName.isBlank()) {
            throw new IllegalArgumentException("runtime name must be non-blank");
        }
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    /**
     * @param name the capability name
     * @return whether a capability with the given name is available
     */
    public boolean hasCapability(String name) {
        for (AprismateCapability capability : capabilities) {
            if (capability.name().equals(name) && capability.available()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the names of all available capabilities
     */
    public List<String> availableCapabilityNames() {
        return capabilities.stream()
                .filter(AprismateCapability::available)
                .map(AprismateCapability::name)
                .toList();
    }
}
