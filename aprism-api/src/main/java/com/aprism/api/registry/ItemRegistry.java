package com.aprism.api.registry;

/**
 * Marker interface for the item registry.
 *
 * <p>Extends {@link Registry} so that item registration shares the generic
 * contract while remaining distinguishable from other registries at the type
 * level.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ItemRegistry extends Registry {
}
