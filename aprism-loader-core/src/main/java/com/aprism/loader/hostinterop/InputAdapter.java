package com.aprism.loader.hostinterop;

import java.util.List;

/**
 * Key binding adapter contract (v26.9 roadmap Alpha.7): the
 * host-agnostic shape of "install these key mappings into your input
 * system". A host that advertises {@link HostCapability#KEY_INPUT}
 * implements it; the existing {@code InputSystemBridge} stays the live
 * detail behind native hosts.
 *
 * @author BlockConnect@StarsailsClover
 */
@FunctionalInterface
public interface InputAdapter {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * One key binding request.
     *
     * @param id the binding id ({@code namespace:name})
     * @param defaultKey the default key name (host-native naming)
     * @param category the settings category label
     */
    record KeyBindingRequest(String id, String defaultKey, String category) {
        /** Validates the request. */
        public KeyBindingRequest {
            if (id == null || !id.matches("[a-z0-9][a-z0-9_-]*:[a-z0-9][a-z0-9_-]*")) {
                throw new IllegalArgumentException("binding id must be namespace:name: " + id);
            }
            if (defaultKey == null || defaultKey.isBlank()) {
                throw new IllegalArgumentException("defaultKey required");
            }
            if (category == null) {
                category = "";
            }
        }
    }

    /**
     * Installs the bindings into the host input system.
     *
     * @param requests the binding requests
     * @return the per-binding registration report
     */
    AdapterReport registerKeyBindings(List<KeyBindingRequest> requests);
}
