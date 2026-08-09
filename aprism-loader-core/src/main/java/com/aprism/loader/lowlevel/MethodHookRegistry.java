package com.aprism.loader.lowlevel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Static registry of programmatic method hooks. This is the lower-level hook
 * mechanism that MCJEBooster-style engines use to instrument Minecraft
 * methods (e.g. the server tick loop) without requiring Mixin annotations or
 * a mod recompile: callers register a hook against a (class, method,
 * descriptor) triple and the {@link MethodHookTransformer} injects a dispatch
 * call into matching methods at load (or retransform) time.
 *
 * <p>Part of the v26.1-Alpha.8 lower-level API foundation (goal #2).
 *
 * <p>Hooks are keyed by a stable string {@code className.methodName+descriptor}
 * so the injected bytecode only needs to carry one constant. Firing a hook
 * with no listeners is a cheap no-op, so hooks left registered but unused
 * cost almost nothing.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class MethodHookRegistry {

    private static final Logger LOG = Logger.getLogger("aprism.lowlevel");

    private static final Map<String, List<Runnable>> HOOKS = new ConcurrentHashMap<>();

    private MethodHookRegistry() {
    }

    /**
     * Builds the stable hook key for a method.
     *
     * @param className  the slashed class name
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @return the hook key
     */
    public static String hookKey(String className, String methodName, String descriptor) {
        return className + "." + methodName + descriptor;
    }

    /**
     * Registers a hook listener for a method.
     *
     * @param className  the slashed class name
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @param listener   the callback invoked each time the hooked method runs
     */
    public static void register(String className, String methodName, String descriptor,
            Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        HOOKS.computeIfAbsent(hookKey(className, methodName, descriptor),
                k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Removes a previously registered hook listener.
     *
     * @param className  the slashed class name
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @param listener   the callback to remove
     */
    public static void unregister(String className, String methodName, String descriptor,
            Runnable listener) {
        List<Runnable> list = HOOKS.get(hookKey(className, methodName, descriptor));
        if (list != null) {
            list.remove(listener);
        }
    }

    /**
     * Whether any listener is registered for any method of the given class.
     * Used as a fast pre-check by the transformer to skip the ASM pass when
     * no hooks exist for the class at all.
     *
     * @param className the slashed class name
     * @return true when at least one hook is registered for the class
     */
    public static boolean hasAnyHookForClass(String className) {
        String prefix = className + ".";
        for (String key : HOOKS.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any listener is registered for a method. Used by the
     * transformer to decide whether a method needs a dispatch injected.
     *
     * @param className  the slashed class name
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @return true when at least one listener is registered
     */
    public static boolean hasHook(String className, String methodName, String descriptor) {
        List<Runnable> list = HOOKS.get(hookKey(className, methodName, descriptor));
        return list != null && !list.isEmpty();
    }

    /**
     * Fires all listeners registered for the given hook key. Called by the
     * bytecode injected by {@link MethodHookTransformer}. A listener that
     * throws is logged and swallowed so a faulty hook never crashes the host
     * game.
     *
     * @param hookKey the hook key (see {@link #hookKey})
     */
    public static void fire(String hookKey) {
        List<Runnable> list = HOOKS.get(hookKey);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Runnable listener : list) {
            try {
                listener.run();
            } catch (Throwable t) {
                LOG.warning("Method hook " + hookKey + " threw: " + t);
            }
        }
    }

    /**
     * Removes all hooks. Called on runtime shutdown for a clean reload cycle.
     */
    public static void clear() {
        HOOKS.clear();
    }
}
