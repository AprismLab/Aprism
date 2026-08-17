package com.aprism.loader.keybinding;

import java.util.List;
import java.util.logging.Logger;

import com.aprism.api.keybinding.KeyBindingRegistry;
import com.aprism.api.keybinding.KeyBindingSpec;

/**
 * Bridges registered {@link KeyBindingSpec} entries into the Minecraft input
 * system (v26.5-Alpha.5).
 *
 * <p>The v26.3-Alpha.8 key-binding registration surface is a registration-only
 * contract. This installer takes the frozen key-binding list and binds each
 * entry to the real MC input system through a platform-supplied
 * {@link InputSystemBridge}.
 *
 * <p>The bridge is supplied by the platform adapter layer (which knows the
 * running MC version's input API, e.g. GLFW key callbacks or MC's
 * {@code KeyMapping} class). The loader core never references MC input
 * classes directly, keeping it version-agnostic.
 *
 * <p>Binding happens when {@link #bindKeyBindings()} is called — typically
 * during the CLIENT phase. If no bridge is attached, binding is a no-op
 * (fail-closed).
 *
 * <p>All binding is fail-safe: a throwing bridge binding isolates only the
 * failing key binding; the remaining bindings still bind.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class KeyBindingBindingInstaller {

    private static final Logger LOG = Logger.getLogger("aprism.keybinding");

    private final KeyBindingRegistry registry;
    private InputSystemBridge bridge;

    /**
     * @param registry the key-binding registry holding the frozen binding list
     */
    public KeyBindingBindingInstaller(KeyBindingRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.registry = registry;
    }

    /**
     * Attaches the platform-supplied input system bridge.
     *
     * @param bridge the input system bridge, or null to detach
     */
    public void setBridge(InputSystemBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * @return whether an input system bridge is currently attached
     */
    public boolean isBridgeAttached() {
        return bridge != null;
    }

    /**
     * Binds all registered key bindings to the MC input system through the
     * attached bridge. Each binding is bound individually so a failing binding
     * isolates only that key binding.
     *
     * @return the number of key bindings successfully bound
     */
    public int bindKeyBindings() {
        if (bridge == null) {
            LOG.info("No input system bridge attached; "
                    + registry.registeredKeyBindings().size()
                    + " key binding(s) registered but not bound");
            return 0;
        }
        List<KeyBindingSpec> bindings = registry.registeredKeyBindings();
        int bound = 0;
        for (KeyBindingSpec spec : bindings) {
            try {
                bridge.bind(spec);
                bound++;
            } catch (RuntimeException e) {
                LOG.warning("Failed to bind key binding '" + spec.id()
                        + "': " + e.getMessage());
            }
        }
        if (bound > 0) {
            LOG.info("Bound " + bound + "/" + bindings.size()
                    + " key binding(s) to the MC input system");
        }
        return bound;
    }

    /**
     * Unbinds all key bindings through the bridge (if attached) and clears
     * state. Called on runtime shutdown.
     */
    public void unbindAll() {
        if (bridge != null) {
            try {
                bridge.unbindAll();
            } catch (RuntimeException e) {
                LOG.warning("Failed to unbind key bindings: " + e.getMessage());
            }
        }
        bridge = null;
    }
}
