package com.aprism.loader.testexts;

import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;

/**
 * Test fixture extension that dynamically registers a loader-support folder
 * during {@link #onInitialize}. Used by
 * {@link com.aprism.loader.AprismRuntimeTest} to verify runtime-driven
 * folder registration via {@link ExtensionContext#registerLoaderSupport}.
 *
 * <p>Registers the loader key {@code "Fa"} with folder {@code "fabric-mods"}
 * on initialization.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class DynamicSupportExtension implements IAprismExtension {

    private static int initCount = 0;

    /**
     * @return how many times onInitialize has been invoked
     */
    public static int getInitCount() {
        return initCount;
    }

    /**
     * Resets the init counter. Call at the start of each test.
     */
    public static void reset() {
        initCount = 0;
    }

    @Override
    public void onInitialize(ExtensionContext context) {
        initCount++;
        context.registerLoaderSupport("Fa", "fabric-mods");
        context.registerLoaderSupport("N", "neoforge-mods");
    }
}
