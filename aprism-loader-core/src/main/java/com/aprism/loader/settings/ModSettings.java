package com.aprism.loader.settings;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aprism.manifest.SettingDeclaration;
import com.aprism.manifest.SettingType;

/**
 * Per-mod settings store (v26.2-Alpha.3, goal #7 part 2). Holds the
 * declared settings of one mod plus the current values, which start at the
 * declared defaults and are overlaid by persisted user values. Values are
 * validated against the declared type before being accepted.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ModSettings {

    private final String modId;
    private final Map<String, SettingDeclaration> declarations = new LinkedHashMap<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private boolean dirty;

    /**
     * @param modId the owning mod id
     */
    public ModSettings(String modId) {
        this.modId = modId;
    }

    /**
     * Registers a declaration and seeds the current value with its default.
     * Re-registering an existing key replaces both declaration and value.
     *
     * @param declaration the setting declaration
     */
    public void declare(SettingDeclaration declaration) {
        if (declaration == null || declaration.key() == null) {
            return;
        }
        declarations.put(declaration.key(), declaration);
        values.put(declaration.key(), declaration.defaultValue());
    }

    /**
     * @return the owning mod id
     */
    public String getModId() {
        return modId;
    }

    /**
     * @return all declarations in declaration order
     */
    public Map<String, SettingDeclaration> getDeclarations() {
        return Map.copyOf(declarations);
    }

    /**
     * Reads the current value of a setting.
     *
     * @param key the setting key
     * @return the current value, or null when the key is unknown
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * Reads a setting value as a String.
     *
     * @param key the setting key
     * @return the string form, or null when unknown or null
     */
    public String getString(String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Reads a setting value as a long.
     *
     * @param key the setting key
     * @param fallback the fallback when the key is unknown or not numeric
     * @return the value or the fallback
     */
    public long getLong(String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Number n ? n.longValue() : fallback;
    }

    /**
     * Reads a setting value as a double.
     *
     * @param key the setting key
     * @param fallback the fallback when the key is unknown or not numeric
     * @return the value or the fallback
     */
    public double getDouble(String key, double fallback) {
        Object value = values.get(key);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    /**
     * Reads a setting value as a boolean.
     *
     * @param key the setting key
     * @param fallback the fallback when the key is unknown or not boolean
     * @return the value or the fallback
     */
    public boolean getBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean b ? b : fallback;
    }

    /**
     * Sets a setting value after validating it against the declaration.
     *
     * @param key   the setting key
     * @param value the new value
     * @throws IllegalArgumentException when the key is undeclared, or the
     *                                  value violates the declared type
     */
    public void set(String key, Object value) {
        SettingDeclaration declaration = declarations.get(key);
        if (declaration == null) {
            throw new IllegalArgumentException("setting not declared: " + key);
        }
        Object coerced = coerce(declaration, value);
        values.put(key, coerced);
        dirty = true;
    }

    /**
     * Validates and coerces a raw value against the declaration's type.
     *
     * @param declaration the declaration
     * @param value       the raw value
     * @return the coerced value
     * @throws IllegalArgumentException on type violation or out-of-option ENUM
     */
    public static Object coerce(SettingDeclaration declaration, Object value) {
        SettingType type = declaration.type();
        return switch (type) {
            case STRING -> value == null ? null : String.valueOf(value);
            case INTEGER -> {
                if (value instanceof Number n) {
                    yield n.longValue();
                }
                if (value instanceof String s) {
                    try {
                        yield Long.parseLong(s.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "setting " + declaration.key() + " expects an integer, got: " + s);
                    }
                }
                throw new IllegalArgumentException(
                        "setting " + declaration.key() + " expects an integer");
            }
            case DOUBLE -> {
                if (value instanceof Number n) {
                    yield n.doubleValue();
                }
                if (value instanceof String s) {
                    try {
                        yield Double.parseDouble(s.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "setting " + declaration.key() + " expects a number, got: " + s);
                    }
                }
                throw new IllegalArgumentException(
                        "setting " + declaration.key() + " expects a number");
            }
            case BOOLEAN -> {
                if (value instanceof Boolean b) {
                    yield b;
                }
                if (value instanceof String s) {
                    String t = s.trim().toLowerCase();
                    if (t.equals("true")) {
                        yield Boolean.TRUE;
                    }
                    if (t.equals("false")) {
                        yield Boolean.FALSE;
                    }
                }
                throw new IllegalArgumentException(
                        "setting " + declaration.key() + " expects a boolean");
            }
            case ENUM -> {
                String s = value == null ? null : String.valueOf(value);
                if (s != null && !declaration.options().isEmpty() && !declaration.options().contains(s)) {
                    throw new IllegalArgumentException("setting " + declaration.key()
                            + " must be one of " + declaration.options() + ", got: " + s);
                }
                yield s;
            }
        };
    }

    /**
     * @return true when a value changed since the last persistence
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Marks the store clean after a successful persistence.
     */
    public void markClean() {
        dirty = false;
    }

    /**
     * @return a snapshot of the current values (for persistence)
     */
    public Map<String, Object> snapshot() {
        return Map.copyOf(values);
    }

    /**
     * Applies persisted user values over the defaults. Values that fail
     * type validation are skipped silently (the default is kept) so a
     * hand-edited config file cannot break the mod.
     *
     * @param persisted the persisted values
     */
    public void applyPersisted(Map<String, Object> persisted) {
        if (persisted == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : persisted.entrySet()) {
            SettingDeclaration declaration = declarations.get(entry.getKey());
            if (declaration == null) {
                continue;
            }
            try {
                values.put(entry.getKey(), coerce(declaration, entry.getValue()));
            } catch (IllegalArgumentException ignored) {
                // Keep the default for invalid persisted values.
            }
        }
    }
}
