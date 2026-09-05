package com.aprism.loader.reginterop;

import com.aprism.api.registry.ResourceKey;

import java.util.Map;

/**
 * Provider-facing sink that validates and collects schema entries
 * (v26.9 roadmap Alpha.4). Validation failures are captured per entry
 * (fail-closed per entry, never per provider) so one broken declaration
 * cannot suppress the rest of a provider's contribution.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface RegistrySchemaSink {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * Contributes one entry; implementations validate against the schema
     * and record a rejection instead of throwing.
     *
     * @param kind the content kind
     * @param id the resource key
     * @param properties the properties (string-valued)
     * @return true when accepted
     */
    boolean contribute(RegistrySchema.Kind kind, ResourceKey id,
            Map<String, String> properties);
}
