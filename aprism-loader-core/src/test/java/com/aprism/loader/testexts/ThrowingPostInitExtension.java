package com.aprism.loader.testexts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;

/**
 * Test fixture extension whose {@code onPostInitialize} always throws
 * (v26.1-Alpha.9, goal #3). Used to verify that a failing post-initialize
 * hook isolates only that extension and does not abort the hook pass for
 * the remaining extensions.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ThrowingPostInitExtension implements IAprismExtension {

    private static final List<String> GLOBAL_LOG = Collections.synchronizedList(new ArrayList<>());

    /**
     * Resets the global log. Call at the start of each test that uses this
     * fixture.
     */
    public static void resetGlobal() {
        GLOBAL_LOG.clear();
    }

    /**
     * @return the global lifecycle log (INIT:/POSTINIT-THROW: prefixed with
     *         the extension id)
     */
    public static List<String> getGlobalLog() {
        return List.copyOf(GLOBAL_LOG);
    }

    @Override
    public void onInitialize(ExtensionContext context) {
        GLOBAL_LOG.add("INIT:" + context.getExtension().getExtensionId());
    }

    @Override
    public void onPostInitialize(ExtensionContext context) {
        GLOBAL_LOG.add("POSTINIT-THROW:" + context.getExtension().getExtensionId());
        throw new RuntimeException("synthetic post-init failure");
    }
}
