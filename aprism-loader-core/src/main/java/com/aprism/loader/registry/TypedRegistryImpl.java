package com.aprism.loader.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.aprism.api.registry.ResourceKey;
import com.aprism.api.registry.TypedRegistry;

/**
 * In-memory implementation of {@link TypedRegistry} (v26.3-Alpha.2, QA0 gap
 * #2). Preserves registration order, rejects duplicate keys, and rejects
 * null entries.
 *
 * @param <T> the content type
 * @author BlockConnect@StarsailsClover
 */
public final class TypedRegistryImpl<T> implements TypedRegistry<T> {

    private final Map<ResourceKey, T> entries = new LinkedHashMap<>();

    @Override
    public T register(ResourceKey key, T entry) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(entry, "entry");
        if (entries.containsKey(key)) {
            throw new IllegalArgumentException("already registered: " + key.combined());
        }
        entries.put(key, entry);
        return entry;
    }

    @Override
    public Optional<T> get(ResourceKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    @Override
    public boolean contains(ResourceKey key) {
        return entries.containsKey(key);
    }

    @Override
    public List<ResourceKey> keys() {
        return List.copyOf(entries.keySet());
    }

    @Override
    public int size() {
        return entries.size();
    }

    /**
     * Drops all entries (runtime shutdown).
     */
    public void clear() {
        entries.clear();
    }
}
