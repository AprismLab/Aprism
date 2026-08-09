package com.aprism.manifest;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * Parsed representation of an {@code aprism.extension.json} file from a
 * {@code .aep} (Aprism Extension) pack.
 *
 * @param extensionId   the unique extension identifier
 * @param type          the extension type (loader-support, api-extension, platform-adapter, converter)
 * @param aprismRange   SemVer range of compatible Aprism Loader versions
 * @param loaderKey     loader key for loader-support type (Fa, Fo, N, L, Q); null otherwise
 * @param loaderRange   SemVer range of the supported loader version; null for non-loader types
 * @param mcEdit        the Minecraft edition (JE or BE)
 * @param mcVersion     the target Minecraft version
 * @param entrypoint    the extension entrypoint class name (JE) or native symbol (BE)
 * @param provides      capability declarations this extension registers
 * @param depends       other extensions this one depends on (id -> version range)
 * @param priority      initialization order hint; higher priority initializes
 *                      first (v26.1-Alpha.9, goal #3). Defaults to 0 when omitted.
 * @author BlockConnect@StarsailsClover
 */
public record AprismExtensionManifest(
        String extensionId,
        String type,
        String aprismRange,
        String loaderKey,
        String loaderRange,
        String mcEdit,
        String mcVersion,
        String entrypoint,
        List<String> provides,
        Map<String, String> depends,
        int priority
) {

    /**
     * Parses a manifest from a JSON string using Gson.
     *
     * @param json the manifest JSON
     * @return the parsed manifest
     */
    public static AprismExtensionManifest fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, AprismExtensionManifest.class);
    }

    /**
     * Builds a manifest with the default priority (0). Convenience for
     * programmatic construction and tests.
     *
     * @param extensionId the unique extension identifier
     * @param type        the extension type
     * @param aprismRange SemVer range of compatible Aprism versions
     * @param loaderKey   loader key, or null
     * @param loaderRange loader version range, or null
     * @param mcEdit      the Minecraft edition
     * @param mcVersion   the target Minecraft version
     * @param entrypoint  the entrypoint class or symbol
     * @param provides    capability declarations
     * @param depends     extension dependencies
     * @return the manifest with priority 0
     */
    public static AprismExtensionManifest of(String extensionId, String type, String aprismRange,
            String loaderKey, String loaderRange, String mcEdit, String mcVersion,
            String entrypoint, List<String> provides, Map<String, String> depends) {
        return new AprismExtensionManifest(extensionId, type, aprismRange, loaderKey,
                loaderRange, mcEdit, mcVersion, entrypoint, provides, depends, 0);
    }
}
