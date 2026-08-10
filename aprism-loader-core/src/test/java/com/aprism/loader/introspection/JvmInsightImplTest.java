package com.aprism.loader.introspection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.introspection.ClassStats;
import com.aprism.api.introspection.CompilationSummary;
import com.aprism.api.introspection.GcSummary;
import com.aprism.api.introspection.HeapSummary;
import com.aprism.api.introspection.ThreadInsight;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the JVM introspection surface (v26.4-Alpha.4). The
 * implementation reads live MXBeans, so assertions verify sane live
 * values rather than exact numbers.
 *
 * @author BlockConnect@StarsailsClover
 */
class JvmInsightImplTest {

    private final JvmInsightImpl insight = new JvmInsightImpl();

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class Threads {

        @Test
        void listsLiveThreadsIncludingCurrent() {
            List<ThreadInsight> threads = insight.threads();

            assertThat(threads).isNotEmpty();
            long currentId = Thread.currentThread().threadId();
            assertThat(threads).anySatisfy(t -> assertThat(t.threadId()).isEqualTo(currentId));
            assertThat(threads).allSatisfy(t -> {
                assertThat(t.name()).isNotBlank();
                assertThat(t.state()).isNotBlank();
                assertThat(t.stackDepth()).isGreaterThanOrEqualTo(0);
            });
        }

        @Test
        void topFramesAreBounded() {
            List<ThreadInsight> threads = insight.threads();

            assertThat(threads).allSatisfy(t -> assertThat(t.topFrames().size())
                    .isLessThanOrEqualTo(16));
        }
    }

    @Nested
    class Classes {

        @Test
        void reportsLiveClassStats() {
            ClassStats stats = insight.classStats();

            assertThat(stats.loadedClassCount()).isGreaterThan(0);
            assertThat(stats.totalLoadedClassCount())
                    .isGreaterThanOrEqualTo(stats.loadedClassCount());
            assertThat(stats.unloadedClassCount()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    class Memory {

        @Test
        void reportsHeapUsage() {
            HeapSummary heap = insight.heap();

            assertThat(heap.heapUsed()).isGreaterThan(0);
            assertThat(heap.heapCommitted()).isGreaterThanOrEqualTo(heap.heapUsed());
            assertThat(heap.nonHeapUsed()).isGreaterThan(0);
        }
    }

    @Nested
    class Gc {

        @Test
        void reportsCollectorsWithValidNames() {
            List<GcSummary> collectors = insight.gcCollectors();

            assertThat(collectors).allSatisfy(gc -> {
                assertThat(gc.name()).isNotBlank();
                assertThat(gc.collectionCount()).isGreaterThanOrEqualTo(-1);
            });
        }
    }

    @Nested
    class Compilation {

        @Test
        void reportsJitState() {
            CompilationSummary compilation = insight.compilation();

            // Test JVMs run with a JIT; contract: either available with a
            // name, or explicitly unavailable.
            if (compilation.jitAvailable()) {
                assertThat(compilation.compilerName()).isNotBlank();
            } else {
                assertThat(compilation.compilerName()).isNull();
            }
        }
    }

    @Nested
    class VmIdentity {

        @Test
        void reportsUptimeAndIdentity() {
            assertThat(insight.uptimeMs()).isGreaterThan(0);
            assertThat(insight.vmName()).isNotBlank();
            assertThat(insight.vmVendor()).isNotBlank();
            assertThat(insight.javaVersion()).isNotBlank();
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesJvmInsight() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getJvmInsight()).isNotNull();
            assertThat(runtime.getJvmInsight().heap().heapUsed()).isGreaterThan(0);
        }

        @Test
        void runtimeInsightIsStableAcrossCalls() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getJvmInsight()).isSameAs(runtime.getJvmInsight());
        }
    }
}
