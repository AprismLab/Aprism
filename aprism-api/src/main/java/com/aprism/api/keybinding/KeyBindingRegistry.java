package com.aprism.api.keybinding;

import java.util.List;

/**
 * Key-binding registration surface providing Fabric
 * {@code KeyBindingRegistry} parity (v26.3-Alpha.8). Mods register key
 * bindings inside the registration window (opened when the INIT phase
 * begins, frozen once the COMPLETE phase fires); registrations outside the
 * window are rejected fail-closed.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface KeyBindingRegistry {

    /**
     * Registers a key binding.
     *
     * @param spec the key-binding specification
     * @throws IllegalArgumentException if the spec is invalid or its id is
     *                                  already registered
     * @throws IllegalStateException if the registration window is not open
     */
    void register(KeyBindingSpec spec);

    /**
     * @return whether the registration window is currently open
     */
    boolean isWindowOpen();

    /**
     * @return the registered key bindings in registration order
     */
    List<KeyBindingSpec> registeredKeyBindings();

    /**
     * Removes all registered key bindings and closes the window. Called by
     * the loader on shutdown.
     */
    void clear();
}
