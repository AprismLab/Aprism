package com.aprism.loader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import com.aprism.api.AprismContext;
import com.aprism.api.AprismEventBus;
import com.aprism.api.AprismPhase;
import com.aprism.api.AprismRegistry;
import com.aprism.api.ExtensionContext;
import com.aprism.api.IAprismExtension;
import com.aprism.api.IAprismMod;
import com.aprism.api.ModContainer;
import com.aprism.loader.remap.BytecodeRemapper;
import com.aprism.loader.remap.McProfile;
import com.aprism.loader.remap.Remapper;
import com.aprism.loader.remap.TinyMappings;
import com.aprism.loader.remap.TinyRemapper;
import com.aprism.loader.bridge.FabricEntrypointBridge;
import com.aprism.manifest.AprismExtensionManifest;
import com.aprism.manifest.AprismManifest;
import com.aprism.manifest.DependencyResolutionException;
import com.aprism.manifest.DependencyResolver;

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
 *       ...), parse manifests, resolve dependencies in topological order, add
 *       jars to the shared class space.</li>
 * </ol>
 * Extensions must complete before mods are scanned, because the set of mod
 * folders to scan depends on which loader-support extensions are present.
 *
 * <p>After loading, mods are driven through the lifecycle phases
 * ({@link AprismPhase#PREINIT} -> {@link AprismPhase#INIT} ->
 * {@link AprismPhase#SETUP} -> {@link AprismPhase#COMPLETE} ->
 * {@link AprismPhase#CLIENT}/{@link AprismPhase#SERVER}) by
 * {@link #invokeEntrypoints(AprismPhase)}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismRuntime {

    private static final Logger LOG = Logger.getLogger(AprismRuntime.class.getName());
    private static final AprismRuntime INSTANCE = new AprismRuntime();

    private AprismClassLoader classLoader;
    private AprismEventBus eventBus;
    private AprismRegistry registry;
    private EntryPointInvoker entryPointInvoker;
    private AprismClassTransformer transformer;
    private final Map<String, LoadedModContainer> mods = new LinkedHashMap<>();
    private final Map<String, LoadedBedrockModContainer> bedrockMods = new LinkedHashMap<>();
    private final Map<String, LoadedExtensionContainer> extensionContainers = new LinkedHashMap<>();
    private Path extensionTempDir;
    private Instrumentation instrumentation;

    private String aprismVersion;
    private String mcEdit;
    private String mcVersion;
    private ExtensionLoader extensionLoader;
    private final List<ExtensionLoader.LoadedExtension> loadedExtensions = new ArrayList<>();

    private McProfile mcProfile;
    private BytecodeRemapper bytecodeRemapper;

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
     * against the running Aprism + Minecraft version. The runtime creates its
     * own {@link AprismClassTransformer}; use
     * {@link #initialize(Instrumentation, AprismClassTransformer, String, String, String)}
     * from the agent so that the transformer registered with the JVM is the
     * same instance that receives access-widener rules.
     *
     * @param inst           the instrumentation handle (may be {@code null} in tests)
     * @param aprismVersion  the running Aprism Loader version (e.g. {@code "26.0-Alpha.1"})
     * @param mcEdit         the Minecraft edition ({@code "JE"} or {@code "BE"})
     * @param mcVersion      the running Minecraft version (e.g. {@code "26.2"})
     */
    public void initialize(Instrumentation inst, String aprismVersion, String mcEdit, String mcVersion) {
        initialize(inst, null, aprismVersion, mcEdit, mcVersion);
    }

    /**
     * Initializes the runtime with an externally created transformer. The
     * agent uses this overload to share the single transformer instance that
     * is registered with the JVM {@link Instrumentation}, so that
     * access-widener rules registered during mod loading take effect on the
     * production transformation path.
     *
     * @param inst                the instrumentation handle (may be {@code null} in tests)
     * @param externalTransformer the transformer to use, or {@code null} to create a new one
     * @param aprismVersion       the running Aprism Loader version
     * @param mcEdit              the Minecraft edition ({@code "JE"} or {@code "BE"})
     * @param mcVersion           the running Minecraft version
     */
    public void initialize(Instrumentation inst, AprismClassTransformer externalTransformer,
            String aprismVersion, String mcEdit, String mcVersion) {
        this.instrumentation = inst;
        this.aprismVersion = aprismVersion;
        this.mcEdit = mcEdit;
        this.mcVersion = mcVersion;
        this.transformer = externalTransformer != null
                ? externalTransformer
                : new AprismClassTransformer();
        this.classLoader = new AprismClassLoader(getClass().getClassLoader(), transformer);
        this.eventBus = new AprismEventBusImpl();
        this.registry = new SimpleRegistry();
        this.entryPointInvoker = new EntryPointInvoker(classLoader);
        this.extensionLoader = new ExtensionLoader(aprismVersion, mcEdit, mcVersion);
        this.mods.clear();
        this.bedrockMods.clear();
        this.loadedExtensions.clear();
        this.extensionContainers.clear();

        // Select the cross-version profile from the Minecraft version. For the
        // no-remap profile (26.1+) the bytecode remapper stays null and the
        // classloader behaves as a plain shared class space. For the remapped
        // profile (pre-26.1), the remapper is installed later via
        // {@link #loadIntermediaryMappings} once the intermediary mappings are
        // located.
        this.mcProfile = McProfile.of(mcVersion);
        this.bytecodeRemapper = null;
        this.classLoader.setBytecodeRemapper(null);

        // Bootstrap the SpongePowered Mixin environment so that @Mixin/@Inject
        // annotations declared by loaded mods are applied via the transformer.
        // The bootstrap is fully fault-tolerant: any failure is logged and
        // swallowed so a broken Mixin environment never blocks game startup.
        AprismMixinBootstrap.bootstrap(classLoader);
    }

    /**
     * Loads Fabric Intermediary (tiny v2) mappings and installs the resulting
     * intermediary→official bytecode remapper onto the classloader. Only
     * meaningful for the {@link McProfile#REMAPPED} profile (Minecraft pre-26.1);
     * for the no-remap profile this is a logged no-op.
     *
     * <p>Mods are compiled against Intermediary names; pre-26.1 game jars use
     * obfuscated official names, so mod bytecode must be remapped at define
     * time for its references to resolve.
     *
     * @param tinyV2File the tiny v2 mappings file (official → intermediary [→ named])
     * @throws IOException if the mappings cannot be read
     */
    public void loadIntermediaryMappings(Path tinyV2File) throws IOException {
        ensureInitialized();
        if (mcProfile != McProfile.REMAPPED) {
            LOG.info("Profile is NO_REMAP for Minecraft " + mcVersion
                    + "; intermediary mappings ignored");
            return;
        }
        TinyMappings mappings = TinyMappings.parse(tinyV2File);
        Remapper intermediaryToOfficial = TinyRemapper.intermediaryToOfficial(mappings);
        this.bytecodeRemapper = BytecodeRemapper.of(intermediaryToOfficial);
        this.classLoader.setBytecodeRemapper(this.bytecodeRemapper);
        LOG.info("Loaded intermediary mappings from " + tinyV2File + ": "
                + mappings.classCount() + " classes, "
                + mappings.methodCount() + " methods, "
                + mappings.fieldCount() + " fields");
    }

    /**
     * @return the cross-version profile selected for the running Minecraft version
     */
    public McProfile getMcProfile() {
        return mcProfile;
    }

    /**
     * @return the shared mod classloader, or {@code null} before initialization
     */
    public AprismClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * @return the installed bytecode remapper, or {@code null} when remapping
     *         is not active (no-remap profile or mappings not yet loaded)
     */
    public BytecodeRemapper getBytecodeRemapper() {
        return bytecodeRemapper;
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
     * Phase 1: scans the {@code aprism-extensions/} directory, loads every
     * valid {@code .aep}, extracts embedded jars into the classloader, and
     * invokes each extension's entrypoint ({@link IAprismExtension#onInitialize}).
     *
     * <p>Loader-support extensions register their per-loader mod folders
     * here (either via manifest {@code loaderKey} or dynamically via
     * {@link ExtensionContext#registerLoaderSupport}), which phase 2
     * ({@link #loadMods(Path)}) consumes.
     *
     * <p>Extensions with no {@code entrypoint} field in their manifest are
     * still registered (for manifest-driven loader-support) but their
     * {@code onInitialize} is not invoked.
     *
     * @param extensionsDir the directory containing {@code .aep} files
     * @return the list of loaded extension containers (with instances populated)
     */
    public List<LoadedExtensionContainer> loadExtensions(Path extensionsDir) {
        ensureInitialized();
        loadedExtensions.clear();
        extensionContainers.clear();

        // Phase 1a: validate and register manifest-driven loader-support folders
        List<ExtensionLoader.LoadedExtension> raw = extensionLoader.load(extensionsDir);
        loadedExtensions.addAll(raw);

        // Phase 1b: extract embedded jars into the classloader
        for (ExtensionLoader.LoadedExtension ext : raw) {
            extractExtensionJars(ext.sourcePath());
        }

        // Phase 1c: instantiate entrypoints and invoke onInitialize
        for (ExtensionLoader.LoadedExtension ext : raw) {
            instantiateExtension(ext);
        }

        LOG.info("Loaded " + extensionContainers.size() + " Aprism extension(s); "
                + extensionLoader.getLoaderFolders().size() + " loader-support folder(s) registered");
        return List.copyOf(extensionContainers.values());
    }

    /**
     * Extracts all embedded jars from a {@code .aep} archive into a temporary
     * directory and adds them to the classloader.
     *
     * @param aepFile the .aep archive path
     */
    private void extractExtensionJars(Path aepFile) {
        try {
            List<String> jarNames = extensionLoader.listEmbeddedJarNames(aepFile);
            if (jarNames.isEmpty()) {
                return;
            }
            Path tempDir = getExtensionTempDir();
            String baseName = aepFile.getFileName().toString().replace(".aep", "");
            for (String jarName : jarNames) {
                String safeName = jarName.replace("/", "_");
                Path target = tempDir.resolve(baseName + "_" + safeName);
                extensionLoader.extractJar(aepFile, jarName, target);
                classLoader.addModJar(target);
            }
        } catch (IOException e) {
            LOG.warning("Failed to extract jars from " + aepFile + ": " + e.getMessage());
        }
    }

    /**
     * Instantiates an extension's entrypoint class, wraps it in a
     * {@link LoadedExtensionContainer}, and invokes
     * {@link IAprismExtension#onInitialize} with a fresh
     * {@link ExtensionContextImpl}.
     *
     * <p>If the manifest has no entrypoint, a container is still registered
     * (with a null instance) so the extension's manifest-driven
     * loader-support folder is honored.
     *
     * @param ext the raw loaded extension
     */
    private void instantiateExtension(ExtensionLoader.LoadedExtension ext) {
        AprismExtensionManifest m = ext.manifest();
        LoadedExtensionContainer container = new LoadedExtensionContainer(m, ext.sourcePath());
        extensionContainers.put(m.extensionId(), container);

        if (m.entrypoint() == null || m.entrypoint().isBlank()) {
            return;
        }
        try {
            Class<?> clazz = classLoader.loadClass(m.entrypoint());
            Object instance = clazz.getDeclaredConstructor().newInstance();
            container.setInstance(instance);

            if (instance instanceof IAprismExtension extension) {
                ExtensionContext context = new ExtensionContextImpl(
                        container, eventBus, registry,
                        (loaderKey, folder) -> extensionLoader.addLoaderFolder(loaderKey, folder));
                extension.onInitialize(context);
            } else {
                LOG.warning("Extension entrypoint " + m.entrypoint()
                        + " does not implement IAprismExtension; skipping onInitialize");
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate extension entrypoint "
                    + m.entrypoint() + " for " + m.extensionId(), e);
        }
    }

    /**
     * Lazily creates a temporary directory for extracted extension jars.
     *
     * @return the temp directory path
     */
    private Path getExtensionTempDir() {
        if (extensionTempDir == null) {
            try {
                extensionTempDir = Files.createTempDirectory("aprism-extensions");
            } catch (IOException e) {
                throw new RuntimeException("Failed to create extension temp directory", e);
            }
        }
        return extensionTempDir;
    }

    /**
     * Phase 2: scans mod folders, resolves dependencies in topological order,
     * and adds every discovered mod jar to the shared class space. Scans the
     * Aprism native {@code mods/} folder plus every loader folder registered
     * by loader-support extensions in phase 1.
     *
     * <p>If phase 1 has not run, only {@code mods/} is scanned (Aprism native
     * only). This is the correct behavior for instances without any loader
     * extensions installed.
     *
     * <p>Mods are loaded in dependency order: a mod's dependencies are always
     * added to the classloader before it. Missing dependencies, version
     * conflicts, and dependency cycles abort the load with
     * {@link DependencyResolutionException}.
     *
     * @param gameRoot the game instance root (contains {@code mods/},
     *                 {@code fabric-mods/}, etc.)
     * @throws DependencyResolutionException if dependencies cannot be resolved
     */
    public void loadMods(Path gameRoot) throws DependencyResolutionException {
        ensureInitialized();
        Map<String, String> loaderFolders = extensionLoader != null
                ? extensionLoader.getLoaderFolders()
                : Map.of();

        ModDiscoverer discoverer = new ModDiscoverer();
        List<ModDiscoverer.DiscoveredMod> discovered = discoverer.discoverAll(gameRoot, loaderFolders);

        // Index discovered mods by id for lookup after dependency sort
        Map<String, ModDiscoverer.DiscoveredMod> discoveredById = new LinkedHashMap<>();
        for (ModDiscoverer.DiscoveredMod dm : discovered) {
            discoveredById.put(dm.manifest().id(), dm);
        }

        // Resolve dependencies: validates versions, detects cycles, returns ids in load order.
        // The environment map supplies the versions for environment-provided ids
        // (loader, game, java) that Fabric mods declare but that are not mods.
        Map<String, String> environment = new HashMap<>();
        environment.put("minecraft", mcVersion != null ? mcVersion : "");
        environment.put("fabricloader", ModDiscoverer.FABRIC_LOADER_VERSION);
        environment.put("java", Integer.toString(Runtime.version().feature()));
        DependencyResolver resolver = new DependencyResolver();
        List<ModContainer> ordered = resolver.resolve(
                discovered.stream().map(ModDiscoverer.DiscoveredMod::manifest).toList(),
                environment);

        // Register to classloader in dependency order
        mods.clear();
        for (ModContainer mc : ordered) {
            ModDiscoverer.DiscoveredMod dm = discoveredById.get(mc.getId());
            if (dm == null) {
                continue;
            }
            if (dm.format() == ModDiscoverer.ModFormat.AJE) {
                // A .aje is a ZIP wrapper: the executable mod classes live in
                // the embedded <modid>.jar (and optional lib/ jars). Extract
                // them to a temp directory and add those to the classloader;
                // a URLClassLoader cannot read classes from a nested archive.
                extractModJars(dm);
            } else {
                // Plain .jar / .litemod: the archive itself is the classpath entry
                classLoader.addModJar(dm.path());
            }
            LoadedModContainer container = new LoadedModContainer(dm.manifest(), dm.path(), dm.loaderKey());
            mods.put(container.getId(), container);
            // Register the mod's mixin configs (if any) with the Mixin environment
            registerMixins(dm.manifest());
            // Register the mod's access widener (if any) with the transformer
            registerAccessWidener(dm.manifest(), dm.path());
        }
        LOG.info("Loaded " + mods.size() + " mod(s) across " + (loaderFolders.size() + 1) + " folder(s)");
    }

    /**
     * Extracts every embedded jar (the {@code <modid>.jar} main jar plus any
     * {@code lib/} dependency jars) from a {@code .aje} archive into the mod
     * temp directory and registers each with the classloader.
     *
     * @param dm the discovered AJE mod
     */
    private void extractModJars(ModDiscoverer.DiscoveredMod dm) {
        Path modPath = dm.path();
        String modId = dm.manifest().id();
        try (FileSystem fs = FileSystems.newFileSystem(modPath, (ClassLoader) null)) {
            List<Path> jars;
            try (var stream = Files.walk(fs.getPath("/"))) {
                jars = stream
                        .filter(p -> p.toString().endsWith(".jar"))
                        .filter(p -> !p.toString().contains("resources/"))
                        .filter(p -> !p.toString().contains("mixins/"))
                        .toList();
            }
            if (jars.isEmpty()) {
                LOG.warning("No embedded jar found in .aje for mod " + modId
                        + "; entrypoints will not resolve");
                return;
            }
            for (Path jar : jars) {
                String name = jar.getFileName().toString();
                Path target = getExtensionTempDir().resolve(modId + "_" + name);
                try (InputStream is = Files.newInputStream(jar)) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
                classLoader.addModJar(target);
            }
        } catch (IOException e) {
            LOG.warning("Failed to extract embedded jars from " + modPath + ": " + e.getMessage());
        }
    }

    /**
     * Convenience entry point that runs both phases in order. Automatically
     * branches between JE and BE loading based on the {@code mcEdit} bound
     * at initialization: JE runs {@link #loadMods} (scans {@code mods/} +
     * loader folders), BE runs {@link #loadBedrockMods} (scans
     * {@code aprism_mods/}).
     *
     * @param gameRoot      the game instance root
     * @param extensionsDir the extensions directory (may be absent)
     * @throws DependencyResolutionException if dependencies cannot be resolved (JE only)
     */
    public void performLoad(Path gameRoot, Path extensionsDir) throws DependencyResolutionException {
        loadExtensions(extensionsDir);
        if ("BE".equalsIgnoreCase(mcEdit)) {
            loadBedrockMods(gameRoot);
        } else {
            loadMods(gameRoot);
        }
    }

    /**
     * Production bootstrap used by the agent's premain: performs the full
     * two-phase load from the game root (extensions from
     * {@code <gameRoot>/aprism-extensions}) and then drives the common
     * lifecycle (PREINIT -> INIT -> SETUP -> COMPLETE). Distribution-specific
     * CLIENT/SERVER phases are not dispatched here; game-event-driven phase
     * dispatch is a later milestone.
     *
     * @param gameRoot the game instance root (e.g. the {@code .minecraft} directory)
     * @throws DependencyResolutionException if dependencies cannot be resolved (JE only)
     */
    public void bootstrapProduction(Path gameRoot) throws DependencyResolutionException {
        bootstrapProduction(gameRoot, null);
    }

    /**
     * Production bootstrap with an explicit distribution side. After the
     * common lifecycle (PREINIT -> INIT -> SETUP -> COMPLETE), the side phase
     * is dispatched: {@link AprismPhase#CLIENT} for {@code side=client},
     * {@link AprismPhase#SERVER} for {@code side=server}. A {@code null} or
     * unrecognized side skips the side phase (premain cannot determine the
     * distribution side on its own; the launcher supplies it).
     *
     * @param gameRoot the game instance root (e.g. the {@code .minecraft} directory)
     * @param side     the distribution side ({@code client}, {@code server}, or {@code null})
     * @throws DependencyResolutionException if dependencies cannot be resolved (JE only)
     */
    public void bootstrapProduction(Path gameRoot, String side) throws DependencyResolutionException {
        ensureInitialized();
        performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
        invokeCommonLifecycle();
        AprismPhase sidePhase = sidePhaseFor(side);
        if (sidePhase != null) {
            LOG.info("Dispatching side phase: " + sidePhase);
            invokeEntrypoints(sidePhase);
        }
    }

    /**
     * Maps the {@code side} agent argument to a distribution phase.
     *
     * @param side the side argument ({@code client}, {@code server}, or {@code null})
     * @return the corresponding phase, or {@code null} if the side is absent or unrecognized
     */
    private static AprismPhase sidePhaseFor(String side) {
        if (side == null) {
            return null;
        }
        return switch (side.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "client" -> AprismPhase.CLIENT;
            case "server" -> AprismPhase.SERVER;
            default -> null;
        };
    }

    /**
     * Legacy single-folder mod loader. Scans only the given directory (treated
     * as Aprism native) without dependency resolution. Retained for backwards
     * compatibility; new callers should use {@link #loadMods(Path)} with the
     * game root.
     *
     * @param modsDir the mods directory
     */
    public void loadModsFromDirectory(Path modsDir) {
        ensureInitialized();
        ModDiscoverer discoverer = new ModDiscoverer();
        for (ModDiscoverer.DiscoveredMod dm : discoverer.discover(modsDir)) {
            classLoader.addModJar(dm.path());
            LoadedModContainer container = new LoadedModContainer(dm.manifest(), dm.path(), dm.loaderKey());
            mods.put(container.getId(), container);
        }
    }

    /**
     * Phase 2 (BE): scans the {@code aprism_mods/} directory under the given
     * game root for {@code .abe} mod archives, parses their manifests, and
     * registers each as a {@link LoadedBedrockModContainer}.
     *
     * <p>Per FACT.md 9.16, BE mods do NOT use the Java classloader. They
     * consist of native binaries, Script API sources, and BP/RP content.
     * This method discovers and validates the mods; the actual native loading
     * is performed by the platform-specific Aprism injector which consumes
     * the {@link LoadedBedrockModContainer#nativeLibraries()} map.
     *
     * <p>BE version support starts from 26.x only. Mods whose manifest
     * declares a pre-26.x BE version are still loaded (version validation
     * against the running BE version is the injector's responsibility, since
     * it holds the signature DB).
     *
     * @param gameRoot the BE game root (typically {@code com.mojang/})
     */
    public void loadBedrockMods(Path gameRoot) {
        ensureInitialized();
        bedrockMods.clear();
        BedrockModDiscoverer discoverer = new BedrockModDiscoverer();
        List<BedrockModDiscoverer.DiscoveredBedrockMod> discovered = discoverer.discover(gameRoot);
        for (BedrockModDiscoverer.DiscoveredBedrockMod dm : discovered) {
            LoadedBedrockModContainer container = new LoadedBedrockModContainer(
                    dm.manifest(), dm.archivePath(), dm.nativeLibraries(),
                    dm.hasBehaviorPack(), dm.hasResourcePack(), dm.hasScripts());
            bedrockMods.put(container.getId(), container);
        }
        LOG.info("Loaded " + bedrockMods.size() + " BE mod(s) from aprism_mods/");
    }

    /**
     * Drives the mod lifecycle by invoking the entrypoints corresponding to
     * the given phase on every loaded mod, in dependency order.
     *
     * <p>Phase-to-entrypoint mapping:
     * <ul>
     *   <li>{@link AprismPhase#PREINIT} -> {@code main} entrypoint,
     *       {@link IAprismMod#onPreInitialize}</li>
     *   <li>{@link AprismPhase#INIT} -> {@code main} entrypoint,
     *       {@link IAprismMod#onInitialize}</li>
     *   <li>{@link AprismPhase#SETUP} -> {@code main} entrypoint,
     *       {@link IAprismMod#onSetup}</li>
     *   <li>{@link AprismPhase#COMPLETE} -> {@code main} entrypoint,
     *       {@link IAprismMod#onComplete}</li>
     *   <li>{@link AprismPhase#CLIENT} -> {@code client} entrypoint,
     *       {@link IAprismMod#onInitialize}</li>
     *   <li>{@link AprismPhase#SERVER} -> {@code server} entrypoint,
     *       {@link IAprismMod#onInitialize}</li>
     * </ul>
     *
     * @param phase the phase to invoke
     */
    public void invokeEntrypoints(AprismPhase phase) {
        ensureInitialized();
        String entrypointKey = entrypointKeyFor(phase);
        if (entrypointKey == null) {
            return;
        }
        for (LoadedModContainer container : mods.values()) {
            invokeModEntrypoint(container, entrypointKey, phase);
        }
    }

    /**
     * Invokes the entrypoint of a single mod for the given phase.
     *
     * @param container     the mod container
     * @param entrypointKey the entrypoint key ({@code main}, {@code client}, {@code server})
     * @param phase         the lifecycle phase
     */
    private void invokeModEntrypoint(LoadedModContainer container, String entrypointKey, AprismPhase phase) {
        AprismManifest manifest = container.getManifest();
        List<String> entrypoints = manifest.entrypoints() == null
                ? List.of()
                : manifest.entrypoints().getOrDefault(entrypointKey, List.of());
        if (entrypoints.isEmpty()) {
            return;
        }
        AprismContext context = new AprismContextImpl(container, eventBus, registry);
        boolean isFabric = ModDiscoverer.FABRIC_KEY.equals(container.getLoaderKey());
        for (String className : entrypoints) {
            try {
                Class<?> clazz = classLoader.loadClass(className);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (instance instanceof IAprismMod mod) {
                    // Aprism-native mod: full lifecycle dispatch
                    invokePhaseMethod(mod, context, phase);
                } else if (isFabric) {
                    // Fabric mod: invoke the Fabric-convention entrypoint
                    FabricEntrypointBridge.invoke(instance, phase);
                }
                // Retain the first instantiated instance on the container
                if (container.getInstance() == null) {
                    container.setInstance(instance);
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke entrypoint " + className
                        + " for mod " + container.getId() + " in phase " + phase, e);
            }
        }
    }

    /**
     * Maps a lifecycle phase to the entrypoint key that should be invoked.
     *
     * @param phase the lifecycle phase
     * @return the entrypoint key, or {@code null} if the phase has no entrypoint
     */
    private static String entrypointKeyFor(AprismPhase phase) {
        return switch (phase) {
            case PREINIT, INIT, SETUP, COMPLETE -> "main";
            case CLIENT -> "client";
            case SERVER -> "server";
        };
    }

    /**
     * Dispatches the appropriate lifecycle method on the mod instance.
     *
     * @param mod     the mod instance
     * @param context the mod-scoped context
     * @param phase   the lifecycle phase
     */
    private static void invokePhaseMethod(IAprismMod mod, AprismContext context, AprismPhase phase) {
        switch (phase) {
            case PREINIT -> mod.onPreInitialize(context);
            case INIT -> mod.onInitialize(context);
            case SETUP -> mod.onSetup(context);
            case COMPLETE -> mod.onComplete(context);
            case CLIENT, SERVER -> mod.onInitialize(context);
        }
    }

    /**
     * Convenience: runs the full common lifecycle (PREINIT -> INIT -> SETUP ->
     * COMPLETE). Skips CLIENT/SERVER since those are distribution-specific.
     */
    public void invokeCommonLifecycle() {
        invokeEntrypoints(AprismPhase.PREINIT);
        invokeEntrypoints(AprismPhase.INIT);
        invokeEntrypoints(AprismPhase.SETUP);
        invokeEntrypoints(AprismPhase.COMPLETE);
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
     * @return the loaded extensions (raw manifests + source paths from phase 1)
     */
    public List<ExtensionLoader.LoadedExtension> getLoadedExtensions() {
        return List.copyOf(loadedExtensions);
    }

    /**
     * @return the loaded extension containers (with instantiated entrypoints)
     */
    public List<LoadedExtensionContainer> getLoadedExtensionContainers() {
        return List.copyOf(extensionContainers.values());
    }

    /**
     * Returns the extension container for the given extension id.
     *
     * @param id the extension id
     * @return the container, or {@code null} if no such extension is loaded
     */
    public LoadedExtensionContainer getExtension(String id) {
        return extensionContainers.get(id);
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
    public LoadedModContainer getMod(String id) {
        return mods.get(id);
    }

    /**
     * @return all loaded mod containers, in dependency-resolved load order
     */
    public List<LoadedModContainer> getMods() {
        return List.copyOf(mods.values());
    }

    /**
     * @return all loaded Bedrock mod containers (BE mode only)
     */
    public List<LoadedBedrockModContainer> getBedrockMods() {
        return List.copyOf(bedrockMods.values());
    }

    /**
     * Returns the Bedrock mod container for the given id.
     *
     * @param id the mod id
     * @return the container, or {@code null} if no such BE mod is loaded
     */
    public LoadedBedrockModContainer getBedrockMod(String id) {
        return bedrockMods.get(id);
    }

    /**
     * Returns the loader key that loaded the given mod id.
     *
     * @param id the mod id
     * @return the loader key (e.g. {@code "aprism"}, {@code "Fa"}, ...), or empty
     */
    public Optional<String> getLoaderKey(String id) {
        LoadedModContainer mc = mods.get(id);
        return mc == null ? Optional.empty() : Optional.of(mc.getLoaderKey());
    }

    private void ensureInitialized() {
        if (classLoader == null) {
            throw new IllegalStateException("AprismRuntime not initialized; call initialize() first");
        }
    }

    /**
     * Registers every mixin config declared in a mod manifest with the Mixin
     * environment. The {@code mixins} field of {@link AprismManifest} holds a
     * list of config resource paths (e.g. {@code "mymod.mixins.json"}); each
     * is offered to {@link AprismMixinBootstrap#offerMixinConfig}.
     *
     * @param manifest the mod manifest (may have a null or empty mixins list)
     */
    private void registerMixins(AprismManifest manifest) {
        if (manifest == null || manifest.mixins() == null || manifest.mixins().isEmpty()) {
            return;
        }
        for (String config : manifest.mixins()) {
            AprismMixinBootstrap.offerMixinConfig(config);
        }
    }

    /**
     * Registers a mixin configuration with the Mixin environment. Exposed for
     * extensions and advanced callers that need to register mixin configs
     * outside the normal mod-manifest flow. Re-binds the Mixin environment
     * (idempotent) in case the caller skipped {@link #initialize}.
     *
     * @param configName the mixin config resource path (e.g. "mymod.mixins.json")
     */
    public void offerMixinConfig(String configName) {
        AprismMixinBootstrap.bootstrap(classLoader);
        AprismMixinBootstrap.offerMixinConfig(configName);
    }

    /**
     * Reads the access widener file declared in a mod manifest and registers
     * its rules with the {@link AprismClassTransformer}'s {@link AccessWidener}.
     * The widener file is read from inside the mod archive (.{@code aje} or
     * {@code .jar}) at the path specified by the manifest's
     * {@code accessWidener} field.
     *
     * @param manifest the mod manifest (may have a null accessWidener field)
     * @param modPath  the path to the mod archive
     */
    private void registerAccessWidener(AprismManifest manifest, Path modPath) {
        if (manifest == null || manifest.accessWidener() == null || manifest.accessWidener().isBlank()) {
            return;
        }
        if (transformer == null) {
            return;
        }
        transformer.getAccessWidener().parseFromArchive(modPath, manifest.accessWidener());
        LOG.info("Registered access widener: " + manifest.accessWidener()
                + " from mod " + manifest.id());
    }

    /**
     * @return whether the SpongePowered Mixin environment has been bootstrapped
     *         and a transformer is available for class transformation
     */
    public boolean isMixinAvailable() {
        return AprismMixinBootstrap.isAvailable();
    }

    /**
     * Shuts down the runtime, closing the classloader and releasing all
     * held resources (mod jars, extension handles). After shutdown the
     * runtime must be re-initialized via {@link #initialize} before use.
     *
     * <p>This is primarily intended for tests and graceful application
     * shutdown. On Windows it is essential to call this before deleting
     * mod jar files, because {@link AprismClassLoader} (a URLClassLoader)
     * holds file locks on every added jar.
     */
    public void shutdown() {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                LOG.warning("Failed to close AprismClassLoader: " + e.getMessage());
            }
        }
        classLoader = null;
        eventBus = null;
        registry = null;
        entryPointInvoker = null;
        transformer = null;
        extensionLoader = null;
        instrumentation = null;
        mcProfile = null;
        bytecodeRemapper = null;
        mods.clear();
        bedrockMods.clear();
        loadedExtensions.clear();
        extensionContainers.clear();
        cleanupExtensionTempDir();
        AprismMixinBootstrap.reset();
    }

    /**
     * Deletes the temporary directory used for extracted extension jars.
     */
    private void cleanupExtensionTempDir() {
        if (extensionTempDir == null) {
            return;
        }
        try (var stream = Files.walk(extensionTempDir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> {
                      try {
                          Files.deleteIfExists(p);
                      } catch (IOException ignored) {
                          // best-effort cleanup
                      }
                  });
        } catch (IOException e) {
            LOG.warning("Failed to clean extension temp directory: " + e.getMessage());
        }
        extensionTempDir = null;
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
