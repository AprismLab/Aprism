package com.aprism.api.resourcereload;

/**
 * A loader-level resource-reload listener (Fabric
 * {@code ResourceManagerReloadListener} parity, v26.3-Alpha.9). The game
 * side calls {@link #onResourceReload()} whenever resources are reloaded
 * (datapack reload, resource-pack refresh); implementations must be
 * fail-safe, since a throwing listener must not abort the game's reload.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ResourceReloadListener {

    /**
     * Invoked when the game reloads resources.
     */
    void onResourceReload();
}
