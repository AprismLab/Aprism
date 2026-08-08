package com.aprism.ext.quilt;

import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;
import com.aprism.loader.ModDiscoverer;

/**
 * Quilt-Support Aprism Extension (.aep). Registers Quilt loader support so
 * that Aprism scans the {@code quilt-mods/} directory and loads genuine Quilt
 * mods.
 *
 * <p>This extension is of type {@code loader-support} with loader key
 * {@code Q}. When its {@code onInitialize} runs (before any mods are
 * scanned), it declares that the {@code quilt-mods/} folder is handled by the
 * Quilt loader support, causing phase 2 of mod loading to include it.
 *
 * <p>Quilt loader ships a built-in Fabric API compatibility layer: Quilt
 * native mods implement {@code net.fabricmc.api.ModInitializer} (and its
 * client/server variants), so Quilt entrypoints are dispatched through the
 * same Fabric-convention bridge used for Fabric mods. The Quilt-native
 * {@code init} entrypoint key is projected to the {@code main} key during
 * manifest projection.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class QuiltSupportExtension implements IAprismExtension {

    /** The mod folder handled by Quilt loader support. */
    public static final String QUILT_MODS_FOLDER = "quilt-mods";

    @Override
    public void onInitialize(ExtensionContext context) {
        context.registerLoaderSupport(ModDiscoverer.QUILT_KEY, QUILT_MODS_FOLDER);
        context.getLogger().info("Quilt-Support registered: scanning "
                + QUILT_MODS_FOLDER + "/ for Quilt mods (loader key "
                + ModDiscoverer.QUILT_KEY + ")");
    }
}
