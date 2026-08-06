package com.aprism.api;

/**
 * Functional interface for listeners on the {@link AprismEventBus}. A listener
 * is registered against a specific event type and invoked when an event of that
 * type is posted.
 *
 * @param <E> the event type handled by this listener
 * @author BlockConnect@StarsailsClover
 */
@FunctionalInterface
public interface AprismEventListener<E extends AprismEvent> {

    /**
     * Called when an event of the registered type is posted.
     *
     * @param event the posted event
     */
    void onEvent(E event);
}
