package com.aprism.loader.livectx;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.aprism.loader.contentbind.OfficialMappings;
import com.aprism.loader.lowlevel.MethodHookRegistry;

/**
 * Live-game conformance harness (v26.9 roadmap Alpha.8).
 *
 * <p>Delivers the live half of the harness - world join, screen/widget tree,
 * and startup metrics - <em>from inside the agent</em>, so the checks need no
 * external control channel. Runtime hooks are registered with mapping-aware
 * targets: on obfuscated profiles both the owner class and every type inside
 * the descriptor are translated through the Mojang mappings before the hook is
 * installed.
 *
 * <p>Fail-open by design: every capture step is individually guarded and a
 * failure is recorded as a diagnostic, never thrown into the game. The harness
 * only activates when the agent is started with {@code harness=true}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LiveHarness {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG = Logger.getLogger("aprism.harness");

    /** Official vanilla hook targets (translated before installation). */
    private static final String CLIENT_CLASS = "net.minecraft.client.Minecraft";
    private static final String SET_LEVEL = "setLevel";
    private static final String SET_LEVEL_DESC =
            "(Lnet/minecraft/client/multiplayer/ClientLevel;)V";
    private static final String SET_SCREEN = "setScreen";
    private static final String SET_SCREEN_DESC =
            "(Lnet/minecraft/client/gui/screens/Screen;)V";

    /**
     * The world-join signal that modern Minecraft actually invokes
     * (v26.9-Alpha.8).
     *
     * <p>{@code Minecraft.setLevel} exists but 1.21.4 does not route a join
     * through it (verified with javap on the genuine client: the level lives in
     * a public field and is assigned directly). {@code ClientPacketListener.
     * handleLogin} IS invoked exactly once per world join, so it is the
     * reliable, class-loading-safe signal: it is a method hook, so no
     * background thread ever has to touch game classes.
     */
    private static final String PACKET_LISTENER_CLASS =
            "net.minecraft.client.multiplayer.ClientPacketListener";
    private static final String HANDLE_LOGIN = "handleLogin";
    private static final String HANDLE_LOGIN_DESC =
            "(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V";

    private final LiveContextTracker tracker;
    private final Path outputDir;
    private final long premainNanos = System.nanoTime();
    private final AtomicLong bootstrapNanos = new AtomicLong(-1);
    private final AtomicLong worldJoinNanos = new AtomicLong(-1);
    private final List<String> diagnostics = new ArrayList<>();
    private final List<String> screens = new ArrayList<>();
    private volatile String worldDetail = "not joined";
    /** One installed hook, kept so shutdown removes the exact registration. */
    private record InstalledHook(String owner, String method, String descriptor,
            Runnable callback) {
    }

    private final List<InstalledHook> installedHooks = new ArrayList<>();
    /** The translated client class name, or null when not yet resolved. */
    private volatile String resolvedClientClass;

    /**
     * @param tracker the live context tracker to report transitions into
     * @param outputDir the game directory the harness report is written to
     */
    public LiveHarness(LiveContextTracker tracker, Path outputDir) {
        this.tracker = tracker;
        this.outputDir = outputDir;
    }

    /**
     * Marks the moment vanilla bootstrap was observed (called by the
     * bootstrap gate path when deferral completes).
     */
    public void markBootstrap() {
        bootstrapNanos.compareAndSet(-1, System.nanoTime());
    }

    /** Resolved (translated) runtime names, kept for diagnostics context. */
    private volatile String resolvedLevelType;
    private volatile String resolvedScreenType;
    /** Probe-hook invocation counter (join-observation investigation). */
    private final java.util.concurrent.atomic.AtomicLong tickProbeCount =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Installs the live hooks with no mapping translation (NO_REMAP profile,
     * where the runtime uses the official names directly).
     */
    public void installWithNoMappings() {
        install(null);
    }

    /**
     * Installs the live hooks. Unresolvable targets degrade to diagnostics so
     * an unknown MC version cannot break the run.
     *
     * <p><b>Why fields and not only methods:</b> verified against the genuine
     * 1.21.4 client, {@code Minecraft} keeps the level and the current screen
     * in <em>public fields</em> ({@code flk.s} = ClientLevel, {@code flk.z} =
     * Screen) and does not route a join through a {@code setLevel} call, so
     * method hooks alone cannot observe a join on modern versions. The
     * harness therefore observes both: mapping-aware method hooks where the
     * target exists, plus a bounded, harness-only sampler as the version
     * independent fallback.
     *
     * @param mappings the loaded official mappings, or null on NO_REMAP
     */
    public void install(OfficialMappings mappings) {
        earlyInstance = this;
        // Merge screens captured by the static path before this instance
        // existed (v26.9-Alpha.8).
        for (String early : earlyScreens) {
            if (early != null && !screens.contains(early)) {
                screens.add(early);
            }
        }
        earlyScreens.clear();
        // Premain-registered hooks (if requested) are applied now so the
        // transformer injects on first load; the deferred-time registration
        // below then only adds the game-event routed path.
        boolean early = applyEarlyRegistration();
        LOG.info("[harness] install: earlyRegistration=" + early);
        if (early) {
            // Premain registration already placed the join/probe/screen hooks
            // into the shared registry, and the transformer injects them on
            // first class load. Re-registering here would double-fire, so only
            // the game-event routing and the retransform (both idempotent) run.
            installJoinViaGameEvents(
                    (mappings == null ? PACKET_LISTENER_CLASS
                            : mappings.runtimeName(PACKET_LISTENER_CLASS))
                            .replace('.', '/'),
                    translateDescriptor(mappings, HANDLE_LOGIN_DESC));
            retransformTargets(
                    translateClass(mappings, CLIENT_CLASS),
                    translateClass(mappings, PACKET_LISTENER_CLASS));
            writeReport();
            return;
        }
        String owner = translateClass(mappings, CLIENT_CLASS);
        this.resolvedClientClass = owner;
        // The field shape checks must compare against the RUNTIME type names:
        // on an obfuscated profile the level field's type is e.g. "gga", not
        // "ClientLevel", so a name-shape heuristic alone would never match.
        this.resolvedLevelType = mappings == null ? "ClientLevel"
                : mappings.runtimeName("net.minecraft.client.multiplayer.ClientLevel");
        this.resolvedScreenType = mappings == null ? "Screen"
                : mappings.runtimeName("net.minecraft.client.gui.screens.Screen");
        String levelDesc = translateDescriptor(mappings, SET_LEVEL_DESC);
        String screenDesc = translateDescriptor(mappings, SET_SCREEN_DESC);

        installHook(owner, SET_LEVEL, levelDesc, "world-join", () -> {
            if (worldJoinNanos.compareAndSet(-1, System.nanoTime())) {
                worldDetail = "setLevel observed on " + owner;
                tracker.transition(LiveContext.Side.CLIENT,
                        LiveContext.State.IN_WORLD, worldDetail);
                LOG.info("[harness] world join observed");
                writeReport();
            }
        });
        // Primary join signal: routed through the RUNTIME's own game-event
        // installer so registration, dispatcher attachment and retransform are
        // owned by the production path (v26.9-Alpha.8). The runtime exposes the
        // WORLD_LOAD event type; we subscribe to the bus it publishes on.
        String listenerOwner = translateClass(mappings, PACKET_LISTENER_CLASS);
        String loginDesc = translateDescriptor(mappings, HANDLE_LOGIN_DESC);
        installJoinViaGameEvents(listenerOwner, loginDesc);
        // Probe hook: ClientPacketListener.tick runs on EVERY client tick, so
        // firing proves the injected dispatch executes in the loaded class;
        // silence means the injection never landed in the executed bytes
        // (class-loading-context mismatch) - the decisive discriminator for
        // the open join-observation investigation (v26.9-Alpha.8).
        String tickOwner = listenerOwner;
        installHook(tickOwner, "tick", "()V", "probe(tick)", () -> {
            if (tickProbeCount.incrementAndGet() == 1) {
                LOG.info("[harness] PROBE FIRED: " + tickOwner
                        + ".tick is executing injected hooks");
                diagnostics.add("probe fired: hook dispatch executes in "
                        + tickOwner);
                writeReport();
            }
        });
        // Method hooks are injected when the transformer sees the class load.
        // By deferred-install time the client classes are already loaded, so a
        // retransform is required for the hooks to take effect (the agent
        // declares Can-Retransform-Classes: true). Without this the hooks
        // register cleanly but never fire - verified live.
        retransformTargets(owner, listenerOwner);
        installHook(owner, SET_SCREEN, screenDesc, "screen", this::captureScreen);
        // NOTE (v26.9-Alpha.8): a background field sampler was implemented and
        // then REMOVED. Verified live on the genuine 1.21.4 client, loading the
        // game client class from a sampler thread during early startup tripped
        // a class-loading circularity inside the agent's transform path:
        //   ClassCircularityError: java/lang/invoke/MethodHandle$1
        //   java.lang.instrument ASSERTION FAILED: invokeJavaAgentMainMethod
        // which aborted the game launch. Observation must therefore never pull
        // game classes in on a non-game thread. Method hooks (installed here)
        // stay; field-based observation needs a safe mechanism and is tracked
        // as follow-up work.
        writeReport();
    }

    /**
     * Registers the harness hooks at AGENT PREMAIN time (v26.9-Alpha.8).
     *
     * <p>This exists because injection happens when the transformer sees a
     * class load: registering after the client classes are already loaded
     * requires a retransform, and the live 1.21.4 run showed retransform
     * reporting success (count=1) while the injected dispatch never executed
     * (a probe on the per-tick method stayed silent through a full world
     * join). Registering at premain makes the transformer inject on the
     * <em>first</em> load of the target classes, which needs no retransform.
     *
     * <p>Only strings and a callback are stored here - no game class is
     * touched - so the premain class-loading hazard that forced the deferred
     * install (ClassCircularityError on java.lang.invoke.MethodHandle) does
     * not apply. The mapping translation is resolved later, at deferred
     * install time, when the mappings and the live context are known; this
     * method therefore registers the pre-translation "pending" hooks that
     * {@link #install(OfficialMappings)} re-keys.
     */
    public static void registerHooksEarly(LiveContextTracker tracker,
            OfficialMappings mappings) {
        pendingEarly = true;
        earlyTracker = tracker;
        earlyMappings = mappings;
        LOG.info("[harness] early hook registration requested at premain "
                + "(mappings=" + (mappings == null ? "none" : mappings.size()
                        + " classes") + ")");
    }

    /** Whether early registration was requested (install consumes it). */
    private static volatile boolean pendingEarly;
    /** The tracker the early registration targets (premain). */
    private static volatile LiveContextTracker earlyTracker;
    /** The mappings available at premain (null on NO_REMAP). */
    private static volatile OfficialMappings earlyMappings;

    /**
     * @return true when early registration was requested at premain
     */
    public static boolean isEarlyRegistrationRequested() {
        return pendingEarly;
    }

    /**
     * Installs the registered hooks into the hook registry at PREMAIN time so
     * the transformer injects them on the FIRST load of each target class
     * (v26.9-Alpha.8). Only strings are registered - no game class is touched -
     * which is why this is safe on the premain thread.
     *
     * @return true when the early registration completed
     */
    public static boolean applyEarlyRegistration() {
        if (!pendingEarly) {
            return false;
        }
        OfficialMappings mappings = earlyMappings;
        String owner = (mappings == null ? CLIENT_CLASS
                : mappings.runtimeName(CLIENT_CLASS)).replace('.', '/');
        // The screen hook can fire from Minecraft's static init / first screen
        // long before the deferred install() runs, so the resolved client class
        // must be available to the static capture path from this moment
        // (v26.9-Alpha.8: removes the "client class not resolved yet"
        // diagnostics).
        resolvedClientClassStatic = owner;
        String listener = (mappings == null ? PACKET_LISTENER_CLASS
                : mappings.runtimeName(PACKET_LISTENER_CLASS)).replace('.', '/');
        String loginDesc = mappings == null ? HANDLE_LOGIN_DESC
                : mappings.runtimeDescriptor(HANDLE_LOGIN_DESC);
        String levelDesc = mappings == null ? SET_LEVEL_DESC
                : mappings.runtimeDescriptor(SET_LEVEL_DESC);
        String screenDesc = mappings == null ? SET_SCREEN_DESC
                : mappings.runtimeDescriptor(SET_SCREEN_DESC);
        // CRITICAL: the hook key is matched against the CLASS-FILE method name,
        // so the method name must be mapping-translated exactly like the class
        // name and descriptor. Registering the official name (e.g.
        // "handleLogin") on an obfuscated runtime never matches the real name
        // ("a"), which is why the transformer saw the hook yet produced
        // unchanged bytes (v26.9-Alpha.8 root cause, found by dumping the
        // post-transform bytes).
        String loginMethod = runtimeMethodName(mappings, PACKET_LISTENER_CLASS,
                HANDLE_LOGIN);
        String tickMethod = runtimeMethodName(mappings, PACKET_LISTENER_CLASS, "tick");
        String levelMethod = runtimeMethodName(mappings, CLIENT_CLASS, SET_LEVEL);
        String screenMethod = runtimeMethodName(mappings, CLIENT_CLASS, SET_SCREEN);
        LiveContextTracker tracker = earlyTracker;

        earlyJoin = () -> markJoined(tracker,
                "handleLogin observed on " + listener);
        MethodHookRegistry.register(listener, loginMethod, loginDesc,
                earlyJoin);
        earlyProbe = () -> markProbe(owner, listener);
        MethodHookRegistry.register(listener, tickMethod, "()V", earlyProbe);
        earlyLevel = () -> markJoined(tracker, "setLevel observed on " + owner);
        MethodHookRegistry.register(owner, levelMethod, levelDesc, earlyLevel);
        earlyScreen = LiveHarness::captureScreenStatic;
        MethodHookRegistry.register(owner, screenMethod, screenDesc, earlyScreen);
        LOG.info("[harness] early hooks registered at premain: " + listener
                + "." + loginMethod + loginDesc + ", " + listener + "."
                + tickMethod + "()V, " + owner + "." + levelMethod + levelDesc);
        return true;
    }

    /**
     * Translates an official method name to its runtime name for hook keys.
     *
     * @param mappings the loaded mappings (null on NO_REMAP)
     * @param officialClass the official owner class name
     * @param officialMethod the official method name
     * @return the runtime method name
     */
    private static String runtimeMethodName(OfficialMappings mappings,
            String officialClass, String officialMethod) {
        return mappings == null ? officialMethod
                : mappings.runtimeMethodName(officialClass, officialMethod);
    }

    private static volatile Runnable earlyJoin;
    private static volatile Runnable earlyProbe;
    private static volatile Runnable earlyLevel;
    private static volatile Runnable earlyScreen;
    /** Shared early-registration state for the static callbacks. */
    private static volatile LiveHarness earlyInstance;

    private static void markJoined(LiveContextTracker tracker, String detail) {
        LiveHarness instance = earlyInstance;
        if (instance != null) {
            instance.onWorldJoined(detail);
            return;
        }
        if (tracker != null) {
            tracker.transition(LiveContext.Side.CLIENT,
                    LiveContext.State.IN_WORLD, detail);
        }
    }

    private static void markProbe(String owner, String listener) {
        LiveHarness instance = earlyInstance;
        if (instance != null) {
            instance.onProbeFired(listener);
        }
    }

    private static void captureScreenStatic() {
        LiveHarness instance = earlyInstance;
        if (instance != null) {
            instance.captureScreen();
            return;
        }
        // No deferred instance yet: record via the early-report hook so an
        // early screen observation is still captured (v26.9-Alpha.8).
        earlyScreens.add(describeScreenStatic());
    }

    /**
     * Best-effort static screen description used before the deferred instance
     * exists: resolves the client via the premain-resolved class name.
     *
     * @return a screen description, or null when unavailable
     */
    private static String describeScreenStatic() {
        String owner = resolvedClientClassStatic;
        if (owner == null) {
            return null;
        }
        try {
            Class<?> clientClass = Class.forName(owner.replace('/', '.'));
            // Same shape-based singleton resolution as clientInstance(): the
            // obfuscated client has no getInstance() method.
            Object client = null;
            for (Method candidate : clientClass.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(candidate.getModifiers())
                        && candidate.getParameterCount() == 0
                        && candidate.getReturnType() == clientClass) {
                    client = candidate.invoke(null);
                    break;
                }
            }
            if (client == null) {
                for (java.lang.reflect.Field field : clientClass.getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                            && field.getType() == clientClass) {
                        client = field.get(null);
                        break;
                    }
                }
            }
            if (client == null) {
                return null;
            }
            java.lang.reflect.Method getScreen = null;
            for (java.lang.reflect.Method m : clientClass.getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType().getName().endsWith("Screen")) {
                    getScreen = m;
                    break;
                }
            }
            if (getScreen == null) {
                return null;
            }
            Object screen = getScreen.invoke(client);
            return screen == null ? "<no screen>" : screen.getClass().getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Early-captured screen descriptions (merged into the instance later). */
    private static final java.util.List<String> earlyScreens =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    /** The premain-resolved client class name for the static capture path. */
    private static volatile String resolvedClientClassStatic;

    /**
     * Records an observed world join once (idempotent).
     *
     * @param detail the diagnostic detail
     */
    void onWorldJoined(String detail) {
        if (worldJoinNanos.compareAndSet(-1, System.nanoTime())) {
            worldDetail = detail;
            tracker.transition(LiveContext.Side.CLIENT,
                    LiveContext.State.IN_WORLD, detail);
            LOG.info("[harness] world join observed: " + detail);
            writeReport();
        }
    }

    /**
     * Records the first execution of the dispatch probe.
     *
     * @param listenerOwner the probed class name
     */
    void onProbeFired(String listenerOwner) {
        if (tickProbeCount.incrementAndGet() == 1) {
            LOG.info("[harness] PROBE FIRED: " + listenerOwner
                    + ".tick is executing injected hooks");
            synchronized (diagnostics) {
                diagnostics.add("probe fired: hook dispatch executes in "
                        + listenerOwner);
            }
            writeReport();
        }
    }

    /**
     * Registers the join hook through the runtime's game-event installer and
     * subscribes to the resulting world-load event (v26.9-Alpha.8). This uses
     * the production hook path rather than the raw registry so the runtime
     * owns dispatcher attachment and event delivery.
     *
     * @param listenerOwner the resolved ClientPacketListener class name
     * @param loginDesc the resolved handleLogin descriptor
     */
    private void installJoinViaGameEvents(String listenerOwner, String loginDesc) {
        // The hook key must use the RUNTIME method name (see applyEarlyRegistration).
        String loginMethodName = earlyMappings == null ? HANDLE_LOGIN
                : earlyMappings.runtimeMethodName(PACKET_LISTENER_CLASS, HANDLE_LOGIN);
        try {
            com.aprism.loader.AprismRuntime runtime =
                    com.aprism.loader.AprismRuntime.instance();
            com.aprism.loader.gameevent.GameEventDispatcher dispatcher =
                    runtime.getGameEventDispatcher();
            com.aprism.loader.gameevent.GameEventHookInstaller installer =
                    runtime.getGameEventHookInstaller();
            if (dispatcher == null || installer == null) {
                diagnostics.add("game-event installer unavailable");
                return;
            }
            dispatcher.setAttached(true);
            runtime.getEventBus().register(
                    com.aprism.api.gameevent.WorldLoadEvent.class,
                    event -> {
                        if (worldJoinNanos.compareAndSet(-1, System.nanoTime())) {
                            worldDetail = "WorldLoadEvent via " + listenerOwner
                                    + " (worldId=" + event.getWorldId() + ")";
                            tracker.transition(LiveContext.Side.CLIENT,
                                    LiveContext.State.IN_WORLD, worldDetail);
                            LOG.info("[harness] world join observed via game events");
                            writeReport();
                        }
                    });
            installer.install(new com.aprism.loader.gameevent.GameEventHookInstaller.HookTarget(
                    listenerOwner, loginMethodName, loginDesc,
                    com.aprism.loader.gameevent.GameEventHookInstaller.EventType.WORLD_LOAD));
            LOG.info("[harness] registered WORLD_LOAD hook via game-event installer: "
                    + listenerOwner + "." + HANDLE_LOGIN + loginDesc);
        } catch (Throwable failure) {
            diagnostics.add("game-event join wiring failed: " + failure);
            LOG.warning("[harness] game-event join wiring failed: " + failure);
        }
    }

    /**
     * Retransforms the already-loaded hook owner classes so the freshly
     * registered hooks are actually injected (v26.9-Alpha.8). Fail-open: an
     * unavailable class or unsupported retransform is recorded and the harness
     * continues with whatever hooks do apply.
     *
     * @param ownerNames the resolved (runtime) owner class names
     */
    private void retransformTargets(String... ownerNames) {
        com.aprism.loader.AprismRuntime runtime =
                com.aprism.loader.AprismRuntime.instance();
        com.aprism.loader.lowlevel.ClassRedefiner redefiner =
                runtime.getClassRedefiner();
        if (redefiner == null) {
            // Fall back to the agent's retained handle: the runtime may have
            // been initialized earlier without one (idempotent init guard).
            java.lang.instrument.Instrumentation inst =
                    com.aprism.loader.AprismAgent.getInstrumentation();
            if (inst != null) {
                redefiner = new com.aprism.loader.lowlevel.ClassRedefiner(inst);
                LOG.info("[harness] using the agent's retained instrumentation handle");
            }
        }
        if (redefiner == null) {
            diagnostics.add("retransform unavailable: no instrumentation handle");
            return;
        }
        java.util.List<Class<?>> targets = new java.util.ArrayList<>();
        for (String ownerName : ownerNames) {
            try {
                targets.add(Class.forName(ownerName.replace('/', '.'), false,
                        ClassLoader.getSystemClassLoader()));
            } catch (Throwable notLoadedYet) {
                // Not loaded yet: the transformer will apply the hook on load.
                LOG.info("[harness] class not loaded yet, hook applies on load: "
                        + ownerName);
            }
        }
        for (Class<?> target : targets) {
            try {
                int count = redefiner.retransform(target);
                LOG.info("[harness] retransformed " + target.getName()
                        + " (count=" + count + ")");
            } catch (Throwable retransformFailure) {
                diagnostics.add("retransform " + target.getName() + ": "
                        + retransformFailure);
            }
        }
    }

    /**
     * Installs one method hook, recording a diagnostic instead of throwing
     * when the target is unavailable on this MC version.
     *
     * @param owner the resolved (runtime) owner class name
     * @param method the resolved method name
     * @param descriptor the resolved method descriptor
     * @param label the diagnostic label
     * @param callback the callback invoked on each call
     */
    private void installHook(String owner, String method, String descriptor,
            String label, Runnable callback) {
        try {
            MethodHookRegistry.register(owner, method, descriptor, callback);
            synchronized (installedHooks) {
                installedHooks.add(new InstalledHook(owner, method, descriptor,
                        callback));
            }
            LOG.info("[harness] installed " + label + " hook: " + owner + "."
                    + method + descriptor);
        } catch (Throwable t) {
            diagnostics.add(label + " hook unavailable: " + t);
            LOG.warning("[harness] " + label + " hook unavailable: " + t);
        }
    }

    /**
     * Captures the current screen's widget tree reflectively and records it
     * for the report (Alpha.8 widget-tree check).
     */
    private void captureScreen() {
        try {
            Object client = clientInstance();
            if (client == null) {
                return;
            }
            // v26.9-Alpha.8: obfuscated clients expose the screen via a method
            // whose RETURN TYPE is the Screen class (or via the public screen
            // field) - not via a readable name like getScreen(). Resolve by
            // shape, matching the translated Screen type when known.
            Method getScreen = findScreenAccessor(client.getClass());
            Object screen = null;
            if (getScreen != null) {
                screen = getScreen.invoke(client);
            } else {
                screen = firstNonNullFieldValue(client, "screen");
            }
            String entry = screen == null ? "<null>"
                    : describeScreen(screen);
            synchronized (screens) {
                screens.add(entry);
                if (screens.size() > 16) {
                    screens.remove(0);
                }
            }
            writeReport();
        } catch (Throwable t) {
            diagnostics.add("screen capture failed: " + t);
        }
    }

    /** Best-effort description of a screen: class plus widget count. */
    private String describeScreen(Object screen) {
        StringBuilder sb = new StringBuilder(screen.getClass().getName());
        for (String fieldName : new String[] {"renderables", "children", "drawables"}) {
            try {
                Field field = findField(screen.getClass(), fieldName);
                if (field == null) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof List<?> widgets) {
                    sb.append(" widgets=").append(widgets.size());
                    int limit = Math.min(widgets.size(), 8);
                    for (int i = 0; i < limit; i++) {
                        sb.append(" [").append(widgets.get(i).getClass()
                                .getSimpleName()).append(']');
                    }
                    break;
                }
            } catch (Throwable ignored) {
                // try the next candidate field
            }
        }
        return sb.toString();
    }

    private Object clientInstance() {
        String owner = resolvedClientClass;
        if (owner == null) {
            owner = resolvedClientClassStatic;
        }
        if (owner == null) {
            diagnostics.add("client class not resolved yet (harness not installed)");
            return null;
        }
        try {
            Class<?> clientClass = Class.forName(owner.replace('/', '.'));
            // v26.9-Alpha.8: modern obfuscated clients have NO getInstance()
            // method (javap on 1.21.4 shows only a static field "static flk F"),
            // so resolve the singleton by SHAPE: first a no-arg static method
            // returning the client type, then a static field of that type.
            for (Method candidate : clientClass.getMethods()) {
                if (java.lang.reflect.Modifier.isStatic(candidate.getModifiers())
                        && candidate.getParameterCount() == 0
                        && candidate.getReturnType() == clientClass) {
                    return candidate.invoke(null);
                }
            }
            for (java.lang.reflect.Field field : clientClass.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && field.getType() == clientClass) {
                    return field.get(null);
                }
            }
            diagnostics.add("client singleton not found on " + owner);
            return null;
        } catch (Throwable t) {
            diagnostics.add("client instance unavailable: " + t);
            return null;
        }
    }

    private String translateClass(OfficialMappings mappings, String official) {
        return (mappings == null ? official : mappings.runtimeName(official))
                .replace('.', '/');
    }

    private String translateDescriptor(OfficialMappings mappings, String descriptor) {
        return mappings == null ? descriptor : mappings.runtimeDescriptor(descriptor);
    }

    /**
     * Reads the first non-null instance field of the target whose declared
     * type matches the requested kind (v26.9-Alpha.8). Currently used for the
     * screen fallback: on obfuscated runtimes the screen lives in a public
     * field typed {@code fum} (the translated Screen), so matching runs against
     * the translated type name and readable suffixes.
     *
     * @param target the client instance
     * @param names the requested kinds ({@code screen})
     * @return the value, or null when nothing matches
     */
    private Object firstNonNullFieldValue(Object target, String... names) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                for (String name : names) {
                    if (!"screen".equals(name)
                            || !matchesScreenShape(field.getType().getName())) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        Object value = field.get(target);
                        if (value != null) {
                            return value;
                        }
                    } catch (Throwable ignored) {
                        // not accessible: keep looking
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    /**
     * @param typeName the declared field type name
     * @return true when the type is the translated Screen type or a readable
     *         Screen type
     */
    private boolean matchesScreenShape(String typeName) {
        String screenType = resolvedScreenType;
        if (screenType != null
                && (typeName.equals(screenType)
                    || typeName.equals(screenType.replace('/', '.')))) {
            return true;
        }
        return typeName.endsWith("Screen") || typeName.contains("gui.screens");
    }

    /**
     * Finds a no-arg instance method on the client whose return type is the
     * Screen class (v26.9-Alpha.8). Obfuscated runtimes give the accessor an
     * opaque name, so the shape is the only stable signal; on a readable
     * runtime the getScreen-style method also matches by return type.
     *
     * @param clientClass the client class
     * @return the accessor, or null when none matches
     */
    private Method findScreenAccessor(Class<?> clientClass) {
        String screenType = resolvedScreenType;
        for (Method method : clientClass.getMethods()) {
            if (method.getParameterCount() != 0
                    || java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            String returnName = returnType.getName();
            if (screenType != null
                    && (returnName.equals(screenType)
                        || returnName.equals(screenType.replace('/', '.')))) {
                return method;
            }
            if (returnName.endsWith("Screen")
                    || returnName.contains("gui.screens")) {
                return method;
            }
        }
        return null;
    }

    private static Method findNoArg(Class<?> type, String preferred, String fallbackFragment) {
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (method.getName().equals(preferred)) {
                return method;
            }
        }
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 0
                    && method.getName().toLowerCase(java.util.Locale.ROOT)
                            .contains(fallbackFragment)
                    && !method.getReturnType().equals(void.class)) {
                return method;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException absent) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Writes the harness report into the game directory. Called on every
     * observed transition so a crash still leaves the latest state on disk.
     */
    public synchronized void writeReport() {
        if (outputDir == null) {
            return;
        }
        try {
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("aprism-harness.json"), toJson(),
                    StandardCharsets.UTF_8);
        } catch (IOException failure) {
            LOG.warning("[harness] report write failed: " + failure);
        }
    }

    /**
     * @return the report as compact JSON
     */
    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schemaVersion\":\"aprism.harness/v1\"")
                .append(",\"worldJoined\":").append(worldJoinNanos.get() > 0)
                .append(",\"worldDetail\":\"").append(escape(worldDetail)).append('"');
        sb.append(",\"metrics\":{\"bootstrapMs\":")
                .append(millisSince(premainNanos, bootstrapNanos.get()))
                .append(",\"worldJoinMs\":")
                .append(millisSince(premainNanos, worldJoinNanos.get()))
                .append('}');
        sb.append(",\"screens\":[");
        synchronized (screens) {
            for (int i = 0; i < screens.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(escape(screens.get(i))).append('"');
            }
        }
        sb.append("],\"diagnostics\":[");
        synchronized (diagnostics) {
            for (int i = 0; i < diagnostics.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(escape(diagnostics.get(i))).append('"');
            }
        }
        return sb.append("]}").toString();
    }

    /** @return the captured screen descriptions (test/diagnostic access) */
    public List<String> screens() {
        synchronized (screens) {
            return List.copyOf(screens);
        }
    }

    /** @return the recorded diagnostics */
    public List<String> diagnostics() {
        synchronized (diagnostics) {
            return List.copyOf(diagnostics);
        }
    }

    /** @return true once a world join was observed */
    public boolean worldJoined() {
        return worldJoinNanos.get() > 0;
    }

    /**
     * Removes the installed hooks (shutdown path; keeps reloads clean).
     */
    public void uninstall() {
        synchronized (installedHooks) {
            for (InstalledHook hook : installedHooks) {
                MethodHookRegistry.unregister(hook.owner(), hook.method(),
                        hook.descriptor(), hook.callback());
            }
            installedHooks.clear();
        }
    }

    private static double millisSince(long startNanos, long endNanos) {
        if (endNanos <= 0) {
            return -1.0;
        }
        return Math.round((endNanos - startNanos) / 10_000.0) / 100.0;
    }

    private static String escape(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
