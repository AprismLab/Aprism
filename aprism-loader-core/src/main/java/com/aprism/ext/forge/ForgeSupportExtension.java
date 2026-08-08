package com.aprism.ext.forge;

import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;
import com.aprism.loader.ModDiscoverer;

/**
 * Forge-Support Aprism Extension (.aep). Registers Forge loader support so
 * that Aprism scans the {@code forge-mods/} directory and loads genuine
 * (legacy FML) Forge mods.
 *
 * <p>This extension is of type {@code loader-support} with loader key
 * {@code Fo}. When its {@code onInitialize} runs (before any mods are
 * scanned), it declares that the {@code forge-mods/} folder is handled by the
 * Forge loader support, causing phase 2 of mod loading to include it.
 *
 * <p>Like NeoForge, Forge entrypoints are NOT declared in the manifest. They
 * are classes annotated with {@code net.minecraftforge.fml.common.Mod},
 * discovered by bytecode scanning and constructed with an injected
 * {@code IEventBus} via
 * {@link com.aprism.loader.bridge.ForgeEntrypointBridge}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ForgeSupportExtension implements IAprismExtension {

    /** The mod folder handled by Forge loader support. */
    public static final String FORGE_MODS_FOLDER = "forge-mods";

    @Override
    public void onInitialize(ExtensionContext context) {
        context.registerLoaderSupport(ModDiscoverer.FORGE_KEY, FORGE_MODS_FOLDER);
        context.getLogger().info("Forge-Support registered: scanning "
                + FORGE_MODS_FOLDER + "/ for Forge mods (loader key "
                + ModDiscoverer.FORGE_KEY + ")");
    }
}
