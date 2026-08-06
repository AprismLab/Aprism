package com.aprism.loader;

import com.aprism.api.AprismEvent;
import com.aprism.api.AprismEventBus;
import com.aprism.api.AprismEventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe implementation of {@link AprismEventBus}.
 *
 * <p>Listeners are stored in a map keyed by event type. Dispatch is
 * synchronous and thread-safe via concurrent collections.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismEventBusImpl implements AprismEventBus {

    private final Map<Class<?>, List<AprismEventListener<?>>> listeners = new ConcurrentHashMap<>();

    @Override
    public <E extends AprismEvent> void register(Class<E> eventType, AprismEventListener<E> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public <E extends AprismEvent> void unregister(Class<E> eventType, AprismEventListener<E> listener) {
        List<AprismEventListener<?>> bucket = listeners.get(eventType);
        if (bucket != null) {
            bucket.remove(listener);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void post(AprismEvent event) {
        List<AprismEventListener<?>> bucket = listeners.get(event.getClass());
        if (bucket == null) {
            return;
        }
        for (AprismEventListener<?> listener : bucket) {
            ((AprismEventListener<AprismEvent>) listener).onEvent(event);
            if (event.isCancelled()) {
                break;
            }
        }
    }
}
