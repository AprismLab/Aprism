package com.aprism.loader.foreignlang;

import com.aprism.api.foreignlang.ForeignBinding;
import com.aprism.api.nativebridge.NativeResult;

import java.util.List;

/**
 * The cross-language runtime: manages {@link ForeignBinding}s and invokes
 * them through the native interop seam (v26.4-Alpha.8, Cpp2Java /
 * Rust2Java reference). This is the runtime half of the AprismJDK design
 * §6 cross-language transition; the binding-generator half (header
 * consumption, stub emission) is a build-time concern and ships separately.
 *
 * <p>Invocation is capability-gated end to end: the runtime looks up the
 * binding, then delegates to the active native bridge provider through
 * {@link com.aprism.loader.nativebridge.NativeBridgeRegistry}. On stock
 * JVMs with no FFM backend registered, every call is refused fail-closed
 * with a clear reason — never thrown.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class CrossLanguageRuntime {

    private final java.util.Map<String, ForeignBinding> bindings =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final com.aprism.loader.nativebridge.NativeBridgeRegistry nativeBridges;

    /**
     * @param nativeBridges the native interop seam used to load libraries
     *                      and resolve symbols
     */
    public CrossLanguageRuntime(
            com.aprism.loader.nativebridge.NativeBridgeRegistry nativeBridges) {
        this.nativeBridges = nativeBridges;
    }

    /**
     * Registers a binding. Duplicate ids are refused.
     *
     * @param binding the binding
     * @return the registered binding
     * @throws IllegalArgumentException on duplicate id
     */
    public ForeignBinding registerBinding(ForeignBinding binding) {
        java.util.Objects.requireNonNull(binding, "binding");
        if (bindings.putIfAbsent(binding.id(), binding) != null) {
            throw new IllegalArgumentException("binding already registered: " + binding.id());
        }
        return binding;
    }

    /**
     * @param id the binding id
     * @return the binding, or empty when unknown
     */
    public java.util.Optional<ForeignBinding> getBinding(String id) {
        return java.util.Optional.ofNullable(bindings.get(id));
    }

    /**
     * @return the ids of all registered bindings
     */
    public List<String> getBindingIds() {
        return List.copyOf(bindings.keySet());
    }

    /**
     * Invokes a registered binding (capability-gated).
     *
     * <p>Steps: look up the binding; find its symbol through the named
     * native provider; invoke with the given arguments. Any missing piece
     * (unknown binding, no provider, unresolved symbol, provider refusal)
     * is reported as a refused {@link NativeResult} with a reason.
     *
     * @param bindingId the binding id
     * @param providerName the native bridge provider to use
     * @param arguments the call arguments (already marshalled by the caller)
     * @return the invocation result
     */
    public NativeResult invoke(String bindingId, String providerName, Object... arguments) {
        ForeignBinding binding = bindings.get(bindingId);
        if (binding == null) {
            return NativeResult.refused("binding not registered: " + bindingId);
        }
        NativeResult symbol = nativeBridges.findSymbol(providerName, binding.library(),
                binding.symbolName(), com.aprism.api.nativebridge.NativeSymbol.Kind.FUNCTION);
        if (!symbol.success()) {
            return NativeResult.refused("symbol resolution failed: " + symbol.reason());
        }
        com.aprism.api.nativebridge.NativeSymbol resolved =
                new com.aprism.api.nativebridge.NativeSymbol(
                        binding.library(), binding.symbolName(),
                        com.aprism.api.nativebridge.NativeSymbol.Kind.FUNCTION);
        return nativeBridges.invoke(providerName, resolved, arguments);
    }

    /**
     * Removes all bindings. Called by the loader on shutdown.
     */
    public void clear() {
        bindings.clear();
    }
}
