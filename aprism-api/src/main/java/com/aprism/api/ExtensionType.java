package com.aprism.api;

/**
 * Categorizes the purpose of an Aprism Extension (.aep).
 *
 * @author BlockConnect@StarsailsClover
 */
public enum ExtensionType {

    /** Provides a mod loader runtime (Fabric, Forge, NeoForge, LiteLoader, Quilt). */
    LOADER_SUPPORT("loader-support"),

    /** Extends the Aprism API beyond the core surface. */
    API_EXTENSION("api-extension"),

    /** Adapts Aprism to a platform or version boundary. */
    PLATFORM_ADAPTER("platform-adapter"),

    /** Provides a format conversion pipeline (e.g. JE-to-BE). */
    CONVERTER("converter"),

    /**
     * Provides an AI assistant capability (v26.3-Alpha.4, goal #8).
     * Experimental / reference-only: no production guarantee.
     */
    AI_EXTENSION("ai-extension"),

    /**
     * Provides a rendering backend capability (v26.3-Alpha.5, goal #9).
     * Experimental / reference-only: no production guarantee.
     */
    RENDERING_EXTENSION("rendering-extension");

    private final String manifestValue;

    ExtensionType(String manifestValue) {
        this.manifestValue = manifestValue;
    }

    /**
     * @return the string used in aprism.extension.json
     */
    public String getManifestValue() {
        return manifestValue;
    }

    /**
     * Parses a manifest type token into an {@link ExtensionType}.
     *
     * @param token the token from the manifest
     * @return the matching type
     * @throws IllegalArgumentException if the token is unrecognized
     */
    public static ExtensionType parse(String token) {
        if (token == null) {
            throw new IllegalArgumentException("extension type is null");
        }
        for (ExtensionType t : values()) {
            if (t.manifestValue.equalsIgnoreCase(token.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown extension type: " + token);
    }
}
