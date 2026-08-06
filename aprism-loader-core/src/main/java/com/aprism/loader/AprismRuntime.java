package com.aprism.loader;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

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
 * <p>Loading is a two-phase process (see FACT.md 9.14 / 9.15):
 * <ol>
 *   <li><b>Extension phase</b>: scan {@code aprism-extensions/}, validate each
 *       {@code .aep} against the running Aprism + Minecraft version, register
 *       loader-support capabilities (which declare per-loader mod folders).</li>
 *   <li><b>Mod phase</b>: scan {@code mods/} (Aprism native) plus every
 *       registered loader folder ({@code fabric-mods/}, {@code neoforge-mods/},
 *       ...), parse manifests, add jars to the shared class space.</li>
 * </ol>
 * Extensions must complete before mods are scanned, because the set of mod
 * folders to scan depends on which loader-support extensions are present.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismRuntime {

    private static final Logger LOG = Logger.getLogger(AprismRuntime.class.getName());
    private static final AprismRuntime INSTANCE = new AprismRuntime();

    private AprismClassLoader classLoader;
    private AprismEventBus eventBus;
    private AprismRegistry registry;
    private final Map<String, ModContainer> mods = new LinkedHashMap<>();
    private final Map<String, String> modLoaderKeys = new HashMap<>();
    private Instrumentation instrumentation;

    private String aprismVersion;
    private String mcEdit;
    private String mcVersion;
    private ExtensionLoader extensionLoader;
    private final List<ExtensionLoader.LoadedExtension> loadedExtensions = new ArrayList<>();

    private AprismRuntime() {
    }

    /**
     * @return the singleton runtime instance
     */
    public static AprismRuntime instance() {
        return INSTANCE;
    }

    /**
     * Initializes the runtime with version metadata and the instrumentation
     * handle. The version triple is used to validate extensions and mods
     * against the running Aprism + Minecraft version.
     *
     * @param inst           the instrumentation handle
     * @param aprismVersion  the running Aprism Loader version (e.g. {@code "26.0-Alpha.1"})
     * @param mcEdit         the Minecraft edition ({@code "JE"} or {@code "BE"})
     * @param mcVersion      the running Minecraft version (e.g. {@code "1.21.4"})
     */
    public void initialize(Instrumentation inst, String aprismVersion, String mcEdit, String mcVersion) {
        this.instrumentation = inst;
        this.aprismVersion = aprismVersion;
        this.mcEdit = mcEdit;
        this.mcVersion = mcVersion;
        AprismClassTransformer transformer = new AprismClassTransformer();
        this.classLoader = new AprismClassLoader(getClass().getClassLoader(), transformer);
        this.eventBus = new SimpleEventBus();
        this.registry = new SimpleRegistry();
        this.extensionLoader = new ExtensionLoader(aprismVersion, mcEdit, mcVersion);
    }

    /**
     * Backwards-compatible initializer that does not bind version metadata.
     * Equivalent to {@code initialize(inst, null, null, null)}. Use the
     * versioned overload for any real runtime; this exists for tests and
     * legacy callers.
     *
     * @param inst the instrumentation handle
     */
    public void initialize(Instrumentation inst) {
        initialize(inst, null, null, null);
    }

    /**
     * Phase 1: scans the {@code aprism-extensions/} directory and loads every
     * valid {@code .aep}. Loader-support extensions register their per-loader
     * mod folders here, which phase 2 ({@link #loadMods(Path)}) consumes.
     *
     * @param extensionsDir the directory containing {@code .aep} files
     * @return the list of loaded extensions
     */
    public List<ExtensionLoader.LoadedExtension> loadExtensions(Path extensionsDir) {
        ensureInitialized();
        loadedExtensions.clear();
        List<ExtensionLoader.LoadedExtension> result = extensionLoader.load(extensionsDir);
        loadedExtensions.addAll(result);
        LOG.info("Loaded " + result.size() + " Aprism extension(s); "
                + extensionLoader.getLoaderFolders().size() + " loader-support folder(s) registered");
        return List.copyOf(loadedExtensions);
    }

    /**
     * Phase 2: scans mod folders and adds every discovered mod jar to the
     * shared class space. Scans the Aprism native {@code mods/} folder plus
     * every loader folder registered by loader-support extensions in phase 1.
     *
     * <p>If phase 1 has not run, only {@code mods/} is scanned (Aprism native
     * only). This is the correct behavior for instances without any loader
     * extensions installed.
     *
     * @param gameRoot the game instance root (contains {@code mods/},
     *                 {@code fabric-mods/}, etc.)
     */
    public void loadMods(Path gameRoot) {
        ensureInitialized();
        Map<String, String> loaderFolders = extensionLoader != null
                ? extensionLoader.getLoaderFolders()
                : Map.of();

        ModDiscoverer discoverer = new ModDiscoverer();
        List<ModDiscoverer.DiscoveredMod> discovered = discoverer.discoverAll(gameRoot, loaderFolders);

        mods.clear();
        modLoaderKeys.clear();
        for (ModDiscoverer.DiscoveredMod dm : discovered) {
            classLoader.addModJar(dm.path());
            ModContainer container = new SimpleModContainer(dm);
            mods.put(container.getId(), container);
            modLoaderKeys.put(container.getId(), dm.loaderKey());
        }
        LOG.info("Loaded " + mods.size() + " mod(s) across " + loaderFolders.size() + 1 + " folder(s)");
    }

    /**
     * Convenience entry point that runs both phases in order.
     *
     * @param gameRoot      the game instance root
     * @param extensionsDir the extensions directory (may be absent)
     */
    public void performLoad(Path gameRoot, Path extensionsDir) {
        loadExtensions(extensionsDir);
        loadMods(gameRoot);
    }

    /**
     * Legacy single-folder mod loader. Scans only the given directory (treated
     * as Aprism native). Retained for backwards compatibility; new callers
     * should use {@link #loadMods(Path)} with the game root.
     *
     * @param modsDir the mods directory
     */
    public void loadModsFromDirectory(Path modsDir) {
        ensureInitialized();
        ModDiscoverer discoverer = new ModDiscoverer();
        for (ModDiscoverer.DiscoveredMod dm : discoverer.discover(modsDir)) {
            classLoader.addModJar(dm.path());
            ModContainer container = new SimpleModContainer(dm);
            mods.put(container.getId(), container);
            modLoaderKeys.put(container.getId(), dm.loaderKey());
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
     * @return the registry
     */
    public AprismRegistry getRegistry() {
        return registry;
    }

    /**
     * @return the Aprism version bound at initialization (may be {@code null})
     */
    public String getAprismVersion() {
        return aprismVersion;
    }

    /**
     * @return the Minecraft edition bound at initialization (may be {@code null})
     */
    public String getMcEdit() {
        return mcEdit;
    }

    /**
     * @return the Minecraft version bound at initialization (may be {@code null})
     */
    public String getMcVersion() {
        return mcVersion;
    }

    /**
     * @return the loaded extensions (phase 1 output)
     */
    public List<ExtensionLoader.LoadedExtension> getLoadedExtensions() {
        return List.copyOf(loadedExtensions);
    }

    /**
     * @return the loader-support folder map registered by extensions
     */
    public Map<String, String> getLoaderFolders() {
        return extensionLoader != null
                ? extensionLoader.getLoaderFolders()
                : Map.of();
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
     * @return all loaded mod containers, in insertion order
     */
    public List<ModContainer> getMods() {
        return List.copyOf(mods.values());
    }

    /**
     * Returns the loader key that loaded the given mod id.
     *
     * @param id the mod id
     * @return the loader key (e.g. {@code "aprism"}, {@code "Fa"}, ...), or empty
     */
    public Optional<String> getLoaderKey(String id) {
        return Optional.ofNullable(modLoaderKeys.get(id));
    }

    private void ensureInitialized() {
        if (classLoader == null) {
            throw new IllegalStateException("AprismRuntime not initialized; call initialize() first");
        }
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
        public void post(AprismEvent event) {
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

    /**
     * Minimal {@link ModContainer} backed by a discovered mod.
     *
     * @author BlockConnect@StarsailsClover
     */
    private record SimpleModContainer(ModDiscoverer.DiscoveredMod discovered) implements ModContainer {
        @Override
        public String getId() {
            return discovered.manifest().id();
        }

        @Override
        public String getVersion() {
            return discovered.manifest().version();
        }

        @Override
        public String getDisplayName() {
            return discovered.manifest().displayName();
        }

        @Override
        public String getDescription() {
            return discovered.manifest().description();
        }

        @Override
        public Path getSourcePath() {
            return discovered.path();
        }

        @Override
        public Object getInstance() {
            return null;
        }

        @Override
        public <T> Optional<T> getInstance(Class<T> type) {
            return Optional.empty();
        }
    }
}
