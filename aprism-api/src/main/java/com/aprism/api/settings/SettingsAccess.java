package com.aprism.api.settings;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Per-mod typed access to this mod's declared settings (v26.7-Alpha.6).
 *
 * <p>The mod sees only its own settings, validated against the manifest
 * declarations (type + enum options). Every successful {@link #set} fires
 * the registered change listeners fail-safe, giving mods a sync surface
 * without polling; a platform GUI renders screens from the same
 * declarations and mutates through this access.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface SettingsAccess {
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * Sets a setting value with declaration validation.
     *
     * @param key   the setting key
     * @param value the new value (String/Number/Boolean per declared type)
     * @return true when the value was accepted and persisted-dirty
     */
    boolean set(String key, Object value);

    /**
     * @return the current value, or the declared default when unset
     */
    Object get(String key);

    String getString(String key);

    int getInt(String key);

    boolean getBool(String key);

    double getDouble(String key);

    /**
     * @return the declared schema: key -> {type, default, label, options}
     */
    Map<String, Map<String, Object>> schema();

    /**
     * @return the declared setting keys in declaration order
     */
    List<String> keys();

    /**
     * Subscribes a change listener for this mod's settings. Listeners fire
     * fail-safe after every successful {@link #set}.
     *
     * @param listener receives (key, newValue)
     * @return an auto-unsubscribing handle
     */
    AutoCloseable subscribe(Consumer<String> listener);
}
