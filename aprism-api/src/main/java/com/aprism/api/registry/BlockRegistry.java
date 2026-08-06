package com.aprism.api.registry;

/**
 * Marker interface for the block registry.
 *
 * <p>Extends {@link Registry} so that block registration shares the generic
 * contract while remaining distinguishable from other registries at the type
 * level.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface BlockRegistry extends Registry {
}
