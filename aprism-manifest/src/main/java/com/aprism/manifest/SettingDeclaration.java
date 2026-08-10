package com.aprism.manifest;

import java.util.List;
import java.util.Map;

/**
 * A single typed setting declared by a mod in its manifest
 * (v26.2-Alpha.3, goal #7 part 2). Declarations live under the manifest's
 * {@code custom.aprism.settings} object; each entry maps a setting key to a
 * declaration describing its type, default value, human-readable label, and
 * (for ENUM types) the allowed options.
 *
 * <p>Schema shape inside the manifest:
 * <pre>
 * "custom": {
 *   "aprism": {
 *     "settings": {
 *       "maxDistance": {
 *         "type": "integer",
 *         "default": 32,
 *         "label": "Max render distance"
 *       },
 *       "mode": {
 *         "type": "enum",
 *         "default": "fast",
 *         "options": ["fast", "fancy"],
 *         "label": "Render mode"
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * @param key     the setting key (unique within the mod)
 * @param type    the declared data type
 * @param defaultValue the default value (may be null, meaning no default)
 * @param label   the human-readable label (may be null)
 * @param options the allowed values for ENUM types (empty otherwise)
 * @author BlockConnect@StarsailsClover
 */
public record SettingDeclaration(
        String key,
        SettingType type,
        Object defaultValue,
        String label,
        List<String> options
) {

    /**
     * Builds a declaration with an empty options list (non-ENUM types).
     *
     * @param key          the setting key
     * @param type         the data type
     * @param defaultValue the default value, or null
     * @param label        the label, or null
     * @return the declaration
     */
    public static SettingDeclaration of(String key, SettingType type, Object defaultValue, String label) {
        return new SettingDeclaration(key, type, defaultValue, label, List.of());
    }

    /**
     * @return true when this is an ENUM declaration with at least one option
     */
    public boolean hasOptions() {
        return options != null && !options.isEmpty();
    }
}
