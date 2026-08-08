package com.aprism.ext.fabric;

import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;
import com.aprism.loader.ModDiscoverer;

/**
 * Fabric-Support Aprism Extension (.aep). Registers Fabric loader support so
 * that Aprism scans the {@code fabric-mods/} directory and loads genuine
 * Fabric mods, bridging their {@code ModInitializer}-style entrypoints onto
 * the Aprism lifecycle via {@link com.aprism.loader.bridge.FabricEntrypointBridge}.
 *
 * <p>This extension is of type {@code loader-support} with loader key
 * {@code Fa}. When its {@code onInitialize} runs (before any mods are
 * scanned), it declares that the {@code fabric-mods/} folder is handled by the
 * Fabric loader support, causing phase 2 of mod loading to include it.
 *
 * <p>Fabric mods discovered in {@code fabric-mods/} retain their own
 * {@code fabric.mod.json} manifests (projected to Aprism manifests) and their
 * entrypoints are invoked through the Fabric convention rather than the Aprism
 * {@code IAprismMod} contract.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class FabricSupportExtension implements IAprismExtension {

    /** The mod folder handled by Fabric loader support. */
    public static final String FABRIC_MODS_FOLDER = "fabric-mods";

    @Override
    public void onInitialize(ExtensionContext context) {
        context.registerLoaderSupport(ModDiscoverer.FABRIC_KEY, FABRIC_MODS_FOLDER);
        context.getLogger().info("Fabric-Support registered: scanning "
                + FABRIC_MODS_FOLDER + "/ for Fabric mods (loader key "
                + ModDiscoverer.FABRIC_KEY + ")");
    }
}
