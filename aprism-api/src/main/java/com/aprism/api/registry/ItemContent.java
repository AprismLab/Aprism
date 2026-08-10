package com.aprism.api.registry;

/**
 * Typed item content registered through the Aprism item registry
 * (v26.3-Alpha.2, QA0 gap #2). Carries the properties a JE item exposes to
 * mods; the native game binding is provided by the platform adapter layer.
 *
 * @param id       the resource key of the item
 * @param maxStack the maximum stack size (1-64)
 * @author BlockConnect@StarsailsClover
 */
public record ItemContent(ResourceKey id, int maxStack) {

    /**
     * Validates the stack size bounds.
     */
    public ItemContent {
        if (maxStack < 1 || maxStack > 64) {
            throw new IllegalArgumentException("maxStack must be 1-64, got: " + maxStack);
        }
    }
}
