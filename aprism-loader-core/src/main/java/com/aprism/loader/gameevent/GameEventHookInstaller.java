package com.aprism.loader.gameevent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.aprism.loader.lowlevel.MethodHookRegistry;

/**
 * Registers method hooks that bridge Minecraft's game loop into the
 * {@link GameEventDispatcher} (v26.5-Alpha.3).
 *
 * <p>The dispatcher itself (v26.3-Alpha.1) is a passive seam: it exposes
 * {@code fireTickStart}, {@code fireTickEnd}, {@code fireRender},
 * {@code fireWorldLoad}, {@code fireWorldUnload} methods that the native
 * injector is expected to call. This installer uses the v26.1-Alpha.8
 * {@link MethodHookRegistry} to register {@code Runnable} callbacks against
 * specific Minecraft methods; when the transformer injects the dispatch call
 * and the method runs, the callback fires the corresponding game event on the
 * shared event bus.
 *
 * <p>The installer does NOT hardcode Minecraft class names. It accepts
 * {@link HookTarget} records (class + method + descriptor + event type) so
 * that the platform adapter layer (which knows the running MC version's
 * obfuscation profile) supplies the correct targets. This keeps the loader
 * core version-agnostic per the architecture invariant (Doc 01 section 4.2).
 *
 * <p>After registering hooks, the caller should use
 * {@link com.aprism.loader.lowlevel.ClassRedefiner#retransform(Class[])} to
 * retransform the target classes so the injected dispatch calls take effect
 * on already-loaded classes. For classes not yet loaded, the
 * {@link com.aprism.loader.AprismClassTransformer} will inject the dispatch
 * call at load time.
 *
 * <p>All hooks are fail-safe: a throwing callback is caught by
 * {@link MethodHookRegistry#fire(String)} and logged, never propagating into
 * the game loop.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class GameEventHookInstaller {

    private static final Logger LOG = Logger.getLogger("aprism.gameevent");

    private final GameEventDispatcher dispatcher;
    private final List<RegisteredHook> registered = new ArrayList<>();

    /**
     * @param dispatcher the game event dispatcher to fire events onto
     */
    public GameEventHookInstaller(GameEventDispatcher dispatcher) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher must not be null");
        }
        this.dispatcher = dispatcher;
    }

    /**
     * Registers a method hook target. After this call and a retransform of
     * the target class, every invocation of the target method will fire the
     * corresponding game event.
     *
     * @param target the hook target to register
     */
    public void install(HookTarget target) {
        if (target == null) {
            return;
        }
        Runnable callback = createCallback(target.eventType());
        MethodHookRegistry.register(
                target.className(), target.methodName(), target.descriptor(), callback);
        registered.add(new RegisteredHook(target, callback));
        LOG.info("Installed game-event hook: " + target.eventType()
                + " -> " + target.className() + "." + target.methodName() + target.descriptor());
    }

    /**
     * Registers multiple hook targets.
     *
     * @param targets the hook targets to register
     */
    public void installAll(List<HookTarget> targets) {
        if (targets == null) {
            return;
        }
        for (HookTarget target : targets) {
            install(target);
        }
    }

    /**
     * Removes all previously registered hooks and clears the installed list.
     * Called on runtime shutdown for a clean reload cycle.
     */
    public void uninstallAll() {
        for (RegisteredHook rh : registered) {
            MethodHookRegistry.unregister(
                    rh.target().className(), rh.target().methodName(),
                    rh.target().descriptor(), rh.callback());
        }
        registered.clear();
    }

    /**
     * @return the list of registered hook targets
     */
    public List<HookTarget> getRegisteredTargets() {
        return registered.stream().map(RegisteredHook::target).toList();
    }

    /**
     * Creates the callback for a given event type.
     *
     * @param eventType the game event type
     * @return a Runnable that fires the corresponding dispatcher method
     */
    private Runnable createCallback(EventType eventType) {
        return switch (eventType) {
            case TICK_START -> dispatcher::fireTickStart;
            case TICK_END -> dispatcher::fireTickEnd;
            case RENDER -> () -> dispatcher.fireRender(0.0);
            case WORLD_LOAD -> () -> dispatcher.fireWorldLoad("");
            case WORLD_UNLOAD -> () -> dispatcher.fireWorldUnload("");
        };
    }

    /**
     * The type of game event a hook target fires.
     */
    public enum EventType {
        /** Fires {@link GameEventDispatcher#fireTickStart()}. */
        TICK_START,
        /** Fires {@link GameEventDispatcher#fireTickEnd()}. */
        TICK_END,
        /** Fires {@link GameEventDispatcher#fireRender(double)}. */
        RENDER,
        /** Fires {@link GameEventDispatcher#fireWorldLoad(String)}. */
        WORLD_LOAD,
        /** Fires {@link GameEventDispatcher#fireWorldUnload(String)}. */
        WORLD_UNLOAD
    }

    /**
     * A method hook target: the slashed class name, method name, JVM
     * descriptor, and the event type to fire when the method is invoked.
     *
     * @param className  the slashed internal class name (e.g. {@code net/minecraft/server/MinecraftServer})
     * @param methodName the method name (e.g. {@code tickServer})
     * @param descriptor the JVM method descriptor (e.g. {@code ()V})
     * @param eventType  the game event to fire
     */
    public record HookTarget(String className, String methodName, String descriptor,
            EventType eventType) {

        /**
         * Validates the hook target fields.
         *
         * @return true when all fields are non-null and non-blank
         */
        public boolean isValid() {
            return className != null && !className.isBlank()
                    && methodName != null && !methodName.isBlank()
                    && descriptor != null && !descriptor.isBlank()
                    && eventType != null;
        }
    }

    /**
     * Internal record pairing a hook target with its registered callback so
     * that {@link #uninstallAll()} can remove the exact same callback
     * instance from {@link MethodHookRegistry}.
     */
    private record RegisteredHook(HookTarget target, Runnable callback) {
    }
}
