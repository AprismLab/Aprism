package com.aprism.loader.resourcereload;

import com.aprism.api.resourcereload.ResourceReloadListener;
import com.aprism.api.resourcereload.ResourceReloadRegistry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe implementation of {@link ResourceReloadRegistry} with a
 * one-shot registration window and fail-safe reload firing (v26.3-Alpha.9,
 * Fabric ResourceManagerReloadListener parity). The window opens when the
 * INIT phase begins and freezes when the COMPLETE phase fires;
 * {@link #fireReload()} invokes every listener, logging and skipping any
 * listener that throws so the game reload is never aborted.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ResourceReloadRegistryImpl implements ResourceReloadRegistry {

    private static final Logger LOG = Logger.getLogger("aprism.resourcereload");

    private final List<ResourceReloadListener> listeners = new CopyOnWriteArrayList<>();
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
    public void register(ResourceReloadListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("resource-reload listener must be non-null");
        }
        if (!windowOpen) {
            throw new IllegalStateException(
                    "resource-reload listener registration outside the registration window");
        }
        synchronized (listeners) {
            if (listeners.contains(listener)) {
                throw new IllegalArgumentException("resource-reload listener already registered");
            }
            listeners.add(listener);
        }
    }

    @Override
    public boolean isWindowOpen() {
        return windowOpen;
    }

    @Override
    public List<ResourceReloadListener> registeredListeners() {
        return List.copyOf(listeners);
    }

    @Override
    public void fireReload() {
        for (ResourceReloadListener listener : listeners) {
            try {
                listener.onResourceReload();
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "resource-reload listener failed", failure);
            }
        }
    }

    @Override
    public void clear() {
        listeners.clear();
        windowOpen = false;
        frozen = false;
    }
}
