package com.aprism.api;

/**
 * The canonical mod entrypoint interface that all Aprism mods must implement.
 * <p>
 * This is the unified entrypoint for both Java Edition (JE) and Bedrock Edition
 * (BE) mods loaded by the Aprism loader. Implementations are discovered via the
 * {@code entrypoints} section of {@code aprism.manifest.json} and invoked by the
 * runtime in a strict phase order: {@link AprismPhase#PREINIT},
 * {@link AprismPhase#INIT}, {@link AprismPhase#SETUP}, and
 * {@link AprismPhase#COMPLETE}.
 * </p>
 * <p>
 * Only {@link #onInitialize(AprismContext)} is required; the remaining lifecycle
 * hooks provide sensible no-op defaults so mods may opt in to the phases they
 * care about.
 * </p>
 *
 * @author BlockConnect@StarsailsClover
 */
public interface IAprismMod {

    /**
     * Called during the {@link AprismPhase#INIT initialization phase}. This is
     * the primary hook where mods register content and subscribe to events.
     *
     * @param context the runtime context providing access to the event bus, registry, and logger
     */
    void onInitialize(AprismContext context);

    /**
     * Called during the {@link AprismPhase#PREINIT pre-initialization phase}.
     * Default implementation is a no-op.
     *
     * @param context the runtime context
     */
    default void onPreInitialize(AprismContext context) {
        // default no-op
    }

    /**
     * Called during the {@link AprismPhase#SETUP setup phase} for
     * post-registration wiring and cross-mod integration. Default
     * implementation is a no-op.
     *
     * @param context the runtime context
     */
    default void onSetup(AprismContext context) {
        // default no-op
    }

    /**
     * Called during the {@link AprismPhase#COMPLETE completion phase} once all
     * mods are initialized. Default implementation is a no-op.
     *
     * @param context the runtime context
     */
    default void onComplete(AprismContext context) {
        // default no-op
    }
}
