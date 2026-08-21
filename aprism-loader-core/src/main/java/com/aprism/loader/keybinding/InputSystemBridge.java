package com.aprism.loader.keybinding;

import com.aprism.api.keybinding.KeyBindingSpec;

/**
 * Platform-supplied bridge that binds {@link KeyBindingSpec} entries to the
 * Minecraft input system (v26.5-Alpha.5).
 *
 * <p>The implementation is provided by the platform adapter layer (which
 * knows the running MC version's input API, e.g. GLFW key callbacks or
 * MC's {@code KeyMapping} class). The loader core never references MC input
 * classes directly.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface InputSystemBridge {

    /**
     * Binds a single key-binding spec to the MC input system.
     *
     * @param spec the key-binding specification (id, category, default key code)
     * @throws RuntimeException if the binding fails (caught by the installer)
     */
    void bind(KeyBindingSpec spec);

    /**
     * Unbinds all previously bound key bindings. Called on runtime shutdown.
     */
    void unbindAll();
}
