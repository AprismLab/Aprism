package net.neoforged.bus.api;

import java.util.function.Consumer;

/**
 * NeoForge API shim: the mod-scoped event bus injected into NeoForge mod
 * constructors. Provided by Aprism so that genuine NeoForge mods can be
 * instantiated without the real NeoForge/FML runtime on the classpath.
 *
 * <p>Mirrors the minimal surface of {@code net.neoforged.bus.api.IEventBus}
 * that mods use during construction: registering event listeners. Aprism backs
 * this shim with its own event bus, so listeners registered here are dispatched
 * through Aprism's phase-strict bus.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface IEventBus {

    /**
     * Registers an event listener for the given event type.
     *
     * @param type     the event class
     * @param consumer the listener invoked when an event of {@code type} is posted
     * @param <T>      the event type
     */
    <T> void addListener(Class<T> type, Consumer<T> consumer);

    /**
     * Registers a generic event listener.
     *
     * @param consumer the listener invoked for any posted event
     */
    void addGenericListener(Consumer<Object> consumer);

    /**
     * Posts an event to all registered listeners.
     *
     * @param event the event instance
     */
    void post(Object event);
}
