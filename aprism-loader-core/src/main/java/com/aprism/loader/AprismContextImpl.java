package com.aprism.loader;

import java.util.logging.Logger;

import com.aprism.api.AprismContext;
import com.aprism.api.AprismEventBus;
import com.aprism.api.AprismRegistry;
import com.aprism.api.ModContainer;

/**
 * Mod-scoped {@link AprismContext} implementation. Each loaded mod receives
 * its own context instance bound to its container, the shared event bus and
 * registry, and a logger named after the mod id.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismContextImpl implements AprismContext {

    private final ModContainer mod;
    private final AprismEventBus eventBus;
    private final AprismRegistry registry;
    private final Logger logger;

    /**
     * @param mod       the owning mod container
     * @param eventBus  the shared event bus
     * @param registry  the shared registry
     */
    public AprismContextImpl(ModContainer mod, AprismEventBus eventBus, AprismRegistry registry) {
        this.mod = mod;
        this.eventBus = eventBus;
        this.registry = registry;
        this.logger = Logger.getLogger("aprism." + mod.getId());
    }

    @Override
    public ModContainer getMod() {
        return mod;
    }

    @Override
    public AprismEventBus getEventBus() {
        return eventBus;
    }

    @Override
    public AprismRegistry getRegistry() {
        return registry;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }
}
