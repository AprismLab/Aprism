package com.aprism.loader.hardware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.hardware.CpuFeatures;
import com.aprism.api.hardware.HardwareInsight;
import com.aprism.api.hardware.HardwareProbe;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the performance &amp; hardware fusion reference
 * (v26.4-Alpha.7): default probe guarantees, registry activation, value
 * validation, and runtime wiring. All values are advisory; tests assert
 * contract shape, not machine-specific numbers.
 *
 * @author BlockConnect@StarsailsClover
 */
class HardwareRegistryTest {

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class DefaultProbe {

        @Test
        void reportsProvenArchitectureAndProcessors() {
            HardwareInsight insight = new DefaultHardwareProbe().probe();

            CpuFeatures cpu = insight.cpuFeatures();
            assertThat(cpu.architecture()).isNotBlank();
            assertThat(cpu.osName()).isNotBlank();
            assertThat(cpu.availableProcessors()).isGreaterThanOrEqualTo(1);
        }

        @Test
        void reportsUnknownSentinelsForCacheAndNuma() {
            HardwareInsight insight = new DefaultHardwareProbe().probe();

            assertThat(insight.cacheLineBytes()).isEqualTo(-1);
            assertThat(insight.numaNodeCount()).isEqualTo(-1);
            assertThat(insight.cacheLineKnown()).isFalse();
            assertThat(insight.numaKnown()).isFalse();
        }

        @Test
        void featureTokensAreIsaGuaranteesOnly() {
            HardwareInsight insight = new DefaultHardwareProbe().probe();
            CpuFeatures cpu = insight.cpuFeatures();
            String arch = cpu.architecture().toLowerCase();

            // Only proven tokens: sse2 on amd64/x86_64, neon on aarch64/arm64.
            for (String token : cpu.featureTokens()) {
                assertThat(token).isIn("sse2", "neon");
            }
            if (arch.contains("amd64") || arch.contains("x86_64")) {
                assertThat(cpu.hasFeature("sse2")).isTrue();
            }
        }

        @Test
        void hasFeatureIsCaseInsensitive() {
            CpuFeatures cpu = new CpuFeatures("amd64", "test", 1, Set.of("sse2"));

            assertThat(cpu.hasFeature("SSE2")).isTrue();
            assertThat(cpu.hasFeature("avx512f")).isFalse();
            assertThat(cpu.hasFeature(null)).isFalse();
        }
    }

    @Nested
    class RegistryActivation {

        @Test
        void defaultProbeIsAlwaysActive() {
            HardwareRegistry registry = new HardwareRegistry();

            assertThat(registry.getActiveProbeName()).isEqualTo("default");
            assertThat(registry.getProbeNames()).containsExactly("default");
        }

        @Test
        void registerAndActivateDeepProbe() {
            HardwareRegistry registry = new HardwareRegistry();
            registry.register(new HardwareProbe() {
                @Override
                public String name() {
                    return "deep";
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public HardwareInsight probe() {
                    return new HardwareInsight(
                            new CpuFeatures("amd64", "test", 2, Set.of("sse2", "avx2")), 64, 1);
                }
            });

            assertThat(registry.activate("deep")).isTrue();
            assertThat(registry.getActiveProbeName()).isEqualTo("deep");
            HardwareInsight insight = registry.insight();
            assertThat(insight.cacheLineBytes()).isEqualTo(64);
            assertThat(insight.cacheLineKnown()).isTrue();
            assertThat(insight.numaNodeCount()).isEqualTo(1);
            assertThat(insight.cpuFeatures().hasFeature("avx2")).isTrue();
        }

        @Test
        void duplicateNameIsRejected() {
            HardwareRegistry registry = new HardwareRegistry();

            assertThatThrownBy(() -> registry.register(new DefaultHardwareProbe()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        void activatingUnknownProbeFails() {
            HardwareRegistry registry = new HardwareRegistry();

            assertThat(registry.activate("missing")).isFalse();
            assertThat(registry.getActiveProbeName()).isEqualTo("default");
        }

        @Test
        void throwingProbeFallsBackToDefault() {
            HardwareRegistry registry = new HardwareRegistry();
            registry.register(new HardwareProbe() {
                @Override
                public String name() {
                    return "boom";
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public HardwareInsight probe() {
                    throw new IllegalStateException("probe failure");
                }
            });
            registry.activate("boom");

            HardwareInsight insight = registry.insight();
            assertThat(insight).isNotNull();
            assertThat(insight.cacheLineBytes()).isEqualTo(-1);
        }

        @Test
        void clearResetsToDefault() {
            HardwareRegistry registry = new HardwareRegistry();
            registry.register(new HardwareProbe() {
                @Override
                public String name() {
                    return "deep";
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public HardwareInsight probe() {
                    return new HardwareInsight(
                            new CpuFeatures("amd64", "test", 2, Set.of()), 64, 1);
                }
            });
            registry.activate("deep");

            registry.clear();

            assertThat(registry.getActiveProbeName()).isEqualTo("default");
            assertThat(registry.getProbeNames()).containsExactly("default");
        }
    }

    @Nested
    class ValueValidation {

        @Test
        void cpuFeaturesRejectsBadValues() {
            assertThatThrownBy(() -> new CpuFeatures("", "test", 1, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CpuFeatures("amd64", "", 1, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CpuFeatures("amd64", "test", 0, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void insightRejectsBadValues() {
            CpuFeatures cpu = new CpuFeatures("amd64", "test", 1, Set.of());

            assertThatThrownBy(() -> new HardwareInsight(null, -1, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HardwareInsight(cpu, 0, -1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HardwareInsight(cpu, -1, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesHardwareRegistry() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getHardwareRegistry()).isNotNull();
            assertThat(runtime.getHardwareRegistry().insight()).isNotNull();
        }

        @Test
        void runtimeShutdownResetsHardwareRegistry() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.getHardwareRegistry().register(new HardwareProbe() {
                @Override
                public String name() {
                    return "deep";
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public HardwareInsight probe() {
                    return new HardwareInsight(
                            new CpuFeatures("amd64", "test", 2, Set.of()), 64, 1);
                }
            });
            runtime.getHardwareRegistry().activate("deep");

            runtime.shutdown();

            assertThat(runtime.getHardwareRegistry().getActiveProbeName()).isEqualTo("default");
        }
    }
}
