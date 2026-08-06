package com.aprism.api;

import java.util.logging.Logger;

/**
 * Context object passed to mod lifecycle methods. Provides access to the owning
 * mod container, the phase-strict event bus, the global registry, and a logger
 * scoped to the mod.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface AprismContext {

    /**
     * @return the mod container that owns this context
     */
    ModContainer getMod();

    /**
     * @return the phase-strict event bus
     */
    AprismEventBus getEventBus();

    /**
     * @return the global Aprism registry
     */
    AprismRegistry getRegistry();

    /**
     * @return a logger scoped to the owning mod
     */
    Logger getLogger();
}
