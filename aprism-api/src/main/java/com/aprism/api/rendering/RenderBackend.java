package com.aprism.api.rendering;

/**
 * A rendering backend a provider can serve (v26.3-Alpha.5, goal #9).
 * <strong>Experimental / reference-only: no production guarantee.</strong>
 *
 * <p>Ecosystem context as of 2026-08: Mojang has announced the Minecraft
 * Java Edition transition from OpenGL to Vulkan (Vulkan 1.3 is the minimum
 * graphics requirement since 2026-07; macOS runs Vulkan through a
 * translation layer since macOS is deprecating OpenGL). Backends here are
 * therefore ordered by strategic relevance: VULKAN first, then the
 * experimental alternatives METAL (Apple) and DX12 (Windows).
 *
 * @author BlockConnect@StarsailsClover
 */
public enum RenderBackend {

    /** The legacy OpenGL backend (deprecated upstream). */
    OPENGL("opengl"),

    /** Vulkan — the announced upstream successor backend. */
    VULKAN("vulkan"),

    /** Apple Metal (experimental; reachable on macOS via translation). */
    METAL("metal"),

    /** Microsoft DirectX 12 (experimental; Windows only). */
    DX12("dx12");

    private final String manifestValue;

    RenderBackend(String manifestValue) {
        this.manifestValue = manifestValue;
    }

    /**
     * @return the string used in rendering-extension manifests
     */
    public String getManifestValue() {
        return manifestValue;
    }

    /**
     * Parses a manifest backend token (case-insensitive).
     *
     * @param token the token
     * @return the matching backend
     * @throws IllegalArgumentException when unrecognized
     */
    public static RenderBackend parse(String token) {
        if (token == null) {
            throw new IllegalArgumentException("backend token is null");
        }
        for (RenderBackend backend : values()) {
            if (backend.manifestValue.equalsIgnoreCase(token.trim())
                    || backend.name().equalsIgnoreCase(token.trim())) {
                return backend;
            }
        }
        throw new IllegalArgumentException("unknown render backend: " + token);
    }
}
