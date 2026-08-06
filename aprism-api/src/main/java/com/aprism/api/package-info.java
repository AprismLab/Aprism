/**
 * The Aprism unified API surface. This package defines the contracts that mods
 * depend on at compile time: the {@link com.aprism.api.IAprismMod} entrypoint,
 * the {@link com.aprism.api.AprismContext} lifecycle context, the phase-strict
 * {@link com.aprism.api.AprismEventBus}, and supporting types.
 * <p>
 * The API is edition-agnostic: the same contracts serve both Java Edition and
 * Bedrock Edition mods loaded by the Aprism runtime.
 * </p>
 *
 * @author BlockConnect@StarsailsClover
 */
package com.aprism.api;
