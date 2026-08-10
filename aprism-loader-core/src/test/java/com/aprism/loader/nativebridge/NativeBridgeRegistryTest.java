package com.aprism.loader.nativebridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.nativebridge.NativeBridgeProvider;
import com.aprism.api.nativebridge.NativeLibraryHandle;
import com.aprism.api.nativebridge.NativeResult;
import com.aprism.api.nativebridge.NativeSymbol;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the native interop bridge registry (v26.4-Alpha.5): provider
 * registration, capability gating, refusal semantics, symbol validation,
 * and runtime wiring.
 *
 * @author BlockConnect@StarsailsClover
 */
class NativeBridgeRegistryTest {

    private final NativeBridgeRegistry registry = new NativeBridgeRegistry();

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    private static NativeBridgeProvider provider(String name, boolean available) {
        return new NativeBridgeProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public NativeResult loadLibrary(String libraryName) {
                return NativeResult.ok(new NativeLibraryHandle(libraryName, true, 0));
            }

            @Override
            public NativeResult unloadLibrary(String libraryName) {
                return NativeResult.ok(null);
            }

            @Override
            public NativeResult findSymbol(String libraryName, String symbolName,
                    NativeSymbol.Kind kind) {
                return NativeResult.ok(new NativeSymbol(libraryName, symbolName, kind));
            }

            @Override
            public NativeResult invoke(NativeSymbol symbol, Object... arguments) {
                return NativeResult.ok(42);
            }

            @Override
            public List<NativeLibraryHandle> loadedLibraries() {
                return List.of();
            }
        };
    }

    @Nested
    class Registration {

        @Test
        void registerAndListProviders() {
            NativeBridgeProvider provider = provider("ffm", true);

            registry.register(provider);

            assertThat(registry.getProviderNames()).containsExactly("ffm");
            assertThat(registry.get("ffm")).contains(provider);
        }

        @Test
        void duplicateNameIsRejected() {
            registry.register(provider("ffm", true));

            assertThatThrownBy(() -> registry.register(provider("ffm", false)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        void availableProviderFilteringWorks() {
            registry.register(provider("ffm", true));
            registry.register(provider("stub", false));

            assertThat(registry.getAvailableProviderNames()).containsExactly("ffm");
            assertThat(registry.hasAvailableProvider()).isTrue();
        }

        @Test
        void clearRemovesProviders() {
            registry.register(provider("ffm", true));

            registry.clear();

            assertThat(registry.getProviderNames()).isEmpty();
            assertThat(registry.hasAvailableProvider()).isFalse();
        }
    }

    @Nested
    class CapabilityGating {

        @Test
        void unknownProviderIsRefused() {
            NativeResult result = registry.loadLibrary("missing", "lib");

            assertThat(result.success()).isFalse();
            assertThat(result.reason()).contains("not registered");
        }

        @Test
        void unavailableProviderIsRefused() {
            registry.register(provider("stub", false));

            NativeResult result = registry.loadLibrary("stub", "lib");

            assertThat(result.success()).isFalse();
            assertThat(result.reason()).contains("unavailable");
        }

        @Test
        void availableProviderLoadsLibrary() {
            registry.register(provider("ffm", true));

            NativeResult result = registry.loadLibrary("ffm", "lib");

            assertThat(result.success()).isTrue();
            assertThat(result.value()).isPresent();
        }

        @Test
        void throwingProviderIsRefusedNotThrown() {
            NativeBridgeProvider throwing = new NativeBridgeProvider() {
                @Override
                public String name() {
                    return "boom";
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public NativeResult loadLibrary(String libraryName) {
                    throw new IllegalStateException("native failure");
                }

                @Override
                public NativeResult unloadLibrary(String libraryName) {
                    return NativeResult.refused("n/a");
                }

                @Override
                public NativeResult findSymbol(String libraryName, String symbolName,
                        NativeSymbol.Kind kind) {
                    return NativeResult.refused("n/a");
                }

                @Override
                public NativeResult invoke(NativeSymbol symbol, Object... arguments) {
                    return NativeResult.refused("n/a");
                }

                @Override
                public List<NativeLibraryHandle> loadedLibraries() {
                    return List.of();
                }
            };
            registry.register(throwing);

            NativeResult result = registry.loadLibrary("boom", "lib");

            assertThat(result.success()).isFalse();
            assertThat(result.reason()).contains("failed");
        }

        @Test
        void findSymbolAndInvokeAreGated() {
            registry.register(provider("ffm", true));
            NativeSymbol symbol = new NativeSymbol("lib", "fn", NativeSymbol.Kind.FUNCTION);

            NativeResult found = registry.findSymbol("ffm", "lib", "fn",
                    NativeSymbol.Kind.FUNCTION);
            NativeResult invoked = registry.invoke("ffm", symbol, 1, 2);

            assertThat(found.success()).isTrue();
            assertThat(invoked.success()).isTrue();
            assertThat(invoked.value()).contains(42);
        }

        @Test
        void loadedLibrariesForUnknownProviderIsEmpty() {
            assertThat(registry.loadedLibraries("missing")).isEmpty();
        }
    }

    @Nested
    class ValueValidation {

        @Test
        void symbolValidationRejectsBlankFields() {
            assertThatThrownBy(() -> new NativeSymbol("", "fn", NativeSymbol.Kind.FUNCTION))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NativeSymbol("lib", "", NativeSymbol.Kind.FUNCTION))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NativeSymbol("lib", "fn", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void libraryHandleValidationRejectsBadValues() {
            assertThatThrownBy(() -> new NativeLibraryHandle("", true, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NativeLibraryHandle("lib", true, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nativeResultFactoriesBehave() {
            NativeResult ok = NativeResult.ok("v");
            NativeResult refused = NativeResult.refused("why");

            assertThat(ok.success()).isTrue();
            assertThat(ok.value()).contains("v");
            assertThat(refused.success()).isFalse();
            assertThat(refused.reason()).isEqualTo("why");
            assertThat(refused.value()).isEmpty();
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesNativeBridgeRegistry() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getNativeBridgeRegistry()).isNotNull();
            assertThat(runtime.getNativeBridgeRegistry().hasAvailableProvider()).isFalse();
        }

        @Test
        void runtimeShutdownClearsProviders() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.getNativeBridgeRegistry().register(provider("ffm", true));

            runtime.shutdown();

            assertThat(runtime.getNativeBridgeRegistry().getProviderNames()).isEmpty();
        }
    }
}
