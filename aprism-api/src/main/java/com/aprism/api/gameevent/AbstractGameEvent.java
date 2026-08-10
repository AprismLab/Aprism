package com.aprism.api.gameevent;

import com.aprism.api.AprismEvent;
import com.aprism.api.AprismPhase;

/**
 * Base class for typed game events dispatched through the Aprism event bus
 * (v26.3-Alpha.1, QA0 gap #1). Game events are the runtime-level counterpart
 * of the lifecycle phases: while phases fire once during boot, game events
 * fire repeatedly as the game runs (ticks, world transitions, rendering).
 *
 * <p>Concrete game event types extend this class and implement
 * {@link AprismEvent.GameEvent} so they stay within the sealed
 * {@link AprismEvent} hierarchy while being definable outside the api
 * module's sealed permits clause.
 *
 * @author BlockConnect@StarsailsClover
 */
public abstract class AbstractGameEvent implements AprismEvent.GameEvent {

    private final AprismPhase phase;
    private final boolean cancellable;
    private volatile boolean cancelled;

    /**
     * @param phase       the lifecycle phase context during which the event
     *                    is dispatched (typically COMPLETE for runtime events)
     * @param cancellable whether listeners may cancel this event
     */
    protected AbstractGameEvent(AprismPhase phase, boolean cancellable) {
        this.phase = phase;
        this.cancellable = cancellable;
    }

    @Override
    public AprismPhase getPhase() {
        return phase;
    }

    @Override
    public boolean isCancellable() {
        return cancellable;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        if (cancellable) {
            this.cancelled = cancelled;
        }
    }
}
