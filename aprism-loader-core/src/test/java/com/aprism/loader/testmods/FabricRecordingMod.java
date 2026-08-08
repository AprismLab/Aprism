package com.aprism.loader.testmods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;

/**
 * Test fixture Fabric mod implementing the Fabric entrypoint interfaces
 * (via the Aprism shim). Records every Fabric-convention entrypoint callback
 * it receives so tests can assert the bridge invoked the right methods.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class FabricRecordingMod implements ModInitializer, ClientModInitializer,
        DedicatedServerModInitializer {

    private static final List<String> GLOBAL_CALLS = Collections.synchronizedList(new ArrayList<>());

    /**
     * Resets the global call log.
     */
    public static void resetGlobal() {
        GLOBAL_CALLS.clear();
    }

    /**
     * @return the global call log
     */
    public static List<String> getGlobalCalls() {
        return List.copyOf(GLOBAL_CALLS);
    }

    @Override
    public void onInitialize() {
        GLOBAL_CALLS.add("main");
    }

    @Override
    public void onInitializeClient() {
        GLOBAL_CALLS.add("client");
    }

    @Override
    public void onInitializeServer() {
        GLOBAL_CALLS.add("server");
    }
}
