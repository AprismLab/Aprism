package net.fabricmc.api;

/**
 * Fabric API shim: the entrypoint interface implemented by Fabric mods'
 * {@code main} entrypoints. Provided by Aprism so that genuine Fabric mods can
 * be instantiated and invoked without the real Fabric Loader on the classpath.
 *
 * <p>Mirrors the Fabric Loader {@code net.fabricmc.api.ModInitializer}
 * contract: a single no-arg {@code onInitialize()} called during mod
 * initialization.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ModInitializer {

    /**
     * Runs the mod initializer. Called once during the INIT phase.
     */
    void onInitialize();
}
