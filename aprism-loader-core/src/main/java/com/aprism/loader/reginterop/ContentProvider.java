package com.aprism.loader.reginterop;

/**
 * Content provider SPI (v26.9 roadmap Alpha.4): anything that can
 * contribute game content through the uniform registry schema. Aprism
 * ships its own providers; loader bridges (Refract) translate foreign
 * registration calls into this interface, which is what makes the
 * registry cell of the v26.10 coverage matrix achievable on every host.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}
 * under {@code META-INF/services/com.aprism.loader.reginterop.ContentProvider}.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ContentProvider {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * @return the provider id (diagnostics key, must be non-blank)
     */
    String id();

    /**
     * Contributes entries into the sink. Called once per interop pass;
     * implementations must be idempotent across passes.
     *
     * @param sink the validated collection sink
     */
    void contribute(RegistrySchemaSink sink);
}
