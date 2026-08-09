package com.aprism.loader.loaderext;

import com.aprism.api.AprismPhase;
import com.aprism.loader.LoadedModContainer;

/**
 * Pluggable entrypoint-dispatch handler for a single foreign mod loader.
 *
 * <p>Aprism's native dispatch knows how to invoke {@code IAprismMod}
 * entrypoints. Every other loader (Fabric, NeoForge, Forge, Quilt,
 * LiteLoader, ...) follows its own entrypoint convention. Historically the
 * bridges for those conventions lived inside {@code aprism-loader-core};
 * per FACT.md they are being extracted into the AprismRefract sub-project.
 *
 * <p>This SPI is the extraction seam. A loader-support extension registers a
 * {@code LoaderEntrypointHandler} for its loader key; when the runtime
 * dispatches entrypoints for a mod discovered under that loader key, it
 * delegates to the registered handler instead of (or before) any built-in
 * behaviour. Handlers are looked up by {@link #loaderKey()}.
 *
 * <p>Implementations live in the owning AprismRefract branch and are bundled
 * in that branch's {@code .aep}; the Aprism core only ships this interface
 * and the registry.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface LoaderEntrypointHandler {

    /**
     * @return the loader key this handler serves (e.g. {@code "Fa"},
     *         {@code "N"}, {@code "Fo"}, {@code "Q"}, {@code "L"}). Must be
     *         stable and unique per loader.
     */
    String loaderKey();

    /**
     * Invokes the loader's entrypoint convention for the given mod and phase.
     *
     * <p>Called once per (mod, phase) pair during
     * {@code AprismRuntime.invokeEntrypoints}. The handler owns the full
     * convention: locating the entrypoint class(es), instantiating them, and
     * invoking the correct method for the phase. It may consult
     * {@link LoadedModContainer#getSourcePath()} to scan the mod archive.
     *
     * @param container the mod container (manifest + source path + loader key)
     * @param phase     the lifecycle phase being dispatched
     */
    void invoke(LoadedModContainer container, AprismPhase phase);

    /**
     * Whether this handler takes full responsibility for the loader's
     * entrypoints, suppressing the Aprism-native {@code IAprismMod} fallback
     * for mods under this loader key.
     *
     * <p>Return {@code true} for loaders whose mods never implement
     * {@code IAprismMod} (Fabric, NeoForge, Forge, Quilt, LiteLoader). Return
     * {@code false} to let the runtime still try the native dispatch after
     * this handler runs.
     *
     * @return {@code true} if the handler fully owns dispatch for its loader
     */
    default boolean isExclusive() {
        return true;
    }
}
