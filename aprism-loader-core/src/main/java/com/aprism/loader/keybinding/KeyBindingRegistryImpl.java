package com.aprism.loader.keybinding;

import com.aprism.api.keybinding.KeyBindingRegistry;
import com.aprism.api.keybinding.KeyBindingSpec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe implementation of {@link KeyBindingRegistry} with a one-shot
 * registration window (v26.3-Alpha.8, Fabric parity). The window opens when
 * the INIT phase begins and freezes when the COMPLETE phase fires;
 * registrations outside the window are rejected fail-closed and duplicate
 * key-binding ids are refused.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class KeyBindingRegistryImpl implements KeyBindingRegistry {

    private final List<KeyBindingSpec> keyBindings = new CopyOnWriteArrayList<>();
    private volatile boolean windowOpen;
    private volatile boolean frozen;

    /**
     * Opens the registration window (INIT phase).
     */
    public void openWindow() {
        this.windowOpen = true;
    }

    /**
     * Freezes the registration window (COMPLETE phase).
     */
    public void freezeWindow() {
        this.windowOpen = false;
        this.frozen = true;
    }

    /**
     * @return whether the window was frozen after being open
     */
    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public void register(KeyBindingSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("key-binding spec must be non-null");
        }
        if (!windowOpen) {
            throw new IllegalStateException(
                    "key-binding registration outside the registration window: " + spec.id());
        }
        synchronized (keyBindings) {
            for (KeyBindingSpec existing : keyBindings) {
                if (existing.id().equals(spec.id())) {
                    throw new IllegalArgumentException("duplicate key-binding id: " + spec.id());
                }
            }
            keyBindings.add(spec);
        }
    }

    @Override
    public boolean isWindowOpen() {
        return windowOpen;
    }

    @Override
    public List<KeyBindingSpec> registeredKeyBindings() {
        return List.copyOf(keyBindings);
    }

    @Override
    public void clear() {
        keyBindings.clear();
        windowOpen = false;
        frozen = false;
    }
}
