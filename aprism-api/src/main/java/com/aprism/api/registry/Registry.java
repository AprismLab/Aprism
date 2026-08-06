package com.aprism.api.registry;

import java.util.Set;

/**
 * Generic content registry contract.
 *
 * <p>Registries map string identifiers to registered entries. The Aprism
 * Loader exposes typed marker sub-interfaces (e.g. {@link BlockRegistry}) so
 * that mods can look up the correct registry without downcasting.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface Registry {

    /**
     * Registers an entry under the given identifier.
     *
     * @param id    the registry identifier
     * @param entry the entry to register
     * @param <T>   the entry type
     */
    <T> void register(String id, T entry);

    /**
     * Looks up an entry by identifier.
     *
     * @param id  the registry identifier
     * @param <T> the entry type
     * @return the entry, or {@code null} if absent
     */
    <T> T get(String id);

    /**
     * @return an unmodifiable view of all registered identifiers
     */
    Set<String> keys();
}
