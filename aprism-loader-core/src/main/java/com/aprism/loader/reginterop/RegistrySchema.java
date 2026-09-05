package com.aprism.loader.reginterop;

import com.aprism.api.registry.ResourceKey;

import java.util.Map;
import java.util.Set;

/**
 * Uniform registry contribution schema (v26.9 roadmap Alpha.4): one shape
 * every content provider translates into, regardless of the originating
 * loader API. Validation is fail-closed per entry - unknown or invalid
 * properties never reach the runtime registries.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class RegistrySchema {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** The game content kinds the schema can describe. */
    public enum Kind {
        ITEM, BLOCK, ENTITY
    }

    /** Well-known property keys per kind (all values are strings). */
    public static final Set<String> ITEM_PROPERTIES =
            Set.of("maxStack");
    public static final Set<String> BLOCK_PROPERTIES =
            Set.of("hardness", "resistance", "luminance");
    public static final Set<String> ENTITY_PROPERTIES =
            Set.of("factoryClass", "clientTracked", "height", "width");

    /** One validated content entry. */
    public record Entry(Kind kind, ResourceKey id, Map<String, String> properties) {

        /**
         * Validating canonical constructor: id syntax plus per-kind
         * property allow-list and value ranges. Throws fail-closed.
         */
        public Entry {
            requireId(id);
            requireProperties(kind, properties);
        }

        private static void requireId(ResourceKey id) {
            if (id == null) {
                throw new IllegalArgumentException("id is required");
            }
            if (id.namespace().isBlank() || id.name().isBlank()) {
                throw new IllegalArgumentException(
                        "id namespace and name must be non-blank");
            }
            String name = id.name();
            for (char c : name.toCharArray()) {
                boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                        || c == '_' || c == '/' || c == '.';
                if (!ok) {
                    throw new IllegalArgumentException(
                            "illegal character in id name: " + name);
                }
            }
        }

        private static void requireProperties(Kind kind,
                Map<String, String> properties) {
            Set<String> allowed = switch (kind) {
                case ITEM -> ITEM_PROPERTIES;
                case BLOCK -> BLOCK_PROPERTIES;
                case ENTITY -> ENTITY_PROPERTIES;
            };
            for (Map.Entry<String, String> e : properties.entrySet()) {
                if (!allowed.contains(e.getKey())) {
                    throw new IllegalArgumentException("unknown property '"
                            + e.getKey() + "' for " + kind);
                }
                if (e.getValue() == null || e.getValue().isBlank()) {
                    throw new IllegalArgumentException("property '"
                            + e.getKey() + "' requires a value");
                }
            }
            switch (kind) {
                case ITEM -> requireIntRange(properties, "maxStack", 1, 99);
                case BLOCK -> {
                    requireFloatRange(properties, "hardness", 0f, 50f);
                    requireFloatRange(properties, "resistance", 0f, 1200f);
                    requireIntRange(properties, "luminance", 0, 15);
                }
                case ENTITY -> {
                    String factory = properties.get("factoryClass");
                    if (factory == null) {
                        throw new IllegalArgumentException(
                                "entity requires factoryClass");
                    }
                    requireIntRange(properties, "height", 0, 1024);
                    requireIntRange(properties, "width", 0, 1024);
                }
            }
        }

        private static void requireIntRange(Map<String, String> properties,
                String key, int min, int max) {
            String raw = properties.get(key);
            if (raw == null) {
                return;
            }
            try {
                int value = Integer.parseInt(raw);
                if (value < min || value > max) {
                    throw new IllegalArgumentException(key + " must be "
                            + min + ".." + max + ", got " + value);
                }
            } catch (NumberFormatException malformed) {
                throw new IllegalArgumentException(key
                        + " must be an integer, got: " + raw);
            }
        }

        private static void requireFloatRange(Map<String, String> properties,
                String key, float min, float max) {
            String raw = properties.get(key);
            if (raw == null) {
                return;
            }
            try {
                float value = Float.parseFloat(raw);
                if (value < min || value > max) {
                    throw new IllegalArgumentException(key + " must be "
                            + min + ".." + max + ", got " + value);
                }
            } catch (NumberFormatException malformed) {
                throw new IllegalArgumentException(key
                        + " must be a float, got: " + raw);
            }
        }
    }

    /** A failed contribution (fail-closed record of why). */
    public record Rejection(ResourceKey id, String reason) {
    }

    private RegistrySchema() {
    }
}
