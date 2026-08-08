package com.aprism.loader.testmods;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mumfrey.liteloader.core.LiteMod;

/**
 * Test fixture LiteLoader mod. Implements the LiteLoader {@code LiteMod} shim
 * so {@link com.aprism.loader.bridge.LiteLoaderEntrypointBridge} can discover
 * it by bytecode scanning. Records the {@code init(File)} call and the config
 * folder it received.
 *
 * <p>Because the integration test embeds this class's bytes into the
 * {@code .litemod} archive, the runtime defines a child-classloader copy.
 * Tests therefore observe the constructed instance through
 * {@code container.getInstance()} and reflection.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LiteModRecordingMod implements LiteMod {

    private static volatile boolean initialized;
    private static final List<String> GLOBAL_CALLS = Collections.synchronizedList(new ArrayList<>());
    private File receivedConfigFolder;

    /**
     * Resets the static state. Call at the start of each test.
     */
    public static void resetGlobal() {
        initialized = false;
        GLOBAL_CALLS.clear();
    }

    /**
     * @return whether init(File) was invoked (test-classloader copy only)
     */
    public static boolean wasInitialized() {
        return initialized;
    }

    /**
     * @return the global call log
     */
    public static List<String> getGlobalCalls() {
        return List.copyOf(GLOBAL_CALLS);
    }

    @Override
    public String getName() {
        return "litemod";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void init(File configFolder) {
        initialized = true;
        receivedConfigFolder = configFolder;
        GLOBAL_CALLS.add("init");
    }

    /**
     * @return the config folder passed to {@code init(File)}
     */
    public File getReceivedConfigFolder() {
        return receivedConfigFolder;
    }
}
