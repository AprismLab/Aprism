package com.aprism.api.nativebridge;

import java.util.List;

/**
 * The native interop provider contract (v26.4-Alpha.5, native interop
 * bridge). This is the seam: the loader core defines the contract, and a
 * concrete backend — the Foreign Function &amp; Memory based backend on
 * AprismJDK — supplies the implementation. On stock JVMs without an FFM
 * backend registered, every operation is capability-gated to a refusal;
 * nothing ever throws into the game.
 *
 * <p>The contract intentionally covers the three responsibilities named in
 * the AprismJDK design (§6 cross-language transition): native-library
 * lifecycle (load, symbol resolution), invocation, and arena-scoped
 * memory management.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface NativeBridgeProvider {

    /**
     * @return the provider name (e.g. {@code ffm})
     */
    String name();

    /**
     * @return whether this provider can serve native interop on the
     *         current JVM
     */
    boolean isAvailable();

    /**
     * Loads a native library.
     *
     * @param libraryName the library name or path
     * @return the load result (handle on success)
     */
    NativeResult loadLibrary(String libraryName);

    /**
     * Unloads a previously loaded library.
     *
     * @param libraryName the library name
     * @return the unload result
     */
    NativeResult unloadLibrary(String libraryName);

    /**
     * Resolves a symbol in a loaded library.
     *
     * @param libraryName the library name
     * @param symbolName the symbol name
     * @param kind the symbol kind
     * @return the resolution result
     */
    NativeResult findSymbol(String libraryName, String symbolName, NativeSymbol.Kind kind);

    /**
     * Invokes a previously resolved function symbol.
     *
     * @param symbol the function symbol
     * @param arguments the call arguments (marshalled by the provider)
     * @return the invocation result
     */
    NativeResult invoke(NativeSymbol symbol, Object... arguments);

    /**
     * @return the currently loaded libraries
     */
    List<NativeLibraryHandle> loadedLibraries();
}
