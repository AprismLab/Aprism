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
     *
     * <p>MUST implement value equality: callers (Mixin core caches its key
     * instances, but MixinExtras and other service consumers resolve a fresh
     * key for every get/put) repeatedly call {@link #resolveKey(String)} and
     * expect the backing store to see them as the same property. Without
     * equals/hashCode the map keys are compared by identity, so a
     * setProperty followed by getProperty with a freshly resolved key
     * silently misses - observed as a NullPointerException inside MixinExtras
     * Blackboard.put during its bootstrap (v26.8-Alpha.9).
     */
    private static final class StringKey implements IPropertyKey {
        private final String name;

        StringKey(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof StringKey other)) {
                return false;
            }
            return name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
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
