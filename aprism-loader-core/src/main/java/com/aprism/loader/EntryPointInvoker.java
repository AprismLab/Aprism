package com.aprism.loader;

import java.util.List;

import com.aprism.api.AprismContext;
import com.aprism.api.IAprismMod;
import com.aprism.manifest.AprismManifest;

/**
 * Invokes mod entrypoint classes via reflection. Each entrypoint class is
 * loaded through the {@link AprismClassLoader}, instantiated via its no-arg
 * constructor, and cast to {@link IAprismMod} so the appropriate lifecycle
 * method can be invoked.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class EntryPointInvoker {

    private final AprismClassLoader classLoader;

    /**
     * @param classLoader the classloader used to load entrypoint classes
     */
    public EntryPointInvoker(AprismClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Invokes the {@code main} entrypoint(s) of the given mod.
     *
     * @param manifest the mod manifest
     * @param context  the runtime context
     */
    public void invokeMain(AprismManifest manifest, AprismContext context) {
        invokeEntrypoint(manifest, "main", context);
    }

    /**
     * Invokes the {@code client} entrypoint(s) of the given mod.
     *
     * @param manifest the mod manifest
     * @param context  the runtime context
     */
    public void invokeClient(AprismManifest manifest, AprismContext context) {
        invokeEntrypoint(manifest, "client", context);
    }

    /**
     * Invokes the {@code server} entrypoint(s) of the given mod.
     *
     * @param manifest the mod manifest
     * @param context  the runtime context
     */
    public void invokeServer(AprismManifest manifest, AprismContext context) {
        invokeEntrypoint(manifest, "server", context);
    }

    /**
     * Loads and invokes each entrypoint class registered under the given key.
     *
     * @param manifest the mod manifest
     * @param key      the entrypoint key (e.g. {@code main})
     * @param context  the runtime context
     */
    private void invokeEntrypoint(AprismManifest manifest, String key, AprismContext context) {
        List<String> entrypoints = manifest.entrypoints().get(key);
        if (entrypoints == null) {
            return;
        }
        for (String className : entrypoints) {
            try {
                Class<?> clazz = classLoader.loadClass(className);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (instance instanceof IAprismMod mod) {
                    mod.onInitialize(context);
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke entrypoint " + className, e);
            }
        }
    }
}
