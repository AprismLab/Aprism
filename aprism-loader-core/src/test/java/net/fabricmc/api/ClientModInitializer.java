package net.fabricmc.api;

/**
 * Fabric API shim: the entrypoint interface implemented by Fabric mods'
 * {@code client} entrypoints. Provided by Aprism so that genuine Fabric mods
 * can be instantiated and invoked without the real Fabric Loader.
 *
 * <p>Mirrors the Fabric Loader {@code net.fabricmc.api.ClientModInitializer}
 * contract.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ClientModInitializer {

    /**
     * Runs the client mod initializer. Called once during the CLIENT phase.
     */
    void onInitializeClient();
}
