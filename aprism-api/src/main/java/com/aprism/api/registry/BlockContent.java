package com.aprism.api.registry;

/**
 * Typed block content registered through the Aprism block registry
 * (v26.3-Alpha.2, QA0 gap #2). Carries the properties a JE block exposes to
 * mods; the native game binding (translating this into a real Minecraft
 * block) is provided by the platform adapter layer and is out of scope for
 * the loader core.
 *
 * @param id        the resource key of the block
 * @param hardness  the hardness value (0..50 typical vanilla range)
 * @param resistance the blast resistance value
 * @param luminance the emitted light level (0-15)
 * @author BlockConnect@StarsailsClover
 */
public record BlockContent(ResourceKey id, float hardness, float resistance, int luminance) {

    /**
     * Validates the light level bounds.
     */
    public BlockContent {
        if (luminance < 0 || luminance > 15) {
            throw new IllegalArgumentException("luminance must be 0-15, got: " + luminance);
        }
    }
}
