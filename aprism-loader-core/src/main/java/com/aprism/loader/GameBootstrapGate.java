package com.aprism.loader;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Detects Minecraft's vanilla bootstrap point (registry freeze) so that mod
 * entrypoint dispatch can be deferred until the game is ready for it
 * (v26.7-Alpha.2).
 *
 * <p><b>Why this exists:</b> Aprism attaches as a {@code javaagent}, so its
 * premain runs before even {@code Main.main}. Deep foreign-loader mods
 * (e.g. JEI on the NeoForge path) query vanilla registries during their
 * constructors; at premain time those registries are not yet bootstrapped
 * and construction fails with {@code Not bootstrapped}. Real loaders
 * construct mods after bootstrap - so must we.
 *
 * <p><b>Detection strategy (layered, fail-open):</b>
 * <ol>
 *   <li>Reflectively invoke {@code net.minecraft.server.Bootstrap.checkBootstrapCalled(Supplier)}.
 *       Vanilla throws when bootstrap has not run; a clean return means the
 *       game is ready for registry access.</li>
 *   <li>If the class/method is absent (different MC naming), the gate is
 *       <em>inactive</em> and callers dispatch synchronously - identical to
 *       pre-v26.7-Alpha.2 behaviour, so nothing regresses.</li>
 * </ol>
 *
 * <p><b>Configuration (system properties):</b>
 * <ul>
 *   <li>{@code aprism.deferUntilBootstrap} - set to {@code false} to force
 *       synchronous dispatch (default: defer whenever the probe is active)</li>
 *   <li>{@code aprism.deferTimeoutMs} - fail-open timeout in milliseconds;
 *       if bootstrap has not been observed by then the lifecycle dispatches
 *       anyway (default: 120000)</li>
 *   <li>{@code aprism.deferPollMs} - poll interval (default: 100)</li>
 * </ul>
 *
 * <p>All failures inside the gate are contained: the worst case is the
 * historical immediate-dispatch behaviour, never a hung or crashed boot.
 *
 * @author opencode agent (ox-alpha), working session on behalf of the
 *         AprismRefract owner - upstream core support for the loader-support
 *         extraction line
 */
public final class GameBootstrapGate {

    /** Poll interval property name. */
    public static final String PROP_POLL_MS = "aprism.deferPollMs";
    /** Fail-open timeout property name. */
    public static final String PROP_TIMEOUT_MS = "aprism.deferTimeoutMs";
    /** Master switch property name. */
    public static final String PROP_DEFER = "aprism.deferUntilBootstrap";

    private static final String VANILLA_BOOTSTRAP_CLASS = "net.minecraft.server.Bootstrap";
    private static final String VANILLA_CHECK_METHOD = "checkBootstrapCalled";

    private static final AtomicBoolean DISPATCHED = new AtomicBoolean(false);

    private GameBootstrapGate() {
    }

    /**
     * Returns whether deferral is meaningful in this JVM: the vanilla
     * bootstrap probe is resolvable AND the master switch is not disabled.
     * When false, callers should dispatch synchronously exactly as before.
     *
     * @return true if the caller should hand its lifecycle dispatch to
     *         {@link #onBootstrapped(Runnable)}
     */
    public static boolean shouldDefer() {
        if ("false".equalsIgnoreCase(System.getProperty(PROP_DEFER))) {
            return false;
        }
        return probeAvailable();
    }

    /**
     * Runs {@code dispatch} once the vanilla bootstrap has completed. If the
     * gate observes bootstrap already done on its first poll, the runnable
     * executes inline on the calling thread. Otherwise a single daemon
     * watcher thread polls until bootstrapped or until the fail-open timeout
     * elapses, whichever comes first. The callback runs at most once per JVM.
     *
     * @param dispatch the lifecycle dispatch to run post-bootstrap
     */
    public static void onBootstrapped(Runnable dispatch) {
        if (!DISPATCHED.compareAndSet(false, true)) {
            return;
        }
        if (isVanillaBootstrapped()) {
            dispatch.run();
            return;
        }
        long pollMs = positiveLongProp(PROP_POLL_MS, 100L);
        long timeoutMs = positiveLongProp(PROP_TIMEOUT_MS, 120_000L);
        Thread watcher = new Thread(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;
            try {
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(pollMs);
                    if (isVanillaBootstrapped()) {
                        LoggerHolder.LOG.info(
                                "[gate] vanilla bootstrap detected - dispatching deferred mod lifecycle");
                        dispatch.run();
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Fail-open: never leave mods undispatched just because the
            // signal was missed.
            LoggerHolder.LOG.warning(
                    "[gate] bootstrap signal timeout after " + timeoutMs
                            + " ms - dispatching mod lifecycle fail-open");
            dispatch.run();
        }, "aprism-bootstrap-gate");
        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * Reflective probe: vanilla's own precondition check returns cleanly iff
     * bootstrap has run. Any reflection failure reports "not bootstrapped"
     * (the watcher keeps polling; the timeout covers pathological cases).
     *
     * @return true if vanilla bootstrap has completed
     */
    static boolean isVanillaBootstrapped() {
        try {
            Class<?> bootstrap = Class.forName(VANILLA_BOOTSTRAP_CLASS);
            java.lang.reflect.Method check = bootstrap.getDeclaredMethod(
                    VANILLA_CHECK_METHOD, Supplier.class);
            check.setAccessible(true);
            // A clean return means vanilla considers itself bootstrapped;
            // an IllegalStateException means it does not.
            check.invoke(null, (Supplier<String>) () -> "aprism-bootstrap-gate");
            return true;
        } catch (java.lang.reflect.InvocationTargetException notYet) {
            return false;
        } catch (Throwable reflectiveFailure) {
            LoggerHolder.LOG.warning("[gate] probe unavailable ("
                    + reflectiveFailure.getClass().getSimpleName()
                    + ") - treating as not-yet-bootstrapped");
            return false;
        }
    }

    /**
     * @return whether the vanilla probe class+method are resolvable here
     */
    private static boolean probeAvailable() {
        try {
            Class.forName(VANILLA_BOOTSTRAP_CLASS)
                    .getDeclaredMethod(VANILLA_CHECK_METHOD, Supplier.class);
            return true;
        } catch (Throwable absent) {
            return false;
        }
    }

    private static long positiveLongProp(String key, long fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException malformed) {
            return fallback;
        }
    }

    /** Deferred logger holder to keep the gate's static init trivial. */
    private static final class LoggerHolder {
        private static final java.util.logging.Logger LOG =
                java.util.logging.Logger.getLogger(GameBootstrapGate.class.getName());
    }
}
