package com.aprism.api;

/**
 * Lifecycle interface for Aprism Extensions (.aep). Extensions enhance Aprism
 * itself and load BEFORE mods.
 * <p>
 * Extensions are NOT mods. An extension can provide loader support, extend the
 * API, adapt Aprism to a platform, or provide a conversion pipeline.
 * </p>
 *
 * @author BlockConnect@StarsailsClover
 */
public interface IAprismExtension {

    /**
     * Called during extension initialization, before any mods are scanned.
     * The extension should register its capabilities (e.g. loader runtime,
     * API surfaces, platform hooks) with the context.
     *
     * @param context the extension context providing access to the runtime
     */
    void onInitialize(ExtensionContext context);
}
