package com.aprism.manifest;

/**
 * The data type of a mod setting declared in the manifest
 * (v26.2-Alpha.3, goal #7 part 2). The settings registry validates user
 * values against the declared type before accepting them.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum SettingType {

    /** A free-form text value. */
    STRING,

    /** A 64-bit integer value. */
    INTEGER,

    /** A 64-bit floating-point value. */
    DOUBLE,

    /** A boolean value ({@code true} / {@code false}). */
    BOOLEAN,

    /** One of a fixed set of options (see the declaration's options list). */
    ENUM;

    /**
     * Parses a type name from the manifest (case-insensitive). Unknown names
     * resolve to {@link #STRING} so a mistyped type degrades gracefully
     * rather than failing the whole mod.
     *
     * @param name the type name (string/int/integer/double/boolean/enum)
     * @return the matching type, or STRING when unrecognized
     */
    public static SettingType fromName(String name) {
        if (name == null) {
            return STRING;
        }
        return switch (name.trim().toLowerCase()) {
            case "int", "integer", "long" -> INTEGER;
            case "double", "float", "number" -> DOUBLE;
            case "boolean", "bool" -> BOOLEAN;
            case "enum", "choice", "select" -> ENUM;
            default -> STRING;
        };
    }
}
