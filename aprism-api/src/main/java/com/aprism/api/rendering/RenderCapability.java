package com.aprism.api.rendering;

import java.util.List;
import java.util.Objects;

/**
 * The capabilities a {@link RenderingProvider} exposes on a given backend
 * (v26.3-Alpha.5, goal #9). <strong>Experimental / reference-only: no
 * production guarantee.</strong>
 *
 * @param backend        the backend this capability set describes
 * @param features       the feature tokens supported (e.g. {@code ray-query},
 *                       {@code mesh-shader}, {@code compute}); never null
 * @param maxTextureSize the maximum supported texture dimension (0 when
 *                       unknown)
 * @author BlockConnect@StarsailsClover
 */
public record RenderCapability(RenderBackend backend, List<String> features, int maxTextureSize) {

    /**
     * Validates the backend and normalizes the feature list.
     */
    public RenderCapability {
        Objects.requireNonNull(backend, "backend");
        features = features == null ? List.of() : List.copyOf(features);
    }

    /**
     * @param feature the feature token
     * @return true when the backend exposes the feature
     */
    public boolean supports(String feature) {
        return features.contains(feature);
    }
}
