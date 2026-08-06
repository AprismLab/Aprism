package com.aprism.api;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a mod's manifest data exposed through the API.
 *
 * <p>This is the runtime-facing view of the parsed manifest. The manifest
 * module owns the richer parse-time model; this record carries only what mods
 * and the loader need at runtime. All collections are captured unmodifiable by
 * the loader before being handed out.
 *
 * @author BlockConnect@StarsailsClover
 */
public record ModMetadata(
        String modId,
        String version,
        String displayName,
        String description,
        List<String> authors,
        Environment environment,
        Map<String, List<String>> entrypoints,
        Map<String, String> dependencies,
        List<String> mixins,
        String accessWidener,
        Map<String, Object> platforms,
        List<String> provides
) {

    /**
     * Canonical compact constructor: defensive null handling so the record is
     * always safe to read.
     */
    public ModMetadata {
        authors = authors == null ? List.of() : List.copyOf(authors);
        entrypoints = entrypoints == null ? Map.of() : Map.copyOf(entrypoints);
        dependencies = dependencies == null ? Map.of() : Map.copyOf(dependencies);
        mixins = mixins == null ? List.of() : List.copyOf(mixins);
        platforms = platforms == null ? Map.of() : Map.copyOf(platforms);
        provides = provides == null ? List.of() : List.copyOf(provides);
        environment = environment == null ? Environment.COMMON : environment;
    }

    /**
     * @return {@code displayName} if set, otherwise {@code modId}
     */
    public String effectiveDisplayName() {
        return displayName == null || displayName.isBlank() ? modId : displayName;
    }
}
