package com.aprism.api.keybinding;

/**
 * A loader-level key-binding registration (Fabric
 * {@code KeyBindingRegistry} parity, v26.3-Alpha.8). A key binding is
 * declared by id with a category and a default key code; the game side maps
 * it onto the real input system, so the loader-level spec stays independent
 * of any client input API. Key codes follow the GLFW key-code convention.
 *
 * @param id the unique key-binding identifier
 * @param category the binding category shown in the controls UI
 * @param defaultKeyCode the default GLFW key code (0 for unbound)
 * @author BlockConnect@StarsailsClover
 */
public record KeyBindingSpec(String id, String category, int defaultKeyCode) {

    /**
     * Canonical compact constructor: validates the id and category.
     */
    public KeyBindingSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("key-binding id must be non-blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("key-binding category must be non-blank");
        }
    }
}
