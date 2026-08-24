package com.aprism.loader.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.aprism.api.settings.SettingsAccess;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Loader-side {@link SettingsAccess} bound to one mod's {@link ModSettings}
 * and manifest declarations (v26.7-Alpha.6).
 *
 * <p>Validation delegates to {@link ModSettings#set} (type + enum option
 * checks, undeclared-key rejection). Change listeners fire fail-safe after
 * every accepted set; a throwing listener is isolated and logged.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class SettingsAccessImpl implements SettingsAccess {
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG = Logger.getLogger("aprism.settings");

    private final String modId;
    private final ModSettings settings;
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public SettingsAccessImpl(String modId, ModSettings settings) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public boolean set(String key, Object value) {
        try {
            settings.set(key, value);
        } catch (RuntimeException e) {
            return false;
        }
        boolean accepted = true;
        if (accepted) {
            for (Consumer<String> l : listeners) {
                try {
                    l.accept(key);
                } catch (RuntimeException e) {
                    LOG.warning("Settings listener for '" + modId + "." + key
                            + "' failed: " + e.getMessage());
                }
            }
        }
        return accepted;
    }

    @Override
    public Object get(String key) {
        return settings.get(key);
    }

    @Override
    public String getString(String key) {
        return String.valueOf(settings.get(key));
    }

    @Override
    public int getInt(String key) {
        Object v = settings.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    @Override
    public boolean getBool(String key) {
        Object v = settings.get(key);
        return v instanceof Boolean b && b;
    }

    @Override
    public double getDouble(String key) {
        Object v = settings.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    @Override
    public Map<String, Map<String, Object>> schema() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (String key : keys()) {
            if (settings.getDeclarations().get(key) == null) { continue; }
            Map<String, Object> decl = new LinkedHashMap<>();
            decl.put("type", settings.getDeclarations().get(key).type().name());
            decl.put("default", settings.getDeclarations().get(key).defaultValue());
            String label = settings.getDeclarations().get(key).label();
            if (label != null && !label.isEmpty()) {
                decl.put("label", label);
            }
            List<String> options = settings.getDeclarations().get(key).options();
            if (options != null && !options.isEmpty()) {
                decl.put("options", new ArrayList<>(options));
            }
            out.put(key, decl);
        }
        return out;
    }

    @Override
    public List<String> keys() {
        return List.copyOf(settings.getDeclarations().keySet());
    }

    @Override
    public AutoCloseable subscribe(Consumer<String> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
