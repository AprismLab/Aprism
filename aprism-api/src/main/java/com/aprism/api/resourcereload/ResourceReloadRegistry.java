package com.aprism.api.resourcereload;

import java.util.List;

/**
 * Resource-reload listener registration surface providing Fabric
 * {@code ResourceManagerReloadListener} parity (v26.3-Alpha.9). Listeners
 * are registered inside the registration window (opened when the INIT
 * phase begins, frozen once the COMPLETE phase fires); the game side fires
 * {@link #fireReload()} whenever resources are reloaded, fail-safely.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ResourceReloadRegistry {

    /**
     * Registers a resource-reload listener.
     *
     * @param listener the listener
     * @throws IllegalArgumentException if the listener is null or already
     *                                  registered
     * @throws IllegalStateException if the registration window is not open
     */
    void register(ResourceReloadListener listener);

    /**
     * @return whether the registration window is currently open
     */
    boolean isWindowOpen();

    /**
     * @return the registered listeners in registration order
     */
    List<ResourceReloadListener> registeredListeners();

    /**
     * Fires every registered listener fail-safely; a throwing listener is
     * recorded and never aborts the remaining listeners.
     */
    void fireReload();

    /**
     * Removes all registered listeners and closes the window. Called by the
     * loader on shutdown.
     */
    void clear();
}
