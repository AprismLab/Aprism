package com.aprism.loader.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.rendering.RenderBackend;
import com.aprism.api.rendering.RenderCapability;
import com.aprism.api.rendering.RenderingProvider;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the rendering pipeline innovation reference (v26.3-Alpha.5,
 * goal #9; experimental): {@link RenderBackend} parsing,
 * {@link RenderCapability} semantics, {@link RenderingRegistry}
 * registration and capability-gated queries, and the runtime wiring
 * ({@code AprismRuntime.getRenderingRegistry()}).
 *
 * @author BlockConnect@StarsailsClover
 */
class RenderingRegistryTest {

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    /** Configurable stub rendering provider. */
    static final class StubProvider implements RenderingProvider {
        private final String name;
        private final boolean ready;
        private final List<RenderBackend> backends;
        private final boolean throwOnQuery;

        StubProvider(String name, boolean ready, List<RenderBackend> backends,
                     boolean throwOnQuery) {
            this.name = name;
            this.ready = ready;
            this.backends = backends;
            this.throwOnQuery = throwOnQuery;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<RenderBackend> supportedBackends() {
            return backends;
        }

        @Override
        public RenderCapability queryCapability(RenderBackend backend) {
            if (throwOnQuery) {
                throw new RuntimeException("synthetic query failure");
            }
            return new RenderCapability(backend,
                    List.of("ray-query", "compute"), 16384);
        }

        @Override
        public boolean isReady() {
            return ready;
        }
    }

    @Nested
    class BackendParsing {

        @Test
        void knownBackendTokensResolve() {
            assertThat(RenderBackend.parse("vulkan")).isEqualTo(RenderBackend.VULKAN);
            assertThat(RenderBackend.parse("METAL")).isEqualTo(RenderBackend.METAL);
            assertThat(RenderBackend.parse("dx12")).isEqualTo(RenderBackend.DX12);
            assertThat(RenderBackend.parse("opengl")).isEqualTo(RenderBackend.OPENGL);
        }

        @Test
        void unknownTokenRejected() {
            assertThatThrownBy(() -> RenderBackend.parse("mantle"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown render backend");
            assertThatThrownBy(() -> RenderBackend.parse(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void capabilityFeatureLookup() {
            RenderCapability capability = new RenderCapability(
                    RenderBackend.VULKAN, List.of("ray-query", "mesh-shader"), 16384);

            assertThat(capability.supports("ray-query")).isTrue();
            assertThat(capability.supports("compute")).isFalse();
            assertThat(capability.maxTextureSize()).isEqualTo(16384);
        }
    }

    @Nested
    class Registration {

        @Test
        void registerAndLookup() {
            RenderingRegistry registry = new RenderingRegistry();
            RenderingProvider provider = new StubProvider("refract-render", true,
                    List.of(RenderBackend.VULKAN), false);

            registry.register(provider);

            assertThat(registry.get("refract-render")).contains(provider);
            assertThat(registry.getProviderNames()).containsExactly("refract-render");
        }

        @Test
        void duplicateNameRejected() {
            RenderingRegistry registry = new RenderingRegistry();
            registry.register(new StubProvider("dup", true, List.of(), false));

            assertThatThrownBy(() -> registry.register(new StubProvider("dup", true, List.of(), false)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        void readyProviderNamesFiltered() {
            RenderingRegistry registry = new RenderingRegistry();
            registry.register(new StubProvider("ready", true, List.of(), false));
            registry.register(new StubProvider("broken", false, List.of(), false));

            assertThat(registry.getReadyProviderNames()).containsExactly("ready");
        }
    }

    @Nested
    class CapabilityGating {

        @Test
        void queryUnknownProviderEmpty() {
            RenderingRegistry registry = new RenderingRegistry();

            assertThat(registry.queryCapability("ghost", RenderBackend.VULKAN)).isEmpty();
        }

        @Test
        void queryUnreadyProviderEmpty() {
            RenderingRegistry registry = new RenderingRegistry();
            registry.register(new StubProvider("broken", false,
                    List.of(RenderBackend.VULKAN), false));

            assertThat(registry.queryCapability("broken", RenderBackend.VULKAN)).isEmpty();
        }

        @Test
        void queryUnsupportedBackendEmpty() {
            RenderingRegistry registry = new RenderingRegistry();
            registry.register(new StubProvider("vulkan-only", true,
                    List.of(RenderBackend.VULKAN), false));

            assertThat(registry.queryCapability("vulkan-only", RenderBackend.DX12)).isEmpty();
        }

        @Test
        void querySupportedBackendReturnsCapability() {
            RenderingRegistry registry = new RenderingRegistry();
            registry.register(new StubProvider("multi", true,
                    List.of(RenderBackend.VULKAN, RenderBackend.METAL), false));

            assertThat(registry.queryCapability("multi", RenderBackend.METAL))
                    .isPresent()
                    .hasValueSatisfying(cap -> {
                        assertThat(cap.backend()).isEqualTo(RenderBackend.METAL);
                        assertThat(cap.supports("ray-query")).isTrue();
                    });
        }

        @Test
        void throwingProviderReturnsEmptyNotException() {
            RenderingRegistry registry = new RenderingRegistry();
            registry.register(new StubProvider("bad", true,
                    List.of(RenderBackend.VULKAN), true));

            assertThat(registry.queryCapability("bad", RenderBackend.VULKAN)).isEmpty();
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesRenderingRegistry() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");

            RenderingRegistry registry = runtime.getRenderingRegistry();
            assertThat(registry).isNotNull();
            registry.register(new StubProvider("stub", true,
                    List.of(RenderBackend.VULKAN), false));
            assertThat(registry.get("stub")).isPresent();
        }

        @Test
        void renderingRegistryClearedOnShutdown() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");
            RenderingRegistry registry = runtime.getRenderingRegistry();
            registry.register(new StubProvider("stub", true, List.of(), false));

            runtime.shutdown();

            assertThat(runtime.getRenderingRegistry().getProviderNames()).isEmpty();
        }
    }
}
