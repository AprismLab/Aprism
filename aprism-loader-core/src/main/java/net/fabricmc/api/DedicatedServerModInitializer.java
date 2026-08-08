package net.fabricmc.api;

/**
 * Fabric API shim: the entrypoint interface implemented by Fabric mods'
 * {@code server} entrypoints. Provided by Aprism so that genuine Fabric mods
 * can be instantiated and invoked without the real Fabric Loader.
 *
 * <p>Mirrors the Fabric Loader
 * {@code net.fabricmc.api.DedicatedServerModInitializer} contract.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface DedicatedServerModInitializer {

    /**
     * Runs the dedicated-server mod initializer. Called once during the
     * SERVER phase.
     */
    void onInitializeServer();
}
