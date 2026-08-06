package com.aprism.loader.testexts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;

/**
 * Test fixture extension that records its initialization and any
 * loader-support folders it registers. Used by
 * {@link com.aprism.loader.AprismRuntimeTest} to verify extension lifecycle.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class RecordingExtension implements IAprismExtension {

    private static final List<String> GLOBAL_LOG = Collections.synchronizedList(new ArrayList<>());
    private static final List<String[]> GLOBAL_FOLDERS = Collections.synchronizedList(new ArrayList<>());

    private ExtensionContext context;
    private final List<String[]> registeredFolders = new ArrayList<>();

    /**
     * Resets the global log. Call at the start of each test.
     */
    public static void resetGlobal() {
        GLOBAL_LOG.clear();
        GLOBAL_FOLDERS.clear();
    }

    /**
     * @return the global log of extension initializations
     */
    public static List<String> getGlobalLog() {
        return List.copyOf(GLOBAL_LOG);
    }

    /**
     * @return the global list of registerLoaderSupport calls (each entry is [loaderKey, modFolder])
     */
    public static List<String[]> getGlobalFolders() {
        return List.copyOf(GLOBAL_FOLDERS);
    }

    @Override
    public void onInitialize(ExtensionContext context) {
        this.context = context;
        GLOBAL_LOG.add("INIT:" + context.getExtension().getExtensionId());
    }

    /**
     * Calls {@link ExtensionContext#registerLoaderSupport} with the given
     * arguments, recording the call for later assertions.
     *
     * @param loaderKey the loader key
     * @param modFolder the mod folder
     */
    public void registerFolder(String loaderKey, String modFolder) {
        context.registerLoaderSupport(loaderKey, modFolder);
        registeredFolders.add(new String[]{loaderKey, modFolder});
        GLOBAL_FOLDERS.add(new String[]{loaderKey, modFolder});
    }

    /**
     * @return the context received during onInitialize
     */
    public ExtensionContext getContext() {
        return context;
    }

    /**
     * @return the folders this instance registered
     */
    public List<String[]> getRegisteredFolders() {
        return List.copyOf(registeredFolders);
    }
}
