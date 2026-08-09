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

    /**
     * Registers an entrypoint-dispatch handler for a foreign loader. Called by
     * loader-support extensions to supply the bridge that invokes that loader's
     * entrypoint convention. This is the extraction seam that lets loader
     * support live in the AprismRefract sub-project rather than in the Aprism
     * core: the core only ships the {@code LoaderEntrypointHandler} contract
     * and a registry, and delegates dispatch to whatever handler an extension
     * registers for a loader key.
     *
     * <p>The handler is typed as {@code Object} here because the API module
     * must not depend on the loader-core {@code LoaderEntrypointHandler}
     * interface (that would create a circular dependency). The loader-core
     * {@code ExtensionContextImpl} casts it to the handler interface at
     * registration time.
     *
     * @param loaderKey the loader key this handler serves (Fa, Fo, N, L, Q)
     * @param handler   the handler to register; must not be {@code null}
     */
    void registerEntrypointHandler(String loaderKey, Object handler);
}
