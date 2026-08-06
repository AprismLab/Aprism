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
    CONVERTER("converter");

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
