package com.aprism.loader;

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
            return transformer.transformClassBytes(className, className, classBytes);
        } catch (Throwable t) {
            LOG.warning("Mixin transformation failed for " + className + ": " + t.getMessage());
            return classBytes;
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
