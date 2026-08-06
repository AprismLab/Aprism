package com.aprism.api;

/**
 * Sealed interface base for all Aprism events. Only Aprism-defined event types
 * may implement this interface directly, ensuring the event bus can reason about
 * every dispatched type. The nested {@link GameEvent} provides a non-sealed
 * extension point so that concrete game events may be defined by the runtime.
 *
 * @author BlockConnect@StarsailsClover
 */
public sealed interface AprismEvent permits AprismEvent.GameEvent {

    /**
     * @return the lifecycle phase during which this event is dispatched
     */
    AprismPhase getPhase();

    /**
     * @return whether this event supports cancellation
     */
    boolean isCancellable();

    /**
     * @return whether this event has been cancelled
     */
    boolean isCancelled();

    /**
     * Sets the cancelled state of this event. Has no effect on non-cancellable
     * events.
     *
     * @param cancelled the new cancelled state
     */
    void setCancelled(boolean cancelled);

    /**
     * Non-sealed extension point for game-lifecycle events. Concrete event
     * types implement this interface so they can be defined across modules
     * while remaining within the sealed {@link AprismEvent} hierarchy.
     */
    non-sealed interface GameEvent extends AprismEvent {
    }
}
