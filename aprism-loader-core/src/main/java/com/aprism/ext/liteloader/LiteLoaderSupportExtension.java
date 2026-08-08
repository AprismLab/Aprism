package com.aprism.ext.liteloader;

import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;
import com.aprism.loader.ModDiscoverer;

/**
 * LiteLoader-Support Aprism Extension (.aep). Registers LiteLoader support so
 * that Aprism scans the {@code liteloader-mods/} directory and loads genuine
 * {@code .litemod} mods.
 *
 * <p>This extension is of type {@code loader-support} with loader key
 * {@code L}. When its {@code onInitialize} runs (before any mods are
 * scanned), it declares that the {@code liteloader-mods/} folder is handled
 * by the LiteLoader support, causing phase 2 of mod loading to include it.
 *
 * <p>LiteLoader entrypoints are NOT declared in the manifest. The mod class
 * implements {@code com.mumfrey.liteloader.core.LiteMod} and is discovered by
 * scanning the {@code .litemod} archive bytecode; initialization is the
 * single {@code init(File)} call, dispatched via
 * {@link com.aprism.loader.bridge.LiteLoaderEntrypointBridge}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LiteLoaderSupportExtension implements IAprismExtension {

    /** The mod folder handled by LiteLoader support. */
    public static final String LITELOADER_MODS_FOLDER = "liteloader-mods";

    @Override
    public void onInitialize(ExtensionContext context) {
        context.registerLoaderSupport(ModDiscoverer.LITELOADER_KEY, LITELOADER_MODS_FOLDER);
        context.getLogger().info("LiteLoader-Support registered: scanning "
                + LITELOADER_MODS_FOLDER + "/ for LiteLoader mods (loader key "
                + ModDiscoverer.LITELOADER_KEY + ")");
    }
}
