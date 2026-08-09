package com.aprism.loader.loaderext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of {@link LoaderEntrypointHandler} instances keyed by loader key.
 *
 * <p>This is the extraction seam that lets foreign-loader entrypoint support
 * live outside {@code aprism-loader-core}. The Aprism core no longer needs to
 * import any loader bridge; it consults this registry at dispatch time. Each
 * AprismRefract loader-support extension registers its handler here during
 * extension initialization.
 *
 * <p>Thread-safe: registration and lookup may happen on different threads
 * (extension init vs. lifecycle dispatch).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LoaderEntrypointRegistry {

    private static final Map<String, LoaderEntrypointHandler> HANDLERS =
            new ConcurrentHashMap<>();

    private LoaderEntrypointRegistry() {
    }

    /**
     * Registers a handler for its {@link LoaderEntrypointHandler#loaderKey()}.
     * A later registration for the same key replaces the earlier one (the most
     * recently loaded loader-support extension wins).
     *
     * @param handler the handler to register
     */
    public static void register(LoaderEntrypointHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        String key = handler.loaderKey();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("handler.loaderKey() must not be blank");
        }
        HANDLERS.put(key, handler);
    }

    /**
     * Looks up the handler for a loader key.
     *
     * @param loaderKey the loader key (e.g. {@code "Fa"})
     * @return the registered handler, or {@code null} if none
     */
    public static LoaderEntrypointHandler get(String loaderKey) {
        if (loaderKey == null) {
            return null;
        }
        return HANDLERS.get(loaderKey);
    }

    /**
     * Removes the handler for a loader key.
     *
     * @param loaderKey the loader key
     */
    public static void unregister(String loaderKey) {
        if (loaderKey != null) {
            HANDLERS.remove(loaderKey);
        }
    }

    /**
     * Clears all registered handlers. Called on runtime shutdown so a fresh
     * load cycle starts clean.
     */
    public static void clear() {
        HANDLERS.clear();
    }
}
