package com.aprism.loader.resourcereload;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.aprism.loader.lowlevel.MethodHookRegistry;

/**
 * Triggers {@link ResourceReloadRegistryImpl#fireReload()} from real Minecraft
 * resource-manager reload events (v26.5-Alpha.7).
 *
 * <p>The v26.3-Alpha.9 resource-reload registry is a passive surface: it
 * exposes {@code fireReload()} but nothing calls it. This trigger uses the
 * v26.1-Alpha.8 {@link MethodHookRegistry} to register on-enter hooks against
 * Minecraft's resource-reload methods (e.g.
 * {@code ReloadableServerResources#reload} or
 * {@code Minecraft#getResourceManager}). When the hooked method runs, the
 * callback fires {@code fireReload()} on the shared registry.
 *
 * <p>The trigger does NOT hardcode Minecraft class names. It accepts
 * {@link ReloadHookTarget} records (class + method + descriptor) so that the
 * platform adapter layer supplies the correct targets.
 *
 * <p>All triggers are fail-safe: a throwing fireReload is caught by the
 * registry (per-listener isolation), and a throwing callback is caught by
 * {@link MethodHookRegistry#fire(String)}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ResourceReloadTrigger {

    private static final Logger LOG = Logger.getLogger("aprism.resourcereload");

    private final ResourceReloadRegistryImpl registry;
    private final List<ReloadHookTarget> registered = new ArrayList<>();
    private final List<Runnable> callbacks = new ArrayList<>();

    /**
     * @param registry the resource-reload registry to trigger
     */
    public ResourceReloadTrigger(ResourceReloadRegistryImpl registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.registry = registry;
    }

    /**
     * Registers a method hook target for resource reload detection.
     *
     * @param target the hook target (class + method + descriptor)
     */
    public void install(ReloadHookTarget target) {
        if (target == null) {
            return;
        }
        Runnable callback = registry::fireReload;
        MethodHookRegistry.register(
                target.className(), target.methodName(), target.descriptor(), callback);
        registered.add(target);
        callbacks.add(callback);
        LOG.info("Installed resource-reload trigger: "
                + target.className() + "." + target.methodName() + target.descriptor());
    }

    /**
     * Registers multiple hook targets.
     *
     * @param targets the hook targets
     */
    public void installAll(List<ReloadHookTarget> targets) {
        if (targets == null) {
            return;
        }
        for (ReloadHookTarget target : targets) {
            install(target);
        }
    }

    /**
     * Removes all previously registered hooks. Called on runtime shutdown.
     */
    public void uninstallAll() {
        for (int i = 0; i < registered.size(); i++) {
            ReloadHookTarget target = registered.get(i);
            Runnable callback = callbacks.get(i);
            MethodHookRegistry.unregister(
                    target.className(), target.methodName(), target.descriptor(), callback);
        }
        registered.clear();
        callbacks.clear();
    }

    /**
     * @return the list of registered hook targets
     */
    public List<ReloadHookTarget> getRegisteredTargets() {
        return List.copyOf(registered);
    }

    /**
     * A method hook target for resource reload detection.
     *
     * @param className  the slashed internal class name
     * @param methodName the method name
     * @param descriptor the JVM method descriptor
     */
    public record ReloadHookTarget(String className, String methodName, String descriptor) {

        /**
         * @return true when all fields are non-null and non-blank
         */
        public boolean isValid() {
            return className != null && !className.isBlank()
                    && methodName != null && !methodName.isBlank()
                    && descriptor != null && !descriptor.isBlank();
        }
    }
}
