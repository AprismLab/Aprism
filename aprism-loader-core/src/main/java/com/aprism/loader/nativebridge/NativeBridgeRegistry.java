package com.aprism.loader.nativebridge;

import com.aprism.api.nativebridge.NativeBridgeProvider;
import com.aprism.api.nativebridge.NativeLibraryHandle;
import com.aprism.api.nativebridge.NativeResult;
import com.aprism.api.nativebridge.NativeSymbol;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of native interop providers (v26.4-Alpha.5, native interop
 * bridge). Capability-gated like {@link com.aprism.loader.ai.AiRegistry}:
 * a named provider (the FFM backend on AprismJDK) registers itself; every
 * operation checks {@code isAvailable()} and returns a refusal rather than
 * throwing into the game. When no provider is registered at all, every
 * operation is refused with a clear reason.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class NativeBridgeRegistry {

    private final Map<String, NativeBridgeProvider> providers = new ConcurrentHashMap<>();

    /**
     * Registers a provider under its {@link NativeBridgeProvider#name()}.
     * Duplicate names are refused.
     *
     * @param provider the provider
     * @return the registered provider
     * @throws IllegalArgumentException when the name is already registered
     */
    public NativeBridgeProvider register(NativeBridgeProvider provider) {
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
    public Optional<NativeBridgeProvider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    /**
     * @return the names of all registered providers
     */
    public List<String> getProviderNames() {
        return List.copyOf(providers.keySet());
    }

    /**
     * @return the names of providers currently reporting available
     */
    public List<String> getAvailableProviderNames() {
        return providers.values().stream()
                .filter(NativeBridgeProvider::isAvailable)
                .map(NativeBridgeProvider::name)
                .toList();
    }

    /**
     * @return whether any registered provider is currently available
     */
    public boolean hasAvailableProvider() {
        return providers.values().stream().anyMatch(NativeBridgeProvider::isAvailable);
    }

    /**
     * Loads a library through a named provider (capability-gated).
     *
     * @param providerName the provider name
     * @param libraryName the library name or path
     * @return the load result; a refusal when the provider is unknown,
     *         unavailable, or throws
     */
    public NativeResult loadLibrary(String providerName, String libraryName) {
        NativeBridgeProvider provider = providers.get(providerName);
        if (provider == null) {
            return NativeResult.refused("native provider not registered: " + providerName);
        }
        if (!provider.isAvailable()) {
            return NativeResult.refused("native provider unavailable: " + providerName);
        }
        try {
            return provider.loadLibrary(libraryName);
        } catch (RuntimeException e) {
            return NativeResult.refused("native provider failed: " + e.getMessage());
        }
    }

    /**
     * Resolves a symbol through a named provider (capability-gated).
     *
     * @param providerName the provider name
     * @param libraryName the library name
     * @param symbolName the symbol name
     * @param kind the symbol kind
     * @return the resolution result
     */
    public NativeResult findSymbol(String providerName, String libraryName,
            String symbolName, NativeSymbol.Kind kind) {
        NativeBridgeProvider provider = providers.get(providerName);
        if (provider == null) {
            return NativeResult.refused("native provider not registered: " + providerName);
        }
        if (!provider.isAvailable()) {
            return NativeResult.refused("native provider unavailable: " + providerName);
        }
        try {
            return provider.findSymbol(libraryName, symbolName, kind);
        } catch (RuntimeException e) {
            return NativeResult.refused("native provider failed: " + e.getMessage());
        }
    }

    /**
     * Invokes a function symbol through a named provider (capability-gated).
     *
     * @param providerName the provider name
     * @param symbol the function symbol
     * @param arguments the call arguments
     * @return the invocation result
     */
    public NativeResult invoke(String providerName, NativeSymbol symbol, Object... arguments) {
        NativeBridgeProvider provider = providers.get(providerName);
        if (provider == null) {
            return NativeResult.refused("native provider not registered: " + providerName);
        }
        if (!provider.isAvailable()) {
            return NativeResult.refused("native provider unavailable: " + providerName);
        }
        try {
            return provider.invoke(symbol, arguments);
        } catch (RuntimeException e) {
            return NativeResult.refused("native provider failed: " + e.getMessage());
        }
    }

    /**
     * @param providerName the provider name
     * @return the loaded libraries for that provider, or empty when the
     *         provider is unknown
     */
    public List<NativeLibraryHandle> loadedLibraries(String providerName) {
        NativeBridgeProvider provider = providers.get(providerName);
        return provider == null ? List.of() : provider.loadedLibraries();
    }

    /**
     * Removes all providers. Called by the loader on shutdown.
     */
    public void clear() {
        providers.clear();
    }
}
