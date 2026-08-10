package com.aprism.api.rendering;

import java.util.List;

/**
 * The rendering-backend provider contract (v26.3-Alpha.5, goal #9).
 * <strong>Experimental / reference-only: no production guarantee.</strong>
 *
 * <p>A {@code rendering-extension} (.aep) implements this interface to
 * expose one or more rendering backends (Vulkan, Metal, DX12) to Aprism.
 * Mods and Aprism query backend availability and capabilities through the
 * rendering registry; they never depend on a concrete provider, so the
 * underlying native rendering library can be swapped without touching
 * callers.
 *
 * <p>The loader core defines only this seam plus the registry. Actual
 * native rendering libraries (Vulkan/Metal/DX12 bindings) ship inside the
 * providing extension; the core never depends on a specific rendering
 * library.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface RenderingProvider {

    /**
     * @return the stable provider identifier (e.g. the providing extension
     *         id); unique within the rendering registry
     */
    String name();

    /**
     * @return the backends this provider can serve
     */
    List<RenderBackend> supportedBackends();

    /**
     * Queries the capability of a backend on the current machine.
     *
     * @param backend the backend to query (must be in
     *                {@link #supportedBackends()})
     * @return the capability, or null when the backend is not actually
     *         available on this machine (driver missing, unsupported OS)
     */
    RenderCapability queryCapability(RenderBackend backend);

    /**
     * @return true when the provider's native libraries are loaded and at
     *         least one supported backend is available
     */
    boolean isReady();
}
