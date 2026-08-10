package com.aprism.api;

/**
 * Dispatch priority for listeners on the {@link AprismEventBus}, providing
 * Forge/NeoForge {@code EventPriority} parity for mods ported from those
 * ecosystems (v26.3-Alpha.6).
 *
 * <p>Listeners are invoked in priority order from {@link #HIGHEST} to
 * {@link #LOWEST}; listeners sharing a priority run in registration order.
 * The default priority is {@link #NORMAL}, matching Forge conventions.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum EventPriority {

    /** Dispatched first. */
    HIGHEST,

    /** Dispatched after {@link #HIGHEST}. */
    HIGH,

    /** Default dispatch tier. */
    NORMAL,

    /** Dispatched after {@link #NORMAL}. */
    LOW,

    /** Dispatched last. */
    LOWEST;

    /**
     * @return whether this priority is dispatched strictly before the given
     *         other priority
     */
    public boolean dispatchesBefore(EventPriority other) {
        return this.ordinal() < other.ordinal();
    }
}
