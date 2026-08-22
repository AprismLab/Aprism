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
import com.aprism.loader.remap.VersionLineEntry;
import com.aprism.loader.remap.VersionLineRegistry;
import com.aprism.loader.bedrock.BedrockInjectionCoordinator;
import com.aprism.loader.loaderext.LoaderEntrypointHandler;
import com.aprism.loader.gameevent.GameEventDispatcher;
import com.aprism.loader.gameevent.GameEventHookInstaller;
import com.aprism.api.commands.CommandRegistration;
import com.aprism.api.imc.InterModComms;
import com.aprism.api.keybinding.KeyBindingRegistry;
import com.aprism.api.resourcereload.ResourceReloadRegistry;
import com.aprism.api.scheduler.TickScheduler;
import com.aprism.loader.ai.AiRegistry;
import com.aprism.loader.commands.CommandRegistrationImpl;
import com.aprism.loader.commands.CommandBindingInstaller;
import com.aprism.loader.imc.InterModCommsImpl;
import com.aprism.loader.keybinding.KeyBindingRegistryImpl;
import com.aprism.loader.keybinding.KeyBindingBindingInstaller;
import com.aprism.loader.resourcereload.ResourceReloadRegistryImpl;
import com.aprism.loader.resourcereload.ResourceReloadTrigger;
import com.aprism.loader.scheduler.TickSchedulerImpl;
import com.aprism.loader.scheduler.TickSchedulerDriver;
import com.aprism.loader.networking.NetworkingRegistry;
import com.aprism.loader.networking.NetworkTransportInstaller;
import com.aprism.loader.rendering.RenderingRegistry;
import com.aprism.loader.registry.GameRegistries;
import com.aprism.api.introspection.JvmInsight;
import com.aprism.api.aprismate.AprismateAgentDescriptor;
import com.aprism.loader.foreignlang.CrossLanguageRuntime;
import com.aprism.loader.hardware.HardwareRegistry;
import com.aprism.loader.aprismate.AprismateAgent;
import com.aprism.loader.nativebridge.NativeBridgeRegistry;
import com.aprism.loader.introspection.JvmInsightImpl;
import com.aprism.loader.settings.SettingsRegistry;
import com.aprism.loader.modmenu.ModListEntry;
import com.aprism.loader.modmenu.ModListRegistry;
import com.aprism.loader.modmenu.ModListState;
import com.aprism.loader.status.StatusPublisher;
import com.aprism.loader.contentbind.ContentBindingRunner;
import com.aprism.loader.contentbind.BrigadierCommandBinder;
import com.aprism.loader.logging.AprismLogging;
import com.aprism.loader.logging.AprismLogger;
import com.aprism.loader.logging.ConsoleSink;
import com.aprism.loader.logging.FileSink;
import com.aprism.loader.lowlevel.ClassLoadObserverRegistry;
import com.aprism.loader.lowlevel.ClassRedefiner;
import com.aprism.loader.lowlevel.MethodHookRegistry;
import com.aprism.loader.loaderext.LoaderEntrypointRegistry;
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
    private Path modTempDir;
    private Instrumentation instrumentation;
    /** Lower-level class redefinition API (goal #2). */
    private ClassRedefiner classRedefiner;
    /** Structured logging facility (v26.2-Alpha.1, goal #6). */
    private AprismLogging logging;
    /** Native mod list registry (v26.2-Alpha.2, goal #7). */
    private final ModListRegistry modListRegistry = new ModListRegistry();
    /** Per-mod settings registry (v26.2-Alpha.3, goal #7 part 2). */
    private final SettingsRegistry settingsRegistry = new SettingsRegistry();
    /** JVM introspection API (v26.4-Alpha.4). */
    private final JvmInsightImpl jvmInsight = new JvmInsightImpl();
    /** Native interop bridge registry (v26.4-Alpha.5). */
    private final NativeBridgeRegistry nativeBridgeRegistry = new NativeBridgeRegistry();
    /** AprismateAgent reference (v26.4-Alpha.6). */
    private AprismateAgent aprismateAgent;
    /** Performance & hardware fusion reference (v26.4-Alpha.7). */
    private final HardwareRegistry hardwareRegistry = new HardwareRegistry();
    /** Cross-language runtime (v26.4-Alpha.8). */
    private CrossLanguageRuntime crossLanguageRuntime;
    /** Game-event dispatcher (v26.3-Alpha.1, QA0 gap #1). */
    private GameEventDispatcher gameEventDispatcher;
    /** Game-event hook installer (v26.5-Alpha.3). */
    private GameEventHookInstaller gameEventHookInstaller;
    /** Typed game-content registries (v26.3-Alpha.2, QA0 gap #2). */
    private final GameRegistries gameRegistries = new GameRegistries();
    /** Networking registry (v26.3-Alpha.3, QA0 gap #4). */
    private final NetworkingRegistry networkingRegistry = new NetworkingRegistry();
    /** Network transport installer (v26.5-Alpha.8). */
    private NetworkTransportInstaller networkTransportInstaller;
    /** AI assistant registry (v26.3-Alpha.4, goal #8; experimental). */
    private final AiRegistry aiRegistry = new AiRegistry();
    private final InterModCommsImpl interModComms = new InterModCommsImpl();
    private final CommandRegistrationImpl commandRegistration = new CommandRegistrationImpl();
    /** Command binding installer (v26.5-Alpha.4). */
    private CommandBindingInstaller commandBindingInstaller;
    private final KeyBindingRegistryImpl keyBindingRegistry = new KeyBindingRegistryImpl();
    /** Key-binding binding installer (v26.5-Alpha.5). */
    private KeyBindingBindingInstaller keyBindingBindingInstaller;
    private final TickSchedulerImpl tickScheduler = new TickSchedulerImpl();
    /** Tick scheduler driver (v26.5-Alpha.6). */
    private TickSchedulerDriver tickSchedulerDriver;
    private final ResourceReloadRegistryImpl resourceReloadRegistry = new ResourceReloadRegistryImpl();
    /** Resource reload trigger (v26.5-Alpha.7). */
    private ResourceReloadTrigger resourceReloadTrigger;
    /** Rendering provider registry (v26.3-Alpha.5, goal #9; experimental). */
    private final RenderingRegistry renderingRegistry = new RenderingRegistry();
    private LoadReport loadReport;

    private String aprismVersion;
    private String mcEdit;
    private String mcVersion;
    private ExtensionLoader extensionLoader;
    private final List<ExtensionLoader.LoadedExtension> loadedExtensions = new ArrayList<>();

    private McProfile mcProfile;
    /** Resolved version-line characteristics for the running Minecraft version. */
    private VersionLineEntry versionLineEntry;
    private BytecodeRemapper bytecodeRemapper;
    private Path gameRoot;

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
        // Idempotent re-init guard: once initialized (classLoader present), a
        // repeated initialize() is a no-op. This protects production boot where
        // the agent may be entered more than once (premain + agentmain, or a
        // re-triggered load) from double-registering the class transformer and
        // re-loading mods. shutdown() nulls classLoader, so a subsequent
        // initialize() after shutdown() proceeds normally (test setUp/tearDown
        // pairs rely on this).
        if (classLoader != null) {
            LOG.info("AprismRuntime already initialized; ignoring duplicate initialize()");
            return;
        }
        this.instrumentation = inst;
        // v26.1-Alpha.8 lower-level API (goal #2): runtime class redefinition.
        this.classRedefiner = inst != null ? new ClassRedefiner(inst) : null;
        // v26.4-Alpha.6 AprismateAgent reference: detect the runtime and
        // assemble the capability descriptor (proven, never assumed).
        this.aprismateAgent = new AprismateAgent(inst);
        // v26.2-Alpha.1 structured logging facility (goal #6): console sink by
        // default; a file sink is attached under aprism-logs/ once the game
        // root is known (performLoad). The retained ring buffer backs crash
        // reports and the load report.
        this.logging = new AprismLogging();
        this.logging.attachSink(new ConsoleSink());
        this.aprismVersion = aprismVersion;
        this.mcEdit = mcEdit;
        this.mcVersion = mcVersion;
        this.transformer = externalTransformer != null
                ? externalTransformer
                : new AprismClassTransformer();
        this.classLoader = new AprismClassLoader(getClass().getClassLoader(), transformer);
        this.eventBus = new AprismEventBusImpl();
        // v26.3-Alpha.1 game-event dispatch (QA0 gap #1): the dispatcher
        // translates native hook calls into typed events on the shared bus.
        // It stays detached until the runtime marks it attached.
        this.gameEventDispatcher = new GameEventDispatcher(eventBus);
        this.gameEventHookInstaller = new GameEventHookInstaller(gameEventDispatcher);
        this.commandBindingInstaller = new CommandBindingInstaller(commandRegistration);
        this.keyBindingBindingInstaller = new KeyBindingBindingInstaller(keyBindingRegistry);
        this.tickSchedulerDriver = new TickSchedulerDriver(tickScheduler, eventBus);
        this.resourceReloadTrigger = new ResourceReloadTrigger(resourceReloadRegistry);
        this.networkTransportInstaller = new NetworkTransportInstaller(networkingRegistry);
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
        // v26.1-Alpha.7 version-line foundation (goal #1): resolve the running
        // Minecraft version against the supported JE line (1.20 .. 26.2) and
        // record its characteristics (profile, Java baseline, mappings source).
        this.versionLineEntry = VersionLineRegistry.resolve(mcVersion).orElse(null);
        if (versionLineEntry == null) {
            LOG.warning("Minecraft " + mcVersion + " is below the supported JE line ("
                    + VersionLineRegistry.describeLine() + "); loading may fail");
        } else if (!VersionLineRegistry.isWithinSupportedLine(mcVersion)) {
            LOG.info("Minecraft " + mcVersion + " is beyond the explicit JE line window ("
                    + VersionLineRegistry.describeLine() + "); using " + mcProfile);
        } else {
            LOG.info("Minecraft " + mcVersion + " resolved on JE line "
                    + VersionLineRegistry.describeLine() + " (profile=" + mcProfile
                    + ", java>=" + versionLineEntry.javaBaseline()
                    + ", mappings=" + versionLineEntry.mappingsSource() + ")");
        }
        this.bytecodeRemapper = null;
        this.classLoader.setBytecodeRemapper(null);

        // Bootstrap the SpongePowered Mixin environment so that @Mixin/@Inject
        // annotations declared by loaded mods are applied via the transformer.
        // The bootstrap is fully fault-tolerant: any failure is logged and
        // swallowed so a broken Mixin environment never blocks game startup.
        AprismMixinBootstrap.bootstrap(classLoader);
        // v26.2-Alpha.6 hardening: mirror key lifecycle events into the
        // structured facility so the crash report's log tail is actionable.
        this.logging.getLogger("runtime").info("Aprism runtime initialized: "
                + aprismVersion + " / " + mcEdit + " / " + mcVersion);
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
     * Returns the structured logging facility (v26.2-Alpha.1, goal #6).
     * Available after {@link #initialize}; sinks fan out per-unit records and
     * the retained ring buffer backs crash reports and the load report.
     *
     * @return the logging facility, or null before initialize
     */
    public AprismLogging getLogging() {
        return logging;
    }

    /**
     * Obtains a per-unit structured logger (v26.2-Alpha.1, goal #6).
     *
     * @param unit the unit name (mod id, extension id, or component)
     * @return the logger bound to the facility and unit
     */
    public AprismLogger getLogger(String unit) {
        if (logging == null) {
            throw new IllegalStateException("runtime not initialized");
        }
        return logging.getLogger(unit);
    }

    /**
     * Attaches the aprism-logs file sink once the game root is known
     * (v26.2-Alpha.1, goal #6). Idempotent per game root: repeated
     * performLoad calls against the same root do not double-attach.
     *
     * @param gameRoot the game instance root
     */
    private void attachLogFileSink(Path gameRoot) {
        if (logging == null || gameRoot == null) {
            return;
        }
        for (var sink : logging.getSinks()) {
            if (sink instanceof FileSink) {
                return;
            }
        }
        try {
            logging.attachSink(new FileSink(gameRoot.resolve("aprism-logs").resolve("aprism.log")));
        } catch (IOException e) {
            LOG.warning("Could not open aprism-logs/aprism.log: " + e.getMessage());
        }
    }

    /**
     * Returns the native mod list registry (v26.2-Alpha.2, goal #7). The
     * registry is rebuilt at the end of every {@link #performLoad} pass from
     * the loaded mod and extension containers plus the load report failures.
     *
     * @return the mod list registry
     */
    public ModListRegistry getModList() {
        return modListRegistry;
    }

    /**
     * Returns the per-mod settings registry (v26.2-Alpha.3, goal #7 part 2).
     * Populated during {@link #performLoad} from every loaded mod's manifest
     * settings declarations; user values persist under
     * {@code <game-root>/config/aprism-settings/}.
     *
     * @return the settings registry
     */
    /**
     * Returns the rendering provider registry (v26.3-Alpha.5, goal #9).
     * Experimental / reference-only: no production guarantee.
     *
     * @return the rendering registry
     */
    public RenderingRegistry getRenderingRegistry() {
        return renderingRegistry;
    }

    /**
     * Returns the AI assistant registry (v26.3-Alpha.4, goal #8).
     * Experimental / reference-only: no production guarantee.
     *
     * @return the AI registry
     */
    public AiRegistry getAiRegistry() {
        return aiRegistry;
    }

    /**
     * @return the inter-mod communication surface (Forge/NeoForge parity,
     *         v26.3-Alpha.7)
     */
    public InterModComms getInterModComms() {
        return interModComms;
    }

    /**
     * @return the command registration surface (Fabric parity,
     *         v26.3-Alpha.8)
     */
    public CommandRegistration getCommandRegistration() {
        return commandRegistration;
    }

    /**
     * Returns the command binding installer (v26.5-Alpha.4) that bridges
     * registered commands into the MC command dispatcher. The platform
     * adapter layer attaches a {@code CommandDispatcherBridge} through this
     * installer.
     *
     * @return the command binding installer, or null before initialize
     */
    public CommandBindingInstaller getCommandBindingInstaller() {
        return commandBindingInstaller;
    }

    /**
     * @return the key-binding registry (Fabric parity, v26.3-Alpha.8)
     */
    public KeyBindingRegistry getKeyBindingRegistry() {
        return keyBindingRegistry;
    }

    /**
     * Returns the key-binding binding installer (v26.5-Alpha.5) that bridges
     * registered key bindings into the MC input system. The platform adapter
     * layer attaches an {@code InputSystemBridge} through this installer.
     *
     * @return the key-binding binding installer, or null before initialize
     */
    public KeyBindingBindingInstaller getKeyBindingBindingInstaller() {
        return keyBindingBindingInstaller;
    }

    /**
     * @return the tick-task scheduler (Fabric parity, v26.3-Alpha.9)
     */
    public TickScheduler getTickScheduler() {
        return tickScheduler;
    }

    /**
     * Returns the tick scheduler driver (v26.5-Alpha.6) that drives the
     * scheduler from real game-tick events. The platform adapter layer
     * sets the active side and attaches the driver.
     *
     * @return the tick scheduler driver, or null before initialize
     */
    public TickSchedulerDriver getTickSchedulerDriver() {
        return tickSchedulerDriver;
    }

    /**
     * @return the resource-reload listener registry (Fabric parity,
     *         v26.3-Alpha.9)
     */
    public ResourceReloadRegistry getResourceReloadRegistry() {
        return resourceReloadRegistry;
    }

    /**
     * Returns the resource-reload trigger (v26.5-Alpha.7) that fires
     * {@code fireReload()} from real MC resource-manager reload events.
     *
     * @return the resource-reload trigger, or null before initialize
     */
    public ResourceReloadTrigger getResourceReloadTrigger() {
        return resourceReloadTrigger;
    }

    /**
     * Returns the networking registry (v26.3-Alpha.3, QA0 gap #4): packet
     * channels, listeners, and the transport seam. Sends are fail-closed
     * until a transport is attached.
     *
     * @return the networking registry
     */
    public NetworkingRegistry getNetworking() {
        return networkingRegistry;
    }

    /**
     * Returns the network transport installer (v26.5-Alpha.8) that bridges
     * the MC network stack into the networking registry. The platform adapter
     * layer attaches a transport and delivers inbound packets through this
     * installer.
     *
     * @return the network transport installer, or null before initialize
     */
    public NetworkTransportInstaller getNetworkTransportInstaller() {
        return networkTransportInstaller;
    }

    /**
     * Returns the typed game-content registries (v26.3-Alpha.2, QA0 gap #2):
     * block, item, and entity registries with typed content records. The
     * native game binding (projecting registered content into real Minecraft
     * registries) is delegated to the platform adapter layer.
     *
     * @return the typed game registries
     */
    public GameRegistries getGameRegistries() {
        return gameRegistries;
    }

    /**
     * Returns the game-event dispatcher (v26.3-Alpha.1, QA0 gap #1). Native
     * hooks fire typed game events (tick/render/world) through it onto the
     * shared event bus. Detached until explicitly attached; dropped events
     * never reach half-initialized listeners.
     *
     * @return the game-event dispatcher, or null before initialize
     */
    public GameEventDispatcher getGameEventDispatcher() {
        return gameEventDispatcher;
    }

    /**
     * Returns the game-event hook installer (v26.5-Alpha.3) that bridges
     * Minecraft's game loop methods into the game-event dispatcher via the
     * low-level method-hook API. The platform adapter layer uses this to
     * register version-specific tick/render/world method targets.
     *
     * @return the game-event hook installer, or null before initialize
     */
    public GameEventHookInstaller getGameEventHookInstaller() {
        return gameEventHookInstaller;
    }

    public SettingsRegistry getSettings() {
        return settingsRegistry;
    }

    /**
     * @return the JVM introspection surface (v26.4-Alpha.4): threads,
     *         class stats, heap, GC, JIT and VM identity
     */
    public JvmInsight getJvmInsight() {
        return jvmInsight;
    }

    /**
     * @return the native interop bridge registry (v26.4-Alpha.5): the seam
     *         through which FFM-backed native providers (on AprismJDK)
     *         register themselves
     */
    public NativeBridgeRegistry getNativeBridgeRegistry() {
        return nativeBridgeRegistry;
    }

    /**
     * @return the AprismateAgent capability descriptor (v26.4-Alpha.6), or
     *         {@code null} before {@link #initialize} has run
     */
    public AprismateAgentDescriptor getAprismateDescriptor() {
        return aprismateAgent == null ? null : aprismateAgent.descriptor();
    }

    /**
     * @return the hardware insight registry (performance & hardware fusion
     *         reference, v26.4-Alpha.7): advisory CPU/cache/NUMA values
     *         with a replaceable deep probe
     */
    public HardwareRegistry getHardwareRegistry() {
        return hardwareRegistry;
    }

    /**
     * @return the cross-language runtime (Cpp2Java / Rust2Java reference,
     *         v26.4-Alpha.8): bindings invoked through the native bridge
     *         seam
     */
    public CrossLanguageRuntime getCrossLanguageRuntime() {
        if (crossLanguageRuntime == null) {
            crossLanguageRuntime = new CrossLanguageRuntime(nativeBridgeRegistry);
        }
        return crossLanguageRuntime;
    }

    /**
     * Rebuilds the mod list registry from the current runtime state
     * (v26.2-Alpha.2, goal #7). Every loaded extension and mod contributes a
     * LOADED entry; every failed unit recorded in the load report contributes
     * a FAILED entry (unless already present as loaded).
     */
    private void rebuildModList() {
        Map<String, ModListEntry> entries = new LinkedHashMap<>();
        for (LoadedExtensionContainer ext : extensionContainers.values()) {
            var manifest = ext.getManifest();
            String extVersion = manifest.version() != null ? manifest.version()
                    : (manifest.loaderRange() == null ? "" : manifest.loaderRange());
            entries.put(ext.getExtensionId(), new ModListEntry(
                    ext.getExtensionId(),
                    extVersion,
                    ext.getExtensionId(),
                    manifest.type() == null ? "" : manifest.type(),
                    "extension",
                    manifest.loaderKey() == null ? "aprism" : manifest.loaderKey(),
                    ext.getSourcePath() == null ? "" : ext.getSourcePath().getFileName().toString(),
                    manifest.depends() == null ? Map.of() : manifest.depends(),
                    ModListState.LOADED));
        }
        for (LoadedModContainer mod : mods.values()) {
            var manifest = mod.getManifest();
            entries.put(mod.getId(), new ModListEntry(
                    mod.getId(),
                    mod.getVersion(),
                    mod.getDisplayName(),
                    mod.getDescription() == null ? "" : mod.getDescription(),
                    "mod",
                    mod.getLoaderKey(),
                    mod.getSourcePath() == null ? "" : mod.getSourcePath().getFileName().toString(),
                    manifest.depends() == null ? Map.of() : manifest.depends(),
                    ModListState.LOADED));
        }
        if (loadReport != null) {
            for (LoadReport.Entry entry : loadReport.failures()) {
                if (!entries.containsKey(entry.id())) {
                    entries.put(entry.id(), new ModListEntry(
                            entry.id(),
                            entry.version() == null ? "" : entry.version(),
                            entry.id(),
                            entry.failure() == null ? "" : entry.failure(),
                            entry.kind(),
                            "unknown",
                            "",
                            Map.of(),
                            ModListState.FAILED));
                }
            }
        }
        modListRegistry.rebuild(entries);
    }

    /**
     * Returns the resolved version-line characteristics for the running
     * Minecraft version, or {@code null} when the version is below the
     * supported JE line.
     *
     * @return the version-line entry, or {@code null}
     */
    public VersionLineEntry getVersionLineEntry() {
        return versionLineEntry;
    }

    /**
     * Returns the lower-level class redefinition API, or {@code null} when the
     * runtime was initialized without an instrumentation handle (e.g. tests).
     *
     * @return the class redefiner, or {@code null}
     */
    public ClassRedefiner getClassRedefiner() {
        return classRedefiner;
    }

    /**
     * @return the load-time class observer registry (deep bytecode-hook
     *         API, v26.4-Alpha.3), or {@code null} if the runtime was
     *         initialized without a class transformer
     */
    public ClassLoadObserverRegistry getClassLoadObservers() {
        return transformer == null ? null : transformer.getClassLoadObservers();
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
     * @return the startup load report for this boot, or {@code null} before
     *         {@link #bootstrapProduction(Path, String)} has run
     */
    public LoadReport getLoadReport() {
        return loadReport;
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
        LoaderEntrypointRegistry.clear();
        MethodHookRegistry.clear();
        classRedefiner = null;
        if (loadReport != null) {
            loadReport.beginPhase1();
        }

        // Phase 1a: validate and register manifest-driven loader-support folders
        // extensionLoader.load returns an immutable snapshot; copy so we can sort
        List<ExtensionLoader.LoadedExtension> raw =
                new ArrayList<>(extensionLoader.load(extensionsDir));
        // v26.1-Alpha.9 (goal #3): higher priority initializes first
        raw.sort((a, b) -> Integer.compare(
                b.manifest().priority(), a.manifest().priority()));
        loadedExtensions.addAll(raw);

        // Phase 1b: extract embedded jars into the classloader
        for (ExtensionLoader.LoadedExtension ext : raw) {
            extractExtensionJars(ext.sourcePath());
        }

        // Phase 1c: instantiate entrypoints and invoke onInitialize. A failing
        // extension is isolated (logged + reported) so one broken extension
        // cannot take down the whole boot.
        // v26.1-Alpha.9 (goal #3): validate extension dependencies against the
        // full discovered set (every extension id plus every provides capability),
        // then instantiate. Dependencies reference ids or capabilities; a missing
        // dependency isolates that extension (logged + reported), it does not
        // abort the boot.
        java.util.Map<String, String> available = new java.util.HashMap<>();
        for (ExtensionLoader.LoadedExtension ext : raw) {
            available.put(ext.manifest().extensionId(), ext.manifest().version());
            if (ext.manifest().provides() != null) {
                for (String cap : ext.manifest().provides()) {
                    available.putIfAbsent(cap, ext.manifest().version());
                }
            }
        }
        for (ExtensionLoader.LoadedExtension ext : raw) {
            if (!extensionDependenciesSatisfied(ext, available)) {
                LOG.warning("Extension " + ext.manifest().extensionId()
                        + " has unsatisfied depends; skipping it");
                if (loadReport != null) {
                    loadReport.recordFailure("extension", ext.manifest().extensionId(),
                            ext.manifest().loaderRange(), 0, "unsatisfied depends");
                }
                continue;
            }
            long t0 = System.nanoTime();
            try {
                instantiateExtension(ext);
                if (loadReport != null) {
                    loadReport.recordOk("extension", ext.manifest().extensionId(),
                            ext.manifest().loaderRange(), (System.nanoTime() - t0) / 1_000_000);
                }
            } catch (RuntimeException e) {
                long ms = (System.nanoTime() - t0) / 1_000_000;
                LOG.warning("Extension " + ext.manifest().extensionId()
                        + " failed to initialize; skipping it: " + e.getMessage());
                if (loadReport != null) {
                    loadReport.recordFailure("extension", ext.manifest().extensionId(),
                            ext.manifest().loaderRange(), ms, String.valueOf(e.getMessage()));
                }
            }
        }

        // v26.1-Alpha.9 (goal #3): post-initialize hook after all extensions
        for (LoadedExtensionContainer container : extensionContainers.values()) {
            Object instance = container.getInstance();
            if (instance instanceof IAprismExtension extension) {
                try {
                    ExtensionContext context = new ExtensionContextImpl(
                            container, eventBus, registry,
                            (loaderKey, folder) -> extensionLoader.addLoaderFolder(loaderKey, folder),
                            assistant -> aiRegistry.register((com.aprism.api.ai.AiAssistant) assistant),
                            provider -> renderingRegistry.register((com.aprism.api.rendering.RenderingProvider) provider),
                            provider -> nativeBridgeRegistry.register((com.aprism.api.nativebridge.NativeBridgeProvider) provider));
                    extension.onPostInitialize(context);
                } catch (RuntimeException e) {
                    LOG.warning("Extension " + container.getExtensionId()
                            + " failed in onPostInitialize: " + e.getMessage());
                }
            }
        }

        if (loadReport != null) {
            loadReport.endPhase1();
        }
        LOG.info("Loaded " + extensionContainers.size() + " Aprism extension(s); "
                + extensionLoader.getLoaderFolders().size() + " loader-support folder(s) registered");
        return List.copyOf(extensionContainers.values());
    }


    /**
     * Whether all {@code depends} entries of an extension are satisfied by the
     * available set (every discovered extension id plus every provides
     * capability). Version-range matching uses the full Aprism/SemVer
     * {@link VersionRange} (v26.5-Alpha.2, known-issue #6). A dependency
     * whose version range is {@code null}, empty, or {@code *} matches any
     * version. An unparseable range falls back to "satisfied" so that
     * non-conforming manifests do not block the boot; the manifest
     * validator is the authoritative gate.
     *
     * @param ext       the extension to validate
     * @param available the map of available extension ids to their versions
     *                  (version may be null)
     * @return true when every depends entry is present and its version
     *         satisfies the declared range
     */
    private boolean extensionDependenciesSatisfied(
            ExtensionLoader.LoadedExtension ext, java.util.Map<String, String> available) {
        java.util.Map<String, String> depends = ext.manifest().depends();
        if (depends == null || depends.isEmpty()) {
            return true;
        }
        for (var entry : depends.entrySet()) {
            String depId = entry.getKey();
            String depRange = entry.getValue();
            if (!available.containsKey(depId)) {
                return false;
            }
            String depVersion = available.get(depId);
            if (depVersion == null || depVersion.isBlank()) {
                continue; // presence-only when no version declared
            }
            if (!extensionRangeSatisfied(depVersion, depRange)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether a concrete version satisfies a range expression, using
     * the full Aprism/SemVer {@link VersionRange}. A {@code null}, empty, or
     * {@code *} range matches anything. An unparseable range or version falls
     * back to "satisfied" so that non-conforming manifests do not block the
     * boot. (v26.5-Alpha.2)
     *
     * @param actual the available version
     * @param range  the required range expression
     * @return whether {@code actual} satisfies {@code range}
     */
    private boolean extensionRangeSatisfied(String actual, String range) {
        if (range == null || range.isEmpty() || "*".equals(range.trim())) {
            return true;
        }
        try {
            return com.aprism.manifest.VersionRange.parse(range).contains(actual);
        } catch (IllegalArgumentException e) {
            return true; // non-conforming: do not block
        }
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
                        (loaderKey, folder) -> extensionLoader.addLoaderFolder(loaderKey, folder),
                        assistant -> aiRegistry.register((com.aprism.api.ai.AiAssistant) assistant),
                            provider -> renderingRegistry.register((com.aprism.api.rendering.RenderingProvider) provider),
                            provider -> nativeBridgeRegistry.register((com.aprism.api.nativebridge.NativeBridgeProvider) provider));
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
     * Lazily creates a temporary directory for extracted mod jars. Kept
     * separate from the extension temp dir so mod and extension artifacts do
     * not collide (Alpha 4 production hardening).
     *
     * @return the mod temp directory path
     */
    private Path getModTempDir() {
        if (modTempDir == null) {
            try {
                modTempDir = Files.createTempDirectory("aprism-mods");
            } catch (IOException e) {
                throw new RuntimeException("Failed to create mod temp directory", e);
            }
        }
        return modTempDir;
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
        environment.put("neoforge", ModDiscoverer.NEOFORGE_LOADER_VERSION);
        environment.put("forge", ModDiscoverer.FORGE_LOADER_VERSION);
        environment.put("quilt_loader", ModDiscoverer.QUILT_LOADER_VERSION);
        environment.put("liteloader", ModDiscoverer.LITELOADER_LOADER_VERSION);
        environment.put("java", Integer.toString(Runtime.version().feature()));
        // OPEN-1 (closed in v26.0): the running Aprism Loader itself is an
        // environment provider, so mods may declare depends: {"aprism":
        // ">=26.0"}. Supply the normalized running version (v-prefix and
        // prerelease suffix stripped) so such ranges resolve here exactly as
        // they do in ExtensionLoader's aprismRange validation.
        if (aprismVersion != null && !aprismVersion.isBlank()) {
            environment.put("aprism", ExtensionLoader.normalizeAprismVersion(aprismVersion));
        }
        DependencyResolver resolver = new DependencyResolver();
        List<ModContainer> ordered = resolver.resolve(
                discovered.stream().map(ModDiscoverer.DiscoveredMod::manifest).toList(),
                environment);

        // Register to classloader in dependency order. A single mod that
        // fails to register is isolated (logged + reported) so one broken mod
        // cannot abort the whole boot.
        if (loadReport != null) {
            loadReport.beginPhase2();
        }
        mods.clear();
        for (ModContainer mc : ordered) {
            ModDiscoverer.DiscoveredMod dm = discoveredById.get(mc.getId());
            if (dm == null) {
                continue;
            }
            long t0 = System.nanoTime();
            try {
                LoadedModContainer container = new LoadedModContainer(dm.manifest(), dm.path(), dm.loaderKey());
                if (dm.format() == ModDiscoverer.ModFormat.AJE) {
                    // A .aje is a ZIP wrapper: the executable mod classes live in
                    // the embedded <modid>.jar (and optional lib/ jars). Extract
                    // them to a temp directory and add those to the classloader;
                    // a URLClassLoader cannot read classes from a nested archive.
                    extractModJars(dm, container);
                } else {
                    // Plain .jar / .litemod: the archive itself is the classpath entry
                    classLoader.addModJar(dm.path());
                    container.addExtractedJarPath(dm.path());
                }
                mods.put(container.getId(), container);
                // Register the mod's mixin configs (if any) with the Mixin environment
                registerMixins(dm.manifest());
                // Register the mod's access widener (if any) with the transformer
                registerAccessWidener(dm.manifest(), dm.path());
                if (loadReport != null) {
                    loadReport.recordOk("mod", container.getId(), container.getVersion(),
                            (System.nanoTime() - t0) / 1_000_000);
                }
            } catch (RuntimeException e) {
                long ms = (System.nanoTime() - t0) / 1_000_000;
                LOG.warning("Mod " + dm.manifest().id() + " failed to load; skipping it: "
                        + e.getMessage());
                if (loadReport != null) {
                    loadReport.recordFailure("mod", dm.manifest().id(), dm.manifest().version(),
                            ms, String.valueOf(e.getMessage()));
                }
            }
        }
        if (loadReport != null) {
            loadReport.endPhase2();
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
    private void extractModJars(ModDiscoverer.DiscoveredMod dm, LoadedModContainer container) {
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
                Path target = getModTempDir().resolve(modId + "_" + name);
                try (InputStream is = Files.newInputStream(jar)) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
                classLoader.addModJar(target);
                container.addExtractedJarPath(target);
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
        this.gameRoot = gameRoot;
        // v26.2-Alpha.6 hardening: structured lifecycle trace (backs the crash
        // report log tail).
        if (logging != null) {
            logging.getLogger("runtime").info("performLoad start: gameRoot=" + gameRoot);
        }
        // v26.2-Alpha.2 (goal #7): ensure a load report exists so failed
        // units are recorded even when callers invoke performLoad directly
        // (bootstrapProduction also creates one; keep the earlier instance).
        if (loadReport == null) {
            loadReport = new LoadReport();
        }
        attachLogFileSink(gameRoot);
        loadExtensions(extensionsDir);
        if ("BE".equalsIgnoreCase(mcEdit)) {
            loadBedrockMods(gameRoot);
        } else {
            loadMods(gameRoot);
        }
        // v26.2-Alpha.3 mod settings (goal #7 part 2): register every loaded
        // mod's declared settings and overlay any persisted user values from
        // <game-root>/config/aprism-settings/.
        if (!"BE".equalsIgnoreCase(mcEdit)) {
            settingsRegistry.bindStorage(gameRoot.resolve("config").resolve("aprism-settings"));
            for (LoadedModContainer mod : mods.values()) {
                settingsRegistry.register(mod.getManifest());
            }
        }
        // v26.2-Alpha.2 native mod list (goal #7): rebuild the queryable
        // registry from the loaded containers plus the load report, so the
        // future in-game mod menu sees every unit with its final state.
        rebuildModList();
        // v26.6-Alpha.2 MDL integration: publish the machine-readable status
        // file so external tooling (mdl diagnose, installer reports) can query
        // the loader state without parsing game logs.
        StatusPublisher.publish(gameRoot, StatusPublisher.buildSnapshot(
                aprismVersion, mcEdit, mcVersion, "LOADED", modListRegistry, loadReport));
        if (logging != null) {
            logging.getLogger("runtime").info("performLoad complete: "
                    + mods.size() + " mod(s), "
                    + extensionContainers.size() + " extension(s)");
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
        loadReport = new LoadReport();
        performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
        // v26.7-Alpha.2: deep foreign-loader mods (JEI-class) query vanilla
        // registries during construction. At premain time the registries are
        // not yet frozen, so when the bootstrap probe is available we defer
        // every entrypoint-dispatching stage until after vanilla bootstrap.
        // Without the probe (unit tests, embedders, non-MC hosts) behaviour
        // is identical to previous releases: synchronous dispatch.
        if (GameBootstrapGate.shouldDefer()) {
            LOG.info("Vanilla bootstrap pending - deferring mod lifecycle "
                    + "dispatch until registries are frozen");
            GameBootstrapGate.onBootstrapped(() -> {
                try {
                    dispatchProductionStages(gameRoot, side);
                } catch (DependencyResolutionException deferredFailure) {
                    LOG.warning("Deferred lifecycle dispatch failed: " + deferredFailure);
                }
            });
            return;
        }
        dispatchProductionStages(gameRoot, side);
    }

    /**
     * The post-load stages of production bootstrap: common lifecycle,
     * content binding, optional side phase, and the load report. Split out
     * from {@link #bootstrapProduction(Path, String)} so the same sequence
     * can run either synchronously or deferred post-bootstrap.
     *
     * @param gameRoot the game instance root
     * @param side     the distribution side ({@code client}, {@code server}, or {@code null})
     */
    private void dispatchProductionStages(Path gameRoot, String side)
            throws DependencyResolutionException {
        invokeCommonLifecycle();
        // v26.7-Alpha.1: bind Aprism-native content into the real MC
        // registries. This stage runs AFTER vanilla bootstrap (the
        // GameBootstrapGate defers it), so registries are initialized and
        // still writable - direct bind, fail-closed per entry.
        if (!"BE".equalsIgnoreCase(mcEdit)) {
            try {
                ContentBindingRunner.bindNow(gameRegistries, mcProfile == McProfile.REMAPPED);
            } catch (Throwable t) {
                LOG.warning("Content binding failed: " + t);
            }
        }
        // v26.7-Alpha.2: attempt command binding into the live Brigadier
        // dispatcher. The live instance is per-world (not statically
        // reachable yet), so this fails closed with NO_DISPATCHER until the
        // discovery seam lands; registration state stays queryable.
        try {
            BrigadierCommandBinder cmdBinder =
                    new BrigadierCommandBinder(commandRegistration);
            cmdBinder.setRemapProfile(mcProfile == McProfile.REMAPPED);
            cmdBinder.bindAll();
        } catch (Throwable t) {
            LOG.warning("Command binding failed: " + t);
        }
        AprismPhase sidePhase = sidePhaseFor(side);
        if (sidePhase != null) {
            LOG.info("Dispatching side phase: " + sidePhase);
            invokeEntrypoints(sidePhase);
        }
        // Emit the startup load report (timing + per-unit outcomes) so
        // launcher users can see exactly what loaded and what failed.
        LOG.info("\n" + loadReport.toSummary(aprismVersion));
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
        coordinateBedrockInjection(gameRoot);
    }

    /**
     * Runs the fail-closed Bedrock injection coordination (FACT.md 9.8) against
     * the mods discovered by {@link #loadBedrockMods}. The result is logged: a
     * feasible plan means native injection may proceed (the platform injector
     * consumes {@link BedrockInjectionCoordinator.CoordinationResult#plan()});
     * any refusal is reported and injection is withheld. This is the pure-Java
     * planning step only; actual process attachment is the native injector's job.
     *
     * @param gameRoot the BE game root (typically {@code com.mojang/})
     */
    private void coordinateBedrockInjection(Path gameRoot) {
        BedrockInjectionCoordinator coordinator = new BedrockInjectionCoordinator();
        BedrockInjectionCoordinator.CoordinationResult result =
                coordinator.coordinateForGameRoot(gameRoot, mcVersion, List.copyOf(bedrockMods.values()));
        if (result.isFeasible()) {
            LOG.info("BE injection plan ready: " + result.plan().actions().size()
                    + " native action(s) for BE " + result.plan().beVersion());
        } else {
            LOG.warning("BE injection withheld (fail-closed): " + result.refusalReason());
        }
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
        if (phase == AprismPhase.INIT) {
            interModComms.markInitPhaseReached();
            commandRegistration.openWindow();
            keyBindingRegistry.openWindow();
            resourceReloadRegistry.openWindow();
        }
        if (phase == AprismPhase.COMPLETE) {
            commandRegistration.freezeWindow();
            keyBindingRegistry.freezeWindow();
            resourceReloadRegistry.freezeWindow();
        }
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
        // SPI seam: a registered LoaderEntrypointHandler (supplied by a
        // loader-support .aep, e.g. from AprismRefract) takes ownership of
        // dispatch for its loader key. This is the extraction point that lets
        // foreign-loader bridges live outside aprism-loader-core.
        LoaderEntrypointHandler handler =
                LoaderEntrypointRegistry.get(container.getLoaderKey());
        if (handler != null) {
            handler.invoke(container, phase);
            if (handler.isExclusive()) {
                return;
            }
        }
        // v26.2-Alpha.5 (goal #4 close): foreign-loader dispatch is SPI-only.
        // The transitional built-in bridges were removed from aprism-loader-core;
        // loader-support extensions from AprismRefract own dispatch for their
        // loader keys. A foreign mod with no registered handler is discovered
        // but never entrypoint-dispatched here.
        if (isForeignLoaderKey(container.getLoaderKey())) {
            return;
        }
        AprismManifest manifest = container.getManifest();
        List<String> entrypoints = manifest.entrypoints() == null
                ? List.of()
                : manifest.entrypoints().getOrDefault(entrypointKey, List.of());
        // v26.5-Alpha.1: annotation-scan fallback. When the manifest has no
        // entrypoints for the "main" key, scan the mod's extracted jars for
        // classes annotated with @AprismMod and use those as the entrypoint
        // list. This closes QA0 gap #5 (annotation-scan entrypoint discovery).
        if (entrypoints.isEmpty() && "main".equals(entrypointKey)) {
            entrypoints = AnnotationScanner.scanModEntrypoints(
                    container.getExtractedJarPaths(), container.getId());
            if (!entrypoints.isEmpty()) {
                LOG.fine("Annotation scan found " + entrypoints.size()
                        + " @AprismMod entrypoint(s) for mod " + container.getId()
                        + " (manifest has no explicit entrypoints)");
            }
        }
        if (entrypoints.isEmpty()) {
            return;
        }
        AprismContext context = new AprismContextImpl(container, eventBus, registry, interModComms);
        for (String className : entrypoints) {
            try {
                Class<?> clazz = classLoader.loadClass(className);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (instance instanceof IAprismMod mod) {
                    // Aprism-native mod: full lifecycle dispatch
                    invokePhaseMethod(mod, context, phase);
                }
                // Retain the first instantiated instance on the container
                if (container.getInstance() == null) {
                    container.setInstance(instance);
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke entrypoint " + className
                        + " for mod " + container.getId() + " in phase " + phase, e);
            } catch (RuntimeException e) {
                // A mod throwing during its own entrypoint is isolated so it
                // cannot abort the lifecycle of the remaining mods.
                LOG.warning("Mod " + container.getId() + " threw in phase " + phase
                        + "; skipping its remaining entrypoints: " + e);
                break;
            }
        }
    }

    /**
     * Whether a loader key belongs to a foreign loader (Fabric, Quilt, Forge,
     * NeoForge, or LiteLoader). Foreign dispatch is owned exclusively by the
     * {@link LoaderEntrypointHandler} SPI since v26.2-Alpha.5 (goal #4 close).
     *
     * @param loaderKey the loader key
     * @return true when the key is a foreign loader key
     */
    private static boolean isForeignLoaderKey(String loaderKey) {
        return ModDiscoverer.FABRIC_KEY.equals(loaderKey)
                || ModDiscoverer.QUILT_KEY.equals(loaderKey)
                || ModDiscoverer.FORGE_KEY.equals(loaderKey)
                || ModDiscoverer.NEOFORGE_KEY.equals(loaderKey)
                || ModDiscoverer.LITELOADER_KEY.equals(loaderKey);
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
        // v26.1-Alpha.9 (goal #3): shutdown hook for extensions
        for (LoadedExtensionContainer container : extensionContainers.values()) {
            Object instance = container.getInstance();
            if (instance instanceof IAprismExtension extension) {
                try {
                    ExtensionContext context = new ExtensionContextImpl(
                            container, eventBus, registry,
                            (loaderKey, folder) -> extensionLoader.addLoaderFolder(loaderKey, folder),
                            assistant -> aiRegistry.register((com.aprism.api.ai.AiAssistant) assistant),
                            provider -> renderingRegistry.register((com.aprism.api.rendering.RenderingProvider) provider),
                            provider -> nativeBridgeRegistry.register((com.aprism.api.nativebridge.NativeBridgeProvider) provider));
                    extension.onShutdown(context);
                } catch (RuntimeException e) {
                    LOG.warning("Extension " + container.getExtensionId()
                            + " failed in onShutdown: " + e.getMessage());
                }
            }
        }
        // Runs BEFORE the shared objects are nulled so the context handed to
        // onShutdown still exposes a live event bus, registry and registrar.
        // v26.6-Alpha.2: refresh the status file to SHUTDOWN while the state
        // is still queryable, so external tools never see a stale LOADED doc.
        StatusPublisher.publish(gameRoot, StatusPublisher.buildSnapshot(
                aprismVersion, mcEdit, mcVersion, "SHUTDOWN", modListRegistry, loadReport));
        // v26.2-Alpha.1: flush and close the logging facility (sinks keep the
        // retained ring buffer for post-shutdown inspection).
        if (logging != null) {
            logging.close();
            logging = null;
        }
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
        versionLineEntry = null;
        bytecodeRemapper = null;
        gameRoot = null;
        mods.clear();
        bedrockMods.clear();
        loadedExtensions.clear();
        extensionContainers.clear();
        modListRegistry.clear();
        // v26.2-Alpha.3: flush dirty mod settings before dropping them.
        settingsRegistry.persistAll();
        settingsRegistry.clear();
        // v26.3-Alpha.1: detach and reset the game-event dispatcher.
        // v26.5-Alpha.3: uninstall game-event method hooks first.
        if (gameEventHookInstaller != null) {
            gameEventHookInstaller.uninstallAll();
            gameEventHookInstaller = null;
        }
        if (gameEventDispatcher != null) {
            gameEventDispatcher.reset();
            gameEventDispatcher = null;
        }
        // v26.3-Alpha.2: clear the typed game-content registries.
        gameRegistries.clear();
        // v26.5-Alpha.8: detach network transport installer.
        networkTransportInstaller = null;
        // v26.3-Alpha.3: clear the networking registry (channels, listeners,
        // transport).
        networkingRegistry.clear();
        // v26.3-Alpha.4: clear the AI assistant registry.
        aiRegistry.clear();
        interModComms.clear();
        nativeBridgeRegistry.clear();
        // v26.4-Alpha.6: drop the AprismateAgent descriptor.
        aprismateAgent = null;
        // v26.4-Alpha.7: reset the hardware registry to the default probe.
        hardwareRegistry.clear();
        // v26.4-Alpha.8: clear the cross-language bindings.
        if (crossLanguageRuntime != null) {
            crossLanguageRuntime.clear();
            crossLanguageRuntime = null;
        }
        if (transformer != null) {
            transformer.getClassLoadObservers().clear();
        }
        // v26.5-Alpha.4: unbind commands first.
        if (commandBindingInstaller != null) {
            commandBindingInstaller.unbindAll();
            commandBindingInstaller = null;
        }
        commandRegistration.clear();
        // v26.5-Alpha.5: unbind key bindings first.
        if (keyBindingBindingInstaller != null) {
            keyBindingBindingInstaller.unbindAll();
            keyBindingBindingInstaller = null;
        }
        keyBindingRegistry.clear();
        // v26.5-Alpha.6: detach tick scheduler driver first.
        if (tickSchedulerDriver != null) {
            tickSchedulerDriver.detach();
            tickSchedulerDriver = null;
        }
        tickScheduler.clear();
        // v26.5-Alpha.7: uninstall resource-reload triggers first.
        if (resourceReloadTrigger != null) {
            resourceReloadTrigger.uninstallAll();
            resourceReloadTrigger = null;
        }
        resourceReloadRegistry.clear();
        // v26.3-Alpha.5: clear the rendering provider registry.
        renderingRegistry.clear();
        loadReport = null;
        cleanupExtensionTempDir();
        cleanupModTempDir();
        AprismMixinBootstrap.reset();
    }

    /**
     * Deletes the temporary directory used for extracted mod jars.
     */
    private void cleanupModTempDir() {
        if (modTempDir == null) {
            return;
        }
        try (var stream = Files.walk(modTempDir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> {
                      try {
                          Files.deleteIfExists(p);
                      } catch (IOException ignored) {
                          // best-effort cleanup
                      }
                  });
        } catch (IOException e) {
            LOG.warning("Failed to clean up mod temp directory: " + e.getMessage());
        }
        modTempDir = null;
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
