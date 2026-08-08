package com.aprism.loader.testmods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;

/**
 * Test fixture Quilt mod entrypoint. Quilt loader ships a built-in Fabric API
 * compatibility layer, so a genuine Quilt mod implements
 * {@code net.fabricmc.api.ModInitializer} (and the client/server variants);
 * its entrypoints are declared in {@code quilt.mod.json} under the
 * {@code init}/{@code client}/{@code server} keys. The Aprism projection maps
 * {@code init} to {@code main}, so the Fabric-convention bridge dispatches
 * Quilt mods without any Quilt-specific code path.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class QuiltRecordingMod implements ModInitializer, ClientModInitializer,
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
