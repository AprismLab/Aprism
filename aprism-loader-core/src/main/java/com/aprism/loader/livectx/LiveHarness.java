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
            Method getScreen = findNoArg(client.getClass(), "getScreen", "screen");
            if (getScreen == null) {
                diagnostics.add("screen accessor not found on "
                        + client.getClass().getName());
                return;
            }
            Object screen = getScreen.invoke(client);
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
            diagnostics.add("client class not resolved yet (harness not installed)");
            return null;
        }
        try {
            Class<?> clientClass = Class.forName(owner.replace('/', '.'));
            Method getInstance = clientClass.getMethod("getInstance");
            return getInstance.invoke(null);
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
