package com.aprism.loader;

import com.aprism.api.AprismEvent;
import com.aprism.api.AprismEventBus;
import com.aprism.api.AprismEventListener;
import com.aprism.api.EventPriority;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe implementation of {@link AprismEventBus} with dispatch-priority
 * support (v26.3-Alpha.6, Forge/NeoForge parity).
 *
 * <p>Listeners are stored per event type in priority order, from
 * {@link EventPriority#HIGHEST} to {@link EventPriority#LOWEST}; listeners
 * sharing a priority run in registration order. Dispatch is synchronous and
 * thread-safe via concurrent collections; a cancelled event short-circuits
 * the remaining listeners.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismEventBusImpl implements AprismEventBus {

    /**
     * A listener together with its dispatch priority.
     */
    private record RegisteredListener(EventPriority priority, AprismEventListener<?> listener) {
    }

    private final Map<Class<?>, List<RegisteredListener>> listeners = new ConcurrentHashMap<>();

    @Override
    public <E extends AprismEvent> void register(Class<E> eventType, AprismEventListener<E> listener) {
        register(eventType, listener, EventPriority.NORMAL);
    }

    @Override
    public <E extends AprismEvent> void register(
            Class<E> eventType, AprismEventListener<E> listener, EventPriority priority) {
        EventPriority effective = priority == null ? EventPriority.NORMAL : priority;
        List<RegisteredListener> bucket =
                listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        synchronized (bucket) {
            int index = bucket.size();
            for (int i = 0; i < bucket.size(); i++) {
                if (bucket.get(i).priority().ordinal() > effective.ordinal()) {
                    index = i;
                    break;
                }
            }
            bucket.add(index, new RegisteredListener(effective, listener));
        }
    }

    @Override
    public <E extends AprismEvent> void unregister(Class<E> eventType, AprismEventListener<E> listener) {
        List<RegisteredListener> bucket = listeners.get(eventType);
        if (bucket != null) {
            bucket.removeIf(registered -> registered.listener() == listener);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void post(AprismEvent event) {
        List<RegisteredListener> bucket = listeners.get(event.getClass());
        if (bucket == null) {
            return;
        }
        for (RegisteredListener registered : bucket) {
            ((AprismEventListener<AprismEvent>) registered.listener()).onEvent(event);
            if (event.isCancelled()) {
                break;
            }
        }
    }
}
