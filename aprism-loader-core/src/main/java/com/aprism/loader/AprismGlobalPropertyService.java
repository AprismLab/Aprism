package com.aprism.loader;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/**
 * Aprism's {@link IGlobalPropertyService} implementation. Backed by a simple
 * {@link HashMap}, this provides the global property store that SpongePowered
 * Mixin requires for blackboard-style state (e.g. caching the active
 * transformer, storing platform agents).
 *
 * <p>Discovered by SpongePowered Mixin via the Java {@link java.util.ServiceLoader}
 * mechanism (see
 * {@code META-INF/services/org.spongepowered.asm.service.IGlobalPropertyService}).
 * The bundled LaunchWrapper and ModLauncher blackboard services return
 * {@code false} from their validity checks when those platforms are absent,
 * so Aprism's service is selected as the only valid provider.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismGlobalPropertyService implements IGlobalPropertyService {

    private final Map<IPropertyKey, Object> properties = new HashMap<>();

    /**
     * A property key backed by a string name.
     */
    private static final class StringKey implements IPropertyKey {
        private final String name;

        StringKey(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Override
    public IPropertyKey resolveKey(String name) {
        return new StringKey(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) properties.get(key);
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        properties.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        Object value = properties.get(key);
        return value != null ? (T) value : defaultValue;
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = properties.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
