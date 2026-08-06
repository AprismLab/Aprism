package com.aprism.manifest;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * Parsed representation of an {@code aprism.manifest.json} file.
 * <p>
 * This record mirrors the on-disk manifest structure and is the canonical form
 * consumed by the loader, validator, and dependency resolver.
 * </p>
 *
 * @param schemaVersion  the manifest schema version
 * @param id             the unique, lowercase mod identifier
 * @param version        the mod version (SemVer)
 * @param displayName    the human-readable display name
 * @param description    the mod description
 * @param environment    the target environment ({@code client}, {@code server}, or {@code *})
 * @param entrypoints    map of entrypoint key to list of fully-qualified class names
 * @param mixins         list of mixin config files
 * @param depends        map of dependency id to version range
 * @param platforms      map of platform id to platform-specific provider
 * @param accessWidener  the access widener file path, or {@code null}
 * @param provides       list of alternative ids this mod provides
 * @param custom         map of custom metadata
 * @author BlockConnect@StarsailsClover
 */
public record AprismManifest(
        int schemaVersion,
        String id,
        String version,
        String displayName,
        String description,
        String environment,
        Map<String, List<String>> entrypoints,
        List<String> mixins,
        Map<String, String> depends,
        Map<String, PlatformProvider> platforms,
        String accessWidener,
        List<String> provides,
        Map<String, Object> custom
) {

    /**
     * Platform-specific provider describing how a mod integrates with a given
     * platform (e.g. {@code fabric}, {@code neoforge}, {@code forge}).
     *
     * @param entrypoint   the platform-specific entrypoint class
     * @param mixins       platform-specific mixin configs
     * @param dependencies platform-specific dependency overrides
     */
    public record PlatformProvider(String entrypoint, List<String> mixins, Map<String, String> dependencies) {
    }

    /**
     * Parses a manifest from a JSON string using Gson.
     *
     * @param json the manifest JSON
     * @return the parsed manifest
     */
    public static AprismManifest fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, AprismManifest.class);
    }
}
