package com.aprism.loader;

import com.aprism.api.AprismEvent;
import com.aprism.api.AprismEventBus;
import com.aprism.api.AprismPhase;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Phase-strict implementation of {@link AprismEventBus}.
 *
 * <p>Listeners are stored in a two-level map keyed by phase then event type.
 * A listener registered for {@link AprismPhase#INIT} only fires when an event
 * is posted during {@code INIT}. Dispatch is synchronous and thread-safe via
 * concurrent collections.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismEventBusImpl implements AprismEventBus {

    private final Map<AprismPhase, Map<Class<?>, List<Consumer<?>>>> listeners = new ConcurrentHashMap<>();
    private volatile boolean lastCancelled;

    @Override
    public <E> void addListener(Class<E> eventType, AprismPhase phase, Consumer<E> listener) {
        listeners
                .computeIfAbsent(phase, p -> new ConcurrentHashMap<>())
                .computeIfAbsent(eventType, t -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E> void post(AprismPhase phase, E event) {
        lastCancelled = false;
        Map<Class<?>, List<Consumer<?>>> phaseMap = listeners.get(phase);
        if (phaseMap == null) {
            return;
        }
        if (event instanceof AprismEvent ae) {
            ae.setPhase(phase);
        }
        // Exact-type dispatch. AprismEvent subclasses also fire AprismEvent listeners.
        dispatch(phaseMap, event.getClass(), event);
        if (event instanceof AprismEvent aprismEvent) {
            dispatch(phaseMap, AprismEvent.class, aprismEvent);
            lastCancelled = aprismEvent.isCancelled();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(Map<Class<?>, List<Consumer<?>>> phaseMap, Class<?> type, Object event) {
        List<Consumer<?>> bucket = phaseMap.get(type);
        if (bucket == null) {
            return;
        }
        for (Consumer c : bucket) {
            c.accept(event);
        }
    }

    @Override
    public boolean isCancelled() {
        return lastCancelled;
    }
}
