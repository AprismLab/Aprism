package com.aprism.api;

import java.util.Optional;
import java.util.Set;

/**
 * Global registry for mod-registered content such as blocks, items, and
 * entities. Entries are namespaced by mod ID to avoid collisions between mods.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface AprismRegistry {

    /**
     * Registers an entry under a namespace and name.
     *
     * @param namespace the namespace, typically the owning mod ID
     * @param name the entry name
     * @param entry the entry to register
     * @param <T> the entry type
     * @return the registered entry
     */
    <T> T register(String namespace, String name, T entry);

    /**
     * Retrieves a previously registered entry.
     *
     * @param namespace the namespace
     * @param name the entry name
     * @param <T> the entry type
     * @return an {@link Optional} containing the entry, or empty if not found
     */
    <T> Optional<T> get(String namespace, String name);

    /**
     * @return the set of all registered namespaces
     */
    Set<String> getNamespaces();
}
