package com.aprism.ext.neoforge;

import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;
import com.aprism.loader.ModDiscoverer;

/**
 * NeoForge-Support Aprism Extension (.aep). Registers NeoForge loader support
 * so that Aprism scans the {@code neoforge-mods/} directory and loads genuine
 * NeoForge mods.
 *
 * <p>This extension is of type {@code loader-support} with loader key
 * {@code N}. When its {@code onInitialize} runs (before any mods are
 * scanned), it declares that the {@code neoforge-mods/} folder is handled by
 * the NeoForge loader support, causing phase 2 of mod loading to include it.
 *
 * <p>Unlike Fabric, NeoForge entrypoints are NOT declared in the manifest.
 * They are classes annotated with {@code net.neoforged.fml.common.Mod},
 * discovered by bytecode scanning and constructed with an injected
 * {@code IEventBus} via
 * {@link com.aprism.loader.bridge.NeoForgeEntrypointBridge}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class NeoForgeSupportExtension implements IAprismExtension {

    /** The mod folder handled by NeoForge loader support. */
    public static final String NEOFORGE_MODS_FOLDER = "neoforge-mods";

    @Override
    public void onInitialize(ExtensionContext context) {
        context.registerLoaderSupport(ModDiscoverer.NEOFORGE_KEY, NEOFORGE_MODS_FOLDER);
        context.getLogger().info("NeoForge-Support registered: scanning "
                + NEOFORGE_MODS_FOLDER + "/ for NeoForge mods (loader key "
                + ModDiscoverer.NEOFORGE_KEY + ")");
    }
}
