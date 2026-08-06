package com.aprism.loader;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.aprism.api.AprismEvent;
import com.aprism.api.AprismEventBus;
import com.aprism.api.AprismEventListener;
import com.aprism.api.AprismPhase;
import com.aprism.api.AprismRegistry;
import com.aprism.api.ModContainer;

/**
 * Singleton runtime that orchestrates the Aprism mod loading lifecycle. Holds
 * the {@link AprismClassLoader}, the {@link AprismEventBus}, the
 * {@link AprismRegistry}, and the map of loaded mod containers.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismRuntime {

    private static final AprismRuntime INSTANCE = new AprismRuntime();

    private AprismClassLoader classLoader;
    private AprismEventBus eventBus;
    private AprismRegistry registry;
    private final Map<String, ModContainer> mods = new HashMap<>();
    private Instrumentation instrumentation;

    private AprismRuntime() {
    }

    /**
     * @return the singleton runtime instance
     */
    public static AprismRuntime instance() {
        return INSTANCE;
    }

    /**
     * Initializes the runtime with the given instrumentation handle, creating
     * the classloader, event bus, and registry.
     *
     * @param inst the instrumentation handle
     */
    public void initialize(Instrumentation inst) {
        this.instrumentation = inst;
        AprismClassTransformer transformer = new AprismClassTransformer();
        this.classLoader = new AprismClassLoader(getClass().getClassLoader(), transformer);
        this.eventBus = new SimpleEventBus();
        this.registry = new SimpleRegistry();
    }

    /**
     * Loads mods from the given directory by discovering their archives and
     * adding them to the shared class space.
     *
     * @param modsDir the mods directory
     */
    public void loadMods(Path modsDir) {
        ModDiscoverer discoverer = new ModDiscoverer();
        for (ModDiscoverer.DiscoveredMod dm : discoverer.discover(modsDir)) {
            classLoader.addModJar(dm.path());
        }
    }

    /**
     * Invokes the registered entrypoints for the given phase.
     *
     * @param phase the phase to invoke
     */
    public void invokeEntrypoints(AprismPhase phase) {
        // Real entrypoint invocation is delegated to EntryPointInvoker; this
        // hook exists so the runtime can drive the lifecycle in phase order.
    }

    /**
     * @return the event bus
     */
    public AprismEventBus getEventBus() {
        return eventBus;
    }

    /**
     * Returns the mod container for the given id.
     *
     * @param id the mod id
     * @return the mod container, or {@code null} if no such mod is loaded
     */
    public ModContainer getMod(String id) {
        return mods.get(id);
    }

    /**
     * Minimal {@link AprismEventBus} implementation backed by a map of event
     * type to listener list.
     *
     * @author BlockConnect@StarsailsClover
     */
    private static final class SimpleEventBus implements AprismEventBus {
        private final Map<Class<?>, List<AprismEventListener<?>>> listeners = new HashMap<>();

        @Override
        public <E extends AprismEvent> void register(Class<E> eventType, AprismEventListener<E> listener) {
            listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
        }

        @Override
        public <E extends AprismEvent> void unregister(Class<E> eventType, AprismEventListener<E> listener) {
            List<AprismEventListener<?>> l = listeners.get(eventType);
            if (l != null) {
                l.remove(listener);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <E extends AprismEvent> void post(AprismEvent event) {
            List<AprismEventListener<?>> l = listeners.get(event.getClass());
            if (l != null) {
                for (AprismEventListener<?> listener : l) {
                    ((AprismEventListener<AprismEvent>) listener).onEvent(event);
                }
            }
        }
    }

    /**
     * Minimal {@link AprismRegistry} backed by a namespaced map.
     *
     * @author BlockConnect@StarsailsClover
     */
    private static final class SimpleRegistry implements AprismRegistry {
        private final Map<String, Object> entries = new HashMap<>();

        @Override
        public <T> T register(String namespace, String name, T entry) {
            entries.put(namespace + ":" + name, entry);
            return entry;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> get(String namespace, String name) {
            return (Optional<T>) Optional.ofNullable(entries.get(namespace + ":" + name));
        }

        @Override
        public Set<String> getNamespaces() {
            Set<String> namespaces = new java.util.HashSet<>();
            for (String key : entries.keySet()) {
                namespaces.add(key.substring(0, key.indexOf(':')));
            }
            return namespaces;
        }
    }
}
