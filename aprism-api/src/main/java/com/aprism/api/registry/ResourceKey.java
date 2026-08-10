package com.aprism.api.registry;

import java.util.Objects;

/**
 * A namespaced registry identifier ({@code namespace:name}) for typed game
 * content (v26.3-Alpha.2, QA0 gap #2). The namespace is conventionally the
 * owning mod id; the name is the content name. Both segments must be
 * lowercase alphanumeric with underscores/hyphens.
 *
 * @param namespace the namespace (owning mod id)
 * @param name      the content name
 * @author BlockConnect@StarsailsClover
 */
public record ResourceKey(String namespace, String name) {

    private static final String SEGMENT_PATTERN = "[a-z0-9][a-z0-9_-]*";

    /**
     * Creates a key after validating both segments.
     *
     * @param namespace the namespace
     * @param name      the name
     * @throws IllegalArgumentException when either segment is invalid
     */
    public ResourceKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        if (!namespace.matches(SEGMENT_PATTERN)) {
            throw new IllegalArgumentException("invalid namespace: " + namespace);
        }
        if (!name.matches(SEGMENT_PATTERN)) {
            throw new IllegalArgumentException("invalid name: " + name);
        }
    }

    /**
     * Parses a {@code namespace:name} string into a key.
     *
     * @param combined the combined identifier
     * @return the parsed key
     * @throws IllegalArgumentException when the format or segments are invalid
     */
    public static ResourceKey parse(String combined) {
        Objects.requireNonNull(combined, "combined");
        int colon = combined.indexOf(':');
        if (colon <= 0 || colon == combined.length() - 1) {
            throw new IllegalArgumentException("expected namespace:name, got: " + combined);
        }
        return new ResourceKey(combined.substring(0, colon), combined.substring(colon + 1));
    }

    /**
     * @return the combined {@code namespace:name} form
     */
    public String combined() {
        return namespace + ":" + name;
    }
}
