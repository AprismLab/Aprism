package com.aprism.api.registry;

import java.util.List;
import java.util.Optional;

/**
 * A typed content registry (v26.3-Alpha.2, QA0 gap #2). Unlike the generic
 * {@link Registry} contract, a typed registry binds one content type
 * (blocks, items, entities), validates entries at registration, rejects
 * duplicate keys, and exposes entries with their {@link ResourceKey}s.
 *
 * @param <T> the content type held by this registry
 * @author BlockConnect@StarsailsClover
 */
public interface TypedRegistry<T> {

    /**
     * Registers an entry under the given key.
     *
     * @param key   the resource key
     * @param entry the entry (never null)
     * @return the registered entry
     * @throws IllegalArgumentException when the key is already registered or
     *                                  the entry is null
     */
    T register(ResourceKey key, T entry);

    /**
     * Looks up an entry by key.
     *
     * @param key the resource key
     * @return the entry, or empty when absent
     */
    Optional<T> get(ResourceKey key);

    /**
     * @return true when the key is registered
     */
    boolean contains(ResourceKey key);

    /**
     * @return all registered keys in registration order
     */
    List<ResourceKey> keys();

    /**
     * @return the number of registered entries
     */
    int size();
}
