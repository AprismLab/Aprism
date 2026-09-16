package com.aprism.loader;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/**
 * Bootstraps the SpongePowered Mixin environment and holds the active
 * {@link IMixinTransformer} reference for the {@link AprismClassTransformer}
 * to delegate to.
 *
 * <p>Bootstrap sequence (invoked from {@link AprismRuntime#initialize}):
 * <ol>
 *   <li>Bind the {@link AprismClassLoader} to the static holder so that the
 *       {@link AprismMixinService} (instantiated by the ServiceLoader) can
 *       access it.</li>
 *   <li>Call {@link MixinBootstrap#init} to register the Mixin version (which
 *       the {@link MixinEnvironment} constructor validates), trigger
 *       ServiceLoader discovery of {@link AprismMixinService}, and transition
 *       the environment to the PREINIT phase.</li>
 *   <li>Acquire and cache the {@link IMixinTransformer} for later delegation
 *       via {@link #transformClassBytes}.</li>
 * </ol>
 *
 * <p>The Mixin environment is a JVM-level singleton, so {@link #bootstrap} is
 * idempotent: on repeated invocations it updates the classloader reference and
 * re-acquires the transformer but does not re-initialize the environment.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismMixinBootstrap {

    private static final Logger LOG = Logger.getLogger(AprismMixinBootstrap.class.getName());

    private static AprismClassLoader classLoader;
    private static IMixinTransformer transformer;
    private static boolean environmentInitialized;
    private static final Set<String> transformerExclusions = new HashSet<>();
    private static final Set<String> offeredConfigs = new HashSet<>();

    private AprismMixinBootstrap() {
    }

    /**
     * Bootstraps the Mixin environment with the given classloader. Safe to
     * call multiple times; only the first call initializes the environment,
     * subsequent calls just refresh the classloader binding and transformer
     * reference.
     *
     * @param cl the Aprism classloader to bind to the mixin service
     */
    private static boolean isFabricHost() {
        for (String k : new String[] {
                "net.fabricmc.loader.impl.launch.knot.Knot",
                "net.fabricmc.loader.api.FabricLoader"}) {
            try {
                Class.forName(k, false, AprismMixinBootstrap.class.getClassLoader());
                return true;
            } catch (Throwable ignored) {
                // try next marker
            }
        }
        return false;
    }
    static void bootstrap(AprismClassLoader cl) {
        classLoader = cl;
        // v26.7-Alpha.5: under a Fabric/Knot host, the host loader owns the
        // Mixin environment (and ASM). Bootstrapping our own service there
        // fails safe today but is pointless; defer to the host instead.
        if (!environmentInitialized && isFabricHost()) {
            LOG.info("Fabric host detected - deferring Mixin environment "
                    + "to the host loader");
            environmentInitialized = true; // mark handled; no Aprism service
            return;
        }
        if (!environmentInitialized) {
            try {
                // MixinBootstrap.init() registers the Mixin version (which
                // MixinEnvironment's constructor checks) and transitions the
                // environment to the PREINIT phase. Calling MixinEnvironment.init()
                // alone is insufficient: without MixinBootstrap.init(), the
                // version check in the MixinEnvironment constructor fails with
                // "Environment conflict, mismatched versions or you didn't call
                // MixinBootstrap.init()".
                MixinBootstrap.init();
                environmentInitialized = true;
                LOG.info("SpongePowered Mixin environment initialized (service: Aprism)");
                // The active transformer MUST be registered before mods weave:
                // MixinInternals.getExtensions() dereferences
                // MixinEnvironment.getActiveTransformer() to register Mixin's
                // transformer extensions.
                acquireTransformer();
                // NOTE (v26.9-Alpha.7): MixinExtras is deliberately NOT
                // initialized here. It bootstraps itself at weave time
                // ("Initializing MixinExtras via MixinExtrasServiceImpl(version=...)")
                // through its own service/extension hooks. Forcing it early from
                // premain produced a SECOND initialization later, and
                // MixinExtrasServiceImpl.takeControlFrom() de-initializes the
                // earlier service - which dropped the injection-point specifier
                // registration. Every @ModifyExpressionValue/@WrapOperation mixin
                // then failed with "MIXINEXTRAS:EXPRESSION is not a valid
                // injection point specifier" (Lithium weave failures, world load
                // crash). Let MixinExtras own its own lifecycle, as under Fabric.
                fixUndetectedSide();
                alignCompatibilityLevel();
            } catch (Throwable t) {
                LOG.log(java.util.logging.Level.SEVERE, "Failed to initialize Mixin environment", t);
            }
        }
        acquireTransformer();
    }

    /**
     * Aligns the Mixin environment's compatibility level with the running JVM.
     *
     * <p>Why this is necessary: a mod's mixin config that omits
     * {@code compatibilityLevel} defaults to the <em>environment's</em> current
     * level. If that default level fails Mixin's support check for the active
     * JRE/ASM combination, loading the config throws
     * "The requested compatibility level ... could not be set". By raising the
     * environment level up-front to the highest level the running JVM supports,
     * mod configs load without triggering that check (a config whose level equals
     * the environment level short-circuits). This is what makes third-party mixin
     * configs weave on modern JVMs (e.g. Java 25 for Minecraft 26.x).
     *
     * <p>We try levels from highest to lowest and set the first one Mixin
     * accepts, so a restrictive ASM/JRE combination degrades gracefully instead
     * of aborting all mixins.
     */
    private static void alignCompatibilityLevel() {
        MixinEnvironment env = MixinEnvironment.getDefaultEnvironment();
        MixinEnvironment.CompatibilityLevel current = env.getCompatibilityLevel();
        MixinEnvironment.CompatibilityLevel[] levels = MixinEnvironment.CompatibilityLevel.values();
        // Walk from the highest level down; attempt to set each and adopt the
        // first one the environment accepts. On a modern JRE the highest levels
        // are accepted (e.g. JAVA_25 on Java 25); on an older JRE the high ones
        // throw and we degrade gracefully to the highest supported level.
        for (int i = levels.length - 1; i >= 0; i--) {
            MixinEnvironment.CompatibilityLevel candidate = levels[i];
            try {
                MixinEnvironment.setCompatibilityLevel(candidate);
                LOG.info("Mixin compatibility level aligned to " + candidate
                        + " for JRE " + Runtime.version().feature());
                return;
            } catch (Throwable ignored) {
                // This level is not supported by the active JRE/ASM; try a lower one.
            }
        }
        LOG.info("Mixin compatibility level left at " + current
                + " (no higher level accepted by JRE " + Runtime.version().feature() + ")");
    }

    /**
     * Initializes MixinExtras when it is present on the classpath.
     *
     * <p><b>Called lazily via {@link #initializeMixinExtrasOnce()} - never from
     * {@link #bootstrap}.</b> Timing is load-bearing: initializing at premain
     * caused a second initialization at weave time, and
     * {@code MixinExtrasServiceImpl.takeControlFrom()} then de-initialized the
     * earlier service, dropping the injection-point specifier registration so
     * every {@code @ModifyExpressionValue} / {@code @WrapOperation} mixin failed
     * with "MIXINEXTRAS:EXPRESSION is not a valid injection point specifier".
     * Initializing when the first mod config is offered keeps it to a single
     * initialization, with the active transformer already registered.
     *
     * <p>Why MixinExtras matters at all: Fabric Loader bundles it upstream and
     * modern Fabric mods (Lithium, and most others) compile their mixin classes
     * against {@code com.llamalad7.mixinextras.*} injectors and sugars. When the
     * classes are absent, Mixin fails to LOAD the supporting classes during
     * weave and every mixin that uses MixinExtras features is silently skipped
     * ("Mixin apply ... failed"). Partial application then breaks internal
     * invariants inside the mod itself (observed with Lithium 0.25.3 on MC 26.2:
     * the dragon-portal pattern replacement mixin was skipped while a plain cast
     * mixin applied, producing a ClassCastException at world load). Shipping the
     * library in the agent jar is part of the fix; this call activates it.
     */
    private static void initializeMixinExtras() {
        try {
            // MixinExtras 0.4.x entry point is MixinExtrasBootstrap.init();
            // older releases used initialize(). Try both so a future upgrade
            // of the bundled library does not silently disable this path.
            Class<?> bootstrap = Class.forName(
                    "com.llamalad7.mixinextras.MixinExtrasBootstrap");
            java.lang.reflect.Method init;
            try {
                init = bootstrap.getMethod("init");
            } catch (NoSuchMethodException e) {
                init = bootstrap.getMethod("initialize");
            }
            init.invoke(null);
            LOG.info("MixinExtras bootstrap initialized");
        } catch (ClassNotFoundException e) {
            LOG.info("MixinExtras not present on the classpath - skipping bootstrap");
        } catch (Throwable t) {
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException ite
                    && ite.getCause() != null) ? ite.getCause() : t;
            LOG.log(java.util.logging.Level.WARNING,
                    "MixinExtras bootstrap failed", cause);
        }
    }

    /**
     * Mixin's launch-profile based side detection fails for plain javaagent
     * launches (the environment stays UNKNOWN and every sided mixin
     * configuration is skipped with "unable to detect the current side").
     *
     * <p>If the side is still undetected after bootstrap, infer it from the
     * game marker classes ({@code net.minecraft.client.main.Main} present
     * means the JE client), or take an explicit override via
     * {@code -Daprism.side=client|server}. Undetectable environments are left
     * untouched so embedders keep their current behaviour.
     */
    private static void fixUndetectedSide() {
        try {
            MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
            if (env.getSide() != MixinEnvironment.Side.UNKNOWN) {
                return;
            }
            MixinEnvironment.Side side = inferSide();
            if (side == null) {
                LOG.warning("Mixin side undetectable and no evidence found - "
                        + "sided mixin configurations will be skipped (override "
                        + "with -Daprism.side=client|server)");
                return;
            }
            env.setSide(side);
            LOG.info("Mixin side set to " + side
                    + " (launch-profile detection failed)");
        } catch (Throwable t) {
            LOG.warning("Failed to set Mixin side: " + t);
        }
    }

    private static MixinEnvironment.Side inferSide() {
        String prop = System.getProperty("aprism.side", "");
        if ("server".equalsIgnoreCase(prop)) {
            return MixinEnvironment.Side.SERVER;
        }
        if ("client".equalsIgnoreCase(prop)) {
            return MixinEnvironment.Side.CLIENT;
        }
        try {
            Class.forName("net.minecraft.client.main.Main", false,
                    AprismMixinBootstrap.class.getClassLoader());
            return MixinEnvironment.Side.CLIENT;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Acquires the active {@link IMixinTransformer} from the default
     * environment. If no transformer has been registered (the environment
     * does not auto-create one), instantiates the package-private
     * {@code MixinTransformer} implementation via reflection and registers it
     * as the active transformer.
     */
    private static void acquireTransformer() {
        try {
            Object active = MixinEnvironment.getDefaultEnvironment().getActiveTransformer();
            if (active instanceof IMixinTransformer mt) {
                transformer = mt;
                return;
            }
            // No active transformer; instantiate MixinTransformer (package-private)
            // via reflection and register it as the active transformer.
            Class<?> mtClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
            java.lang.reflect.Constructor<?> ctor = mtClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            IMixinTransformer mt = (IMixinTransformer) ctor.newInstance();
            MixinEnvironment.getDefaultEnvironment().setActiveTransformer(mt);
            transformer = mt;
            LOG.info("Aprism Mixin transformer instantiated and registered as active");
        } catch (Throwable t) {
            LOG.log(java.util.logging.Level.SEVERE, "Failed to acquire Mixin transformer", t);
        }
    }

    /**
     * @return the bound classloader, or {@code null} if not yet bootstrapped
     */
    static AprismClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * @return whether the Mixin environment has been initialized and a
     *         transformer is available for delegation
     */
    public static boolean isAvailable() {
        return transformer != null;
    }

    /**
     * Delegates a class transformation to the Mixin transformer. Returns the
     * original bytes unchanged if Mixin is not available or the transformer
     * declines to transform the class.
     *
     * @param className   the binary class name (dotted)
     * @param classBytes  the original class bytecode
     * @return the transformed bytecode, or the original if no transformation applied
     */
    static byte[] transformClassBytes(String className, byte[] classBytes) {
        if (transformer == null) {
            return classBytes;
        }
        if (isExcluded(className)) {
            return classBytes;
        }
        try {
            // IMixinTransformer.transformClassBytes(name, transformedName, bytes) feeds
            // its SECOND argument into transformClass. Mixin indexes configs and
            // mixins by DOTTED class names, so transformedName must be dotted;
            // passing a slashed (internal) name makes hasMixinsFor fail and no
            // mixin is ever applied (the real-game weave bug).
            byte[] result = transformer.transformClassBytes(className, className, classBytes);
            // v26.9-Alpha.7: materialize mixin-generated synthetic classes into
            // the system classloader. A mixin carrying anonymous inner classes
            // (e.g. Lithium's entity.brain mixins) makes the weave register
            // generated Brain$Anonymous$<hash> classes in the transformer's
            // SyntheticClassRegistry; vanilla code references them immediately
            // after. Under the sharedClassSpace=system topology vanilla is
            // defined by the built-in loader, which cannot consult Mixin for
            // classes that exist in no jar - so the reference would CNFE.
            // Mixin supports generation on demand:
            // transformClassBytes(name, name, null) invokes generateClass for
            // registered synthetic names - so generate the bytes now and
            // define them directly into the system loader.
            definePendingSyntheticClasses(className);
            return result;
        } catch (Throwable t) {
            LOG.warning("Mixin transformation failed for " + className + ": " + t.getMessage());
            return classBytes;
        }
    }

    /**
     * Generates and defines the mixin-generated synthetic classes registered
     * during the weave of {@code wovenClass} (e.g. {@code Brain$Anonymous$*}).
     *
     * <p>Option B of the synthetic-class fix design (FACT 2026-09-12c): keep
     * the sharedClassSpace topology, close the gap by defining the generated
     * classes directly into the system classloader right after the weave that
     * produced them - strictly before vanilla code (e.g. Allay clinit) can
     * reference them. A failure here is non-fatal per class: the affected
     * reference then fails the same way it would without this hook, and the
     * game continues.
     */
    private static void definePendingSyntheticClasses(String wovenClass) {
        try {
            Object registry = readSyntheticClassRegistry();
            if (registry == null) {
                return;
            }
            java.util.Map<?, ?> classes = readRegistryMap(registry);
            if (classes == null) {
                return;
            }
            // Collect the pending synthetic classes generated for this weave.
            java.util.Map<String, byte[]> generated = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> e : classes.entrySet()) {
                Object info = e.getValue();
                String name = syntheticClassName(info);
                if (name == null || !name.startsWith(wovenClass + "$")) {
                    continue;
                }
                if (syntheticClassLoaded(info)) {
                    continue;
                }
                try {
                    // Mixin's supported on-demand path: bytes == null means
                    // "generate this registered synthetic class".
                    byte[] bytes = transformer.transformClassBytes(name, name, null);
                    if (bytes != null && bytes.length > 0) {
                        generated.put(name, bytes);
                    }
                } catch (Throwable perClass) {
                    LOG.warning("Failed to generate synthetic class " + name
                            + ": " + perClass);
                }
            }
            if (generated.isEmpty()) {
                return;
            }
            appendSyntheticJar(generated);
        } catch (Throwable t) {
            LOG.warning("Synthetic class materialization failed for " + wovenClass
                    + ": " + t);
        }
    }

    /**
     * Publishes generated synthetic classes to the system classloader through
     * the instrumentation search path.
     *
     * <p>Why a jar and not {@code ClassLoader.defineClass}: reflective
     * defineClass on the system loader is blocked by JPMS for many packages
     * (InaccessibleObjectException: "module java.base does not open
     * java.lang"), which is exactly what the first implementation hit.
     * {@link java.lang.instrument.Instrumentation#appendToSystemClassLoaderSearch}
     * is the supported, module-safe route.
     *
     * <p>Why a FRESH jar path per batch: a jar's entries are indexed when its
     * loader is first consulted, so appending more entries to an
     * already-indexed jar is invisible. A new file name each time guarantees
     * a new loader with a complete index.
     */
    private static void appendSyntheticJar(java.util.Map<String, byte[]> classes) {
        if (instrumentation == null) {
            LOG.warning("No Instrumentation handle; cannot publish "
                    + classes.size() + " generated synthetic class(es)");
            return;
        }
        try {
            Path dir = java.nio.file.Files.createTempDirectory("aprism-synthetic");
            dir.toFile().deleteOnExit();
            Path jarPath = dir.resolve("synthetic-" + syntheticJarSequence++ + ".jar");
            try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(
                    java.nio.file.Files.newOutputStream(jarPath))) {
                for (java.util.Map.Entry<String, byte[]> e : classes.entrySet()) {
                    String entryName = e.getKey().replace('.', '/') + ".class";
                    out.putNextEntry(new java.util.jar.JarEntry(entryName));
                    out.write(e.getValue());
                    out.closeEntry();
                }
            }
            instrumentation.appendToSystemClassLoaderSearch(
                    new java.util.jar.JarFile(jarPath.toFile()));
            LOG.fine("Published " + classes.size()
                    + " mixin-generated synthetic class(es) via " + jarPath.getFileName());
        } catch (Throwable t) {
            LOG.warning("Failed to publish generated synthetic classes: " + t);
        }
    }

    /** Monotonic suffix so every synthetic jar path is unique. */
    private static int syntheticJarSequence;

    /** Instrumentation handle for publishing generated classes (may be null). */
    private static volatile java.lang.instrument.Instrumentation instrumentation;

    /** Binds the instrumentation handle used to publish synthetic classes. */
    static void setInstrumentation(java.lang.instrument.Instrumentation inst) {
        instrumentation = inst;
    }

    /** Reads MixinTransformer.syntheticClassRegistry via reflection. */
    private static Object readSyntheticClassRegistry() throws Exception {
        java.lang.reflect.Field f = transformer.getClass().getDeclaredField(
                "syntheticClassRegistry");
        f.setAccessible(true);
        return f.get(transformer);
    }

    /** Reads SyntheticClassRegistry.classes via reflection. */
    private static java.util.Map<?, ?> readRegistryMap(Object registry) throws Exception {
        java.lang.reflect.Field f = registry.getClass().getDeclaredField("classes");
        f.setAccessible(true);
        Object map = f.get(registry);
        return map instanceof java.util.Map<?, ?> m ? m : null;
    }

    private static String syntheticClassName(Object info) {
        try {
            return (String) info.getClass().getMethod("getClassName").invoke(info);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean syntheticClassLoaded(Object info) {
        try {
            return (Boolean) info.getClass().getMethod("isLoaded").invoke(info);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Registers a mixin configuration with the Mixin environment. Called by
     * {@link AprismRuntime} for each entry in a mod manifest's {@code mixins}
     * list. The config resource (e.g. {@code modid.mixins.json}) must be
     * resolvable from the classloader.
     *
     * @param configName the mixin config resource path (e.g. "mymod.mixins.json")
     */
    public static void offerMixinConfig(String configName) {
        if (configName == null || configName.isBlank()) {
            return;
        }
        if (offeredConfigs.contains(configName)) {
            return;
        }
        // MixinExtras must exist before ANY mod config is woven, because mods
        // compile against its injectors/sugars. Initialize it here - once, at
        // the moment mods start registering - rather than at premain: doing it
        // at premain caused a second initialization later and
        // takeControlFrom() de-initialized the first, dropping the
        // injection-point specifier registration (MIXINEXTRAS:EXPRESSION
        // unresolvable -> every MixinExtras mixin failed to apply).
        initializeMixinExtrasOnce();
        try {
            Mixins.addConfiguration(configName);
            offeredConfigs.add(configName);
            LOG.info("Registered Mixin config: " + configName);
        } catch (Throwable t) {
            // Log the full cause chain so config registration failures are
            // transparent (a bare message hides the real root cause).
            LOG.log(java.util.logging.Level.WARNING,
                    "Failed to register Mixin config " + configName, t);
        }
    }

    /** Guards {@link #initializeMixinExtras()} to a single call per JVM. */
    private static boolean mixinExtrasInitialized;

    /**
     * Initializes MixinExtras exactly once, lazily, at the point mods begin
     * registering their mixin configs.
     *
     * <p>The active transformer is guaranteed to exist by then
     * ({@link #acquireTransformer()} ran during bootstrap), which
     * {@code MixinInternals.registerExtension} requires when MixinExtras
     * installs its {@code MixinTransformerExtension}.
     */
    private static synchronized void initializeMixinExtrasOnce() {
        if (mixinExtrasInitialized) {
            return;
        }
        mixinExtrasInitialized = true;
        initializeMixinExtras();
    }

    /**
     * Registers a transformer exclusion. Classes whose names start with the
     * given prefix are skipped by the Mixin transformer delegation.
     *
     * @param prefix the class name prefix to exclude
     */
    static void addTransformerExclusion(String prefix) {
        if (prefix != null && !prefix.isBlank()) {
            transformerExclusions.add(prefix);
        }
    }

    /**
     * @param className the binary class name
     * @return whether the class matches a registered transformer exclusion
     */
    private static boolean isExcluded(String className) {
        for (String prefix : transformerExclusions) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resets the bootstrap state. Called by {@link AprismRuntime#shutdown} so
     * that the next bootstrap re-binds cleanly. Does NOT un-initialize the
     * Mixin environment (which is a JVM singleton and cannot be reset).
     */
    static void reset() {
        classLoader = null;
        transformer = null;
        offeredConfigs.clear();
        transformerExclusions.clear();
    }

    /**
     * @return whether the Mixin environment has been initialized in this JVM
     */
    public static boolean isEnvironmentInitialized() {
        return environmentInitialized;
    }
}
