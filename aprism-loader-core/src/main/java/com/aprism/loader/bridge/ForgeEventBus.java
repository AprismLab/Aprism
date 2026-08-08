package com.aprism.loader.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Aprism-backed implementation of the Forge {@link IEventBus} shim. Injected
 * into Forge mod constructors so genuine mods can register event listeners
 * without the real Forge/FML runtime.
 *
 * <p>Listeners are keyed by their declared event class. Posting an event
 * dispatches it to every listener whose registered type is assignable from the
 * event's runtime class, mirroring Forge's bus semantics closely enough for
 * construction-time registration patterns.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ForgeEventBus implements IEventBus {

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new LinkedHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void addListener(Class<T> type, Consumer<T> consumer) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>())
                .add((Consumer<Object>) consumer);
    }

    @Override
    public void addGenericListener(Consumer<Object> consumer) {
        listeners.computeIfAbsent(Object.class, k -> new ArrayList<>())
                .add(consumer);
    }

    @Override
    public void post(Object event) {
        if (event == null) {
            return;
        }
        for (Map.Entry<Class<?>, List<Consumer<Object>>> entry : listeners.entrySet()) {
            if (entry.getKey().isInstance(event)) {
                for (Consumer<Object> listener : entry.getValue()) {
                    listener.accept(event);
                }
            }
        }
    }
}
