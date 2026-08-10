package com.aprism.loader.rendering;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.aprism.api.rendering.RenderBackend;
import com.aprism.api.rendering.RenderCapability;
import com.aprism.api.rendering.RenderingProvider;

/**
 * Registry of rendering providers (v26.3-Alpha.5, goal #9).
 * <strong>Experimental / reference-only: no production guarantee.</strong>
 *
 * <p>A {@code rendering-extension} (.aep) registers its
 * {@link RenderingProvider} during {@code onInitialize}; mods and Aprism
 * then query backend availability and capabilities through the registry.
 * The registry is capability-gated: capability queries check
 * {@code isReady()} and return empty when the provider is not ready, never
 * throwing into the game.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class RenderingRegistry {

    private final Map<String, RenderingProvider> providers = new ConcurrentHashMap<>();

    /**
     * Registers a provider under its {@link RenderingProvider#name()}.
     * Duplicate names are refused.
     *
     * @param provider the provider
     * @return the registered provider
     * @throws IllegalArgumentException when the name is already registered
     */
    public RenderingProvider register(RenderingProvider provider) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(provider.name(), "provider.name");
        if (providers.putIfAbsent(provider.name(), provider) != null) {
            throw new IllegalArgumentException("provider already registered: " + provider.name());
        }
        return provider;
    }

    /**
     * @param name the provider name
     * @return the provider, or empty when unknown
     */
    public Optional<RenderingProvider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    /**
     * @return the names of all registered providers
     */
    public List<String> getProviderNames() {
        return List.copyOf(providers.keySet());
    }

    /**
     * @return the names of providers currently reporting ready
     */
    public List<String> getReadyProviderNames() {
        return providers.values().stream()
                .filter(RenderingProvider::isReady)
                .map(RenderingProvider::name)
                .toList();
    }

    /**
     * Queries the capability of a backend on the current machine
     * (capability-gated).
     *
     * @param providerName the provider name
     * @param backend      the backend to query
     * @return the capability, or empty when the provider is unknown, not
     *         ready, or does not actually expose the backend
     */
    public Optional<RenderCapability> queryCapability(String providerName, RenderBackend backend) {
        RenderingProvider provider = providers.get(providerName);
        if (provider == null) {
            return Optional.empty();
        }
        if (!provider.isReady()) {
            return Optional.empty();
        }
        if (!provider.supportedBackends().contains(backend)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(provider.queryCapability(backend));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Drops all providers (runtime shutdown).
     */
    public void clear() {
        providers.clear();
    }
}
