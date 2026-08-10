package com.aprism.api.registry;

/**
 * Typed entity content registered through the Aprism entity registry
 * (v26.3-Alpha.2, QA0 gap #2). Carries the registration-side properties of
 * an entity type; the native game binding (entity factory registration) is
 * provided by the platform adapter layer.
 *
 * @param id            the resource key of the entity type
 * @param factoryClass  the fully-qualified entity factory/instance class
 * @param clientTracked whether the entity is tracked on the client
 * @author BlockConnect@StarsailsClover
 */
public record EntityContent(ResourceKey id, String factoryClass, boolean clientTracked) {

    /**
     * Validates that a factory class is declared.
     */
    public EntityContent {
        if (factoryClass == null || factoryClass.isBlank()) {
            throw new IllegalArgumentException("factoryClass is required");
        }
    }
}
