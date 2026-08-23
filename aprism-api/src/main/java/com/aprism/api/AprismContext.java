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

    /**
     * @return the inter-mod communication surface
     *         (Forge/NeoForge parity, v26.3-Alpha.7)
     */
    com.aprism.api.imc.InterModComms getInterModComms();

    /**
     * @return the command registration surface (Fabric parity, v26.3-Alpha.8)
     */
    default com.aprism.api.commands.CommandRegistration getCommandRegistration() {
        throw new UnsupportedOperationException(
                "getCommandRegistration not supported by this context implementation");
    }

    /**
     * @return the typed item content registry whose entries bind into the
     *         live game registries (v26.7-Alpha.1)
     */
    default com.aprism.api.registry.TypedRegistry<com.aprism.api.registry.ItemContent> getItemRegistry() {
        throw new UnsupportedOperationException(
                "getItemRegistry not supported by this context implementation");
    }

    /**
     * @return the typed block content registry whose entries bind into the
     *         live game registries (v26.7-Alpha.1)
     */
    default com.aprism.api.registry.TypedRegistry<com.aprism.api.registry.BlockContent> getBlockRegistry() {
        throw new UnsupportedOperationException(
                "getBlockRegistry not supported by this context implementation");
    }
}
