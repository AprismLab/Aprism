package com.aprism.loader.testexts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;

/**
 * Test fixture extension that records the full AEP lifecycle
 * (v26.1-Alpha.9, goal #3): {@code onInitialize}, {@code onPostInitialize}
 * and {@code onShutdown}. Used by
 * {@link com.aprism.loader.AprismRuntimeTest} to verify lifecycle hook
 * ordering and shutdown-time context validity. Kept separate from
 * {@link RecordingExtension} so the older single-phase assertions are not
 * affected by the additional POSTINIT/SHUTDOWN log entries.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LifecycleRecordingExtension implements IAprismExtension {

    private static final List<String> GLOBAL_LOG = Collections.synchronizedList(new ArrayList<>());
    private static volatile ExtensionContext lastShutdownContext;

    /**
     * Resets the global log and the captured shutdown context. Call at the
     * start of each test that uses this fixture.
     */
    public static void resetGlobal() {
        GLOBAL_LOG.clear();
        lastShutdownContext = null;
    }

    /**
     * @return the global lifecycle log
     *         (INIT:/POSTINIT:/SHUTDOWN: prefixed with the extension id)
     */
    public static List<String> getGlobalLog() {
        return List.copyOf(GLOBAL_LOG);
    }

    /**
     * @return the context received by the most recent onShutdown call, or null
     */
    public static ExtensionContext getLastShutdownContext() {
        return lastShutdownContext;
    }

    @Override
    public void onInitialize(ExtensionContext context) {
        GLOBAL_LOG.add("INIT:" + context.getExtension().getExtensionId());
    }

    @Override
    public void onPostInitialize(ExtensionContext context) {
        GLOBAL_LOG.add("POSTINIT:" + context.getExtension().getExtensionId());
    }

    @Override
    public void onShutdown(ExtensionContext context) {
        lastShutdownContext = context;
        GLOBAL_LOG.add("SHUTDOWN:" + context.getExtension().getExtensionId());
    }
}
