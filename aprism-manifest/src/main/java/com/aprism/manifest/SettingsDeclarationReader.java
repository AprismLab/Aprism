package com.aprism.manifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads typed setting declarations from a parsed manifest
 * (v26.2-Alpha.3, goal #7 part 2). Declarations live under
 * {@code custom.aprism.settings} as an object mapping setting keys to
 * declaration objects ({@code type}, {@code default}, {@code label},
 * {@code options}). Reading is defensive: malformed declarations are skipped
 * (never throw) so a bad settings block cannot break mod loading.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class SettingsDeclarationReader {

    private SettingsDeclarationReader() {
    }

    /**
     * Reads all setting declarations from the manifest's custom block.
     *
     * @param manifest the parsed manifest
     * @return the declarations in declaration order; empty when none present
     */
    public static List<SettingDeclaration> read(AprismManifest manifest) {
        List<SettingDeclaration> out = new ArrayList<>();
        if (manifest == null || manifest.custom() == null) {
            return out;
        }
        Object aprism = manifest.custom().get("aprism");
        if (!(aprism instanceof Map<?, ?> aprismMap)) {
            return out;
        }
        Object settings = aprismMap.get("settings");
        if (!(settings instanceof Map<?, ?> settingsMap)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : settingsMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> decl)) {
                continue;
            }
            SettingDeclaration declaration = parseDeclaration(key, decl);
            if (declaration != null) {
                out.add(declaration);
            }
        }
        return out;
    }

    private static SettingDeclaration parseDeclaration(String key, Map<?, ?> decl) {
        SettingType type = SettingType.fromName(asString(decl.get("type")));
        String label = asString(decl.get("label"));
        Object defaultValue = coerceDefault(decl.get("default"), type);
        List<String> options = readOptions(decl.get("options"));
        return new SettingDeclaration(key, type, defaultValue, label, options);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Coerces a default value to the declared type. Gson parses JSON numbers
     * as Double, so INTEGER defaults are narrowed to Long.
     */
    private static Object coerceDefault(Object value, SettingType type) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case INTEGER -> value instanceof Number n ? n.longValue() : value;
            case DOUBLE -> value instanceof Number n ? n.doubleValue() : value;
            case BOOLEAN -> value;
            case ENUM, STRING -> String.valueOf(value);
        };
    }

    private static List<String> readOptions(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                options.add(String.valueOf(item));
            }
        }
        return List.copyOf(options);
    }
}
