package com.aprism.api;

import java.util.logging.Logger;

/**
 * Context object passed to extension lifecycle methods. Provides access to the
 * owning extension container, the event bus, the registry, and a logger.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ExtensionContext {

    /**
     * @return the extension container that owns this context
     */
    ExtensionContainer getExtension();

    /**
     * @return the phase-strict event bus shared with mods
     */
    AprismEventBus getEventBus();

    /**
     * @return the global Aprism registry
     */
    AprismRegistry getRegistry();

    /**
     * @return a logger scoped to the owning extension
     */
    Logger getLogger();

    /**
     * Registers a loader-support capability. Called by loader-support
     * extensions to declare which mod folder they handle.
     *
     * @param loaderKey  the loader key (Fa, Fo, N, L, Q)
     * @param modFolder  the mod folder name (e.g. "fabric-mods")
     */
    void registerLoaderSupport(String loaderKey, String modFolder);
}
