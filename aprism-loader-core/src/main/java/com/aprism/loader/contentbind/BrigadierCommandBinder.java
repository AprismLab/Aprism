package com.aprism.loader.contentbind;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.aprism.api.commands.CommandRegistration;
import com.aprism.api.commands.CommandSpec;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Binds registered Aprism commands into the live Brigadier dispatcher
 * (v26.7-Alpha.2).
 *
 * <p>Target surface (official 26.x names): {@code Commands#getDispatcher()}
 * returns {@code com.mojang.brigadier.CommandDispatcher<CommandSourceStack>};
 * each {@link CommandSpec} becomes a
 * {@code LiteralArgumentBuilder.literal(name).executes(command)} registration
 * via {@code CommandDispatcher#register}. The Aprism handler is adapted onto
 * Brigadier's {@code Command<S>} functional interface through a dynamic
 * proxy, keeping the API dispatcher-independent.
 *
 * <p>Fail-closed contract: when no live dispatcher supplier resolves (the
 * Brigadier instance is created per world join, not statically reachable),
 * every spec refuses with TARGET_UNRESOLVED and nothing throws. Profile
 * gating matches the content binder (remapped profiles refuse outright).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class BrigadierCommandBinder {
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG = Logger.getLogger("aprism.contentbind");

    private static final String BRIGADIER_DISPATCHER =
            "com.mojang.brigadier.CommandDispatcher";
    private static final String BRIGADIER_LITERAL_BUILDER =
            "com.mojang.brigadier.builder.LiteralArgumentBuilder";
    private static final String BRIGADIER_COMMAND =
            "com.mojang.brigadier.Command";

    private final CommandRegistration registration;
    private boolean remapProfile;
    private Object liveDispatcher;

    /**
     * Outcome of one bind attempt.
     *
     * @param name    the command name
     * @param ok      whether the command reached the live dispatcher
     * @param refusal refusal reason when not ok ({@code PROFILE_UNSUPPORTED},
     *                {@code NO_DISPATCHER}, {@code ENTRY_FAILED})
     */
    public record BindResult(String name, boolean ok, String refusal) {
    }
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    public BrigadierCommandBinder(CommandRegistration registration) {
        this.registration = Objects.requireNonNull(registration, "registration");
    }

    /** Gates binding on the remap profile (mirrors the content binder). */
    public void setRemapProfile(boolean remapProfile) {
        this.remapProfile = remapProfile;
    }

    /**
     * Supplies the live Brigadier dispatcher once discovered (e.g. from the
     * running game). Null detaches; binds before attachment refuse.
     *
     * @param dispatcher a live {@code CommandDispatcher<?>} instance
     */
    public void attachDispatcher(Object dispatcher) {
        this.liveDispatcher = dispatcher;
    }

    /**
     * Binds every frozen command spec. Never throws; failures isolate per
     * entry.
     */
    public List<BindResult> bindAll() {
        List<BindResult> results = new ArrayList<>();
        List<CommandSpec> specs = registration.registeredCommands();
        if (remapProfile) {
            for (CommandSpec s : specs) {
                results.add(new BindResult(s.name(), false, "PROFILE_UNSUPPORTED"));
            }
            return results;
        }
        Object dispatcher = liveDispatcher;
        if (dispatcher == null) {
            LOG.fine("BrigadierCommandBinder: no live dispatcher attached");
            for (CommandSpec s : specs) {
                results.add(new BindResult(s.name(), false, "NO_DISPATCHER"));
            }
            return results;
        }
        for (CommandSpec spec : specs) {
            results.add(bindOne(dispatcher, spec));
        }
        long ok = results.stream().filter(BindResult::ok).count();
        LOG.info("Command binding: " + ok + "/" + results.size() + " bound");
        return results;
    }
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * Spawns a daemon poller that retries the bind every 2 s (up to
     * {@code timeoutMs}) until the live dispatcher appears - typically when
     * the player joins a single-player world and the integrated server
     * starts. Never blocks the caller.
     *
     * @param timeoutMs total polling budget in milliseconds
     */
    public void bindWhenAvailable(long timeoutMs) {
        Thread poller = new Thread(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Object d = LiveInstanceDiscovery.integratedCommandDispatcher();
                    if (d != null) {
                        attachDispatcher(d);
                        LOG.info("Live command dispatcher discovered (integrated server)");
                        List<BindResult> results = bindAll();
                        if (!results.isEmpty()
                                && results.stream().anyMatch(BindResult::ok)) {
                            return;
                        }
                    }
                } catch (RuntimeException e) {
                    LOG.warning("Deferred command binding attempt failed: " + e);
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
            LOG.info("Deferred command binding gave up after " + timeoutMs + " ms");
        }, "aprism-command-bind");
        poller.setDaemon(true);
        poller.start();
    }

    /**
     * Push-based counterpart of {@link #bindWhenAvailable(long)}
     * (v26.9-Alpha.3): binds once when the live context tracker reports a
     * world join, with no polling thread at all. Registered as a one-shot
     * listener per world join; safe alongside the poller (binds are
     * idempotent overwrites).
     *
     * @param tracker the live context tracker to subscribe to
     */
    public void bindOnLiveContext(
            com.aprism.loader.livectx.LiveContextTracker tracker) {
        //GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        tracker.addListener(new com.aprism.loader.livectx.BindTrigger() {
            private volatile boolean bound;

            @Override
            public void onTransition(com.aprism.loader.livectx.LiveContextTransition t) {
                if (bound
                        || t.side() != com.aprism.loader.livectx.LiveContext.Side.CLIENT
                        || t.to() != com.aprism.loader.livectx.LiveContext.State.IN_WORLD) {
                    return;
                }
                try {
                    Object d = LiveInstanceDiscovery.integratedCommandDispatcher();
                    if (d == null) {
                        return;
                    }
                    bound = true;
                    attachDispatcher(d);
                    LOG.info("Live command dispatcher bound via live-context trigger");
                    bindAll();
                } catch (RuntimeException e) {
                    LOG.warning("Triggered command binding failed: " + e);
                }
            }
        });
    }

    private BindResult bindOne(Object dispatcher, CommandSpec spec) {
        try {
            ClassLoader loader = dispatcher.getClass().getClassLoader();
            Class<?> builderClass = loader.loadClass(BRIGADIER_LITERAL_BUILDER);
            Method literal = builderClass.getMethod("literal", String.class);
            Object builder = literal.invoke(null, spec.name());
            Class<?> commandInterface = loader.loadClass(BRIGADIER_COMMAND);
            Object command = Proxy.newProxyInstance(loader, new Class<?>[] {commandInterface},
                    (proxy, method, args) -> {
                        try {
                            runHandler(spec);
                            return 1; // brigadier SUCCESS single int
                        } catch (RuntimeException e) {
                            LOG.warning("Command '" + spec.name() + "' handler failed: " + e);
                            return 0;
                        }
                    });
            Method executes = findExecutes(builderClass);
            executes.setAccessible(true);
            executes.invoke(builder, command);
            Method register = dispatcher.getClass().getMethod("register", builderClass);
            register.setAccessible(true);
            register.invoke(dispatcher, builder);
            return new BindResult(spec.name(), true, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable cause = (e instanceof java.lang.reflect.InvocationTargetException ite
                    && ite.getCause() != null) ? ite.getCause() : e;
            LOG.warning("Failed to bind command '" + spec.name() + "': " + cause);
            return new BindResult(spec.name(), false, "ENTRY_FAILED");
        }
    }

    /** Adapts the untyped handler: Runnable handlers run; others no-op. */
    private static void runHandler(CommandSpec spec) {
        if (spec.handler() instanceof Runnable r) {
            r.run();
        }
    }

    private static Method findExecutes(Class<?> builderClass)
            throws NoSuchMethodException {
        // builds(...) wraps; the generic executes(Command) lives on the base
        // ArgumentBuilder under erasure of the self type.
        for (Class<?> c = builderClass; c != null && c != Object.class;
                c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if ("executes".equals(m.getName())
                        && m.getParameterCount() == 1) {
                    return m;
                }
            }
        }
        throw new NoSuchMethodException("executes(Command) not found");
    }
}
