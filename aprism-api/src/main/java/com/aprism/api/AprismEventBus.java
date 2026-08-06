package com.aprism.api;

/**
 * Phase-strict event bus for Aprism. Listeners are registered against a specific
 * event type and are only invoked when an event of that exact type is posted.
 * <p>
 * This bus backs both the Fabric-style functional adapters and the Forge-style
 * {@code addListener} adapters exposed to platform mods, so a single subscription
 * model serves mods ported from either ecosystem.
 * </p>
 *
 * @author BlockConnect@StarsailsClover
 */
public interface AprismEventBus {

    /**
     * Registers a listener for a specific event type.
     *
     * @param eventType the event class
     * @param listener the listener to register
     * @param <E> the event type
     */
    <E extends AprismEvent> void register(Class<E> eventType, AprismEventListener<E> listener);

    /**
     * Unregisters a previously registered listener.
     *
     * @param eventType the event class
     * @param listener the listener to unregister
     * @param <E> the event type
     */
    <E extends AprismEvent> void unregister(Class<E> eventType, AprismEventListener<E> listener);

    /**
     * Posts an event to all registered listeners for its type.
     *
     * @param event the event to post
     */
    void post(AprismEvent event);
}
