package com.aprism.loader.modmenu;

import java.util.List;
import java.util.Map;

/**
 * An immutable snapshot entry of the native Aprism mod list
 * (v26.2-Alpha.2, goal #7). Each entry describes one unit (mod or
 * extension) as displayed by the mod list and the future in-game mod menu:
 * identity, metadata, loader provenance, dependencies, and current state.
 *
 * @param id          the unit id (mod id or extension id)
 * @param version     the unit version
 * @param displayName the human-readable display name
 * @param description the unit description
 * @param kind        {@code "mod"} or {@code "extension"}
 * @param loaderKey   the loader key (Aprism-native mods and extensions use
 *                    their own key; foreign-loader mods carry Fa/Fo/N/L/Q),
 *                    never null
 * @param source      the source archive file name, or empty when unknown
 * @param depends     dependency declarations (id -> version range)
 * @param state       the current lifecycle state
 * @author BlockConnect@StarsailsClover
 */
public record ModListEntry(
        String id,
        String version,
        String displayName,
        String description,
        String kind,
        String loaderKey,
        String source,
        Map<String, String> depends,
        ModListState state
) {

    /**
     * Builds an entry with empty dependencies.
     *
     * @param id          the unit id
     * @param version     the unit version
     * @param displayName the display name
     * @param description the description
     * @param kind        {@code "mod"} or {@code "extension"}
     * @param loaderKey   the loader key
     * @param source      the source archive file name
     * @param state       the lifecycle state
     * @return the entry with no dependencies
     */
    public static ModListEntry of(String id, String version, String displayName,
            String description, String kind, String loaderKey, String source,
            ModListState state) {
        return new ModListEntry(id, version, displayName, description, kind,
                loaderKey, source, Map.of(), state);
    }

    /**
     * @return the dependency ids in declaration order
     */
    public List<String> dependencyIds() {
        return List.copyOf(depends.keySet());
    }

    /**
     * @return true when the unit is an Aprism extension
     */
    public boolean isExtension() {
        return "extension".equals(kind);
    }

    /**
     * @return true when the unit failed to load
     */
    public boolean isFailed() {
        return state == ModListState.FAILED;
    }
}
