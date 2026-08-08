package com.aprism.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.ModContainer;

/**
 * Tests for {@link DependencyResolver} environment-dependency handling and
 * SemVer-backed range matching. Real Fabric mods declare dependencies on the
 * runtime environment ({@code fabricloader}, {@code minecraft}, {@code java})
 * with Fabric-style ranges (space-AND, two-segment versions); those must be
 * validated against the environment, never treated as missing mods.
 *
 * @author BlockConnect@StarsailsClover
 */
class DependencyResolverTest {

    private static AprismManifest manifest(String id, String version, Map<String, String> depends) {
        return new AprismManifest(1, id, version, id, "test", "*",
                Map.of(), List.of(), depends, Map.of(), null, List.of(), Map.of());
    }

    @Nested
    class EnvironmentDependencies {

        @Test
        void environmentDependencyResolvedAgainstEnvironment() throws Exception {
            // ferritecore-style: fabricloader + minecraft env deps
            AprismManifest m = manifest("ferritecore", "7.1.3", Map.of(
                    "fabricloader", ">=0.14.21",
                    "minecraft", ">=1.21.4 <1.22"));

            List<ModContainer> result = new DependencyResolver().resolve(List.of(m), Map.of(
                    "fabricloader", "0.16.14",
                    "minecraft", "1.21.4",
                    "java", "21"));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("ferritecore");
        }

        @Test
        void environmentVersionBelowRangeThrows() {
            AprismManifest m = manifest("newmod", "1.0.0", Map.of(
                    "fabricloader", ">=0.20.0"));

            assertThatThrownBy(() -> new DependencyResolver().resolve(List.of(m), Map.of(
                    "fabricloader", "0.16.14")))
                    .isInstanceOf(DependencyResolutionException.class)
                    .hasMessageContaining("fabricloader");
        }

        @Test
        void missingModDependencyStillThrows() {
            AprismManifest m = manifest("somemod", "1.0.0", Map.of(
                    "othermod", "*"));

            assertThatThrownBy(() -> new DependencyResolver().resolve(List.of(m), Map.of(
                    "fabricloader", "0.16.14")))
                    .isInstanceOf(DependencyResolutionException.class)
                    .hasMessageContaining("othermod");
        }

        @Test
        void mixedEnvAndModDependenciesResolve() throws Exception {
            AprismManifest base = manifest("basemod", "2.0.0", Map.of());
            AprismManifest dependent = manifest("topmod", "1.0.0", Map.of(
                    "basemod", "*",
                    "fabricloader", ">=0.14.21"));

            List<ModContainer> result = new DependencyResolver().resolve(
                    List.of(dependent, base), Map.of("fabricloader", "0.16.14"));

            assertThat(result).hasSize(2);
            // base must come before dependent
            assertThat(result.get(0).getId()).isEqualTo("basemod");
            assertThat(result.get(1).getId()).isEqualTo("topmod");
        }
    }

    @Nested
    class SemVerRangeMatching {

        @Test
        void comparatorRangesMatch() throws Exception {
            AprismManifest m = manifest("mod", "1.5.0", Map.of("dep", ">=1.0.0"));
            AprismManifest dep = manifest("dep", "1.5.0", Map.of());

            List<ModContainer> result = new DependencyResolver().resolve(List.of(m, dep), Map.of());
            assertThat(result).hasSize(2);
        }

        @Test
        void comparatorRangeViolationThrows() {
            AprismManifest m = manifest("mod", "1.5.0", Map.of("dep", ">=2.0.0"));
            AprismManifest dep = manifest("dep", "1.5.0", Map.of());

            assertThatThrownBy(() -> new DependencyResolver().resolve(List.of(m, dep), Map.of()))
                    .isInstanceOf(DependencyResolutionException.class);
        }

        @Test
        void twoSegmentVersionsMatch() throws Exception {
            // ">=26.2" style two-segment versions
            AprismManifest m = manifest("mod", "1.0.0", Map.of("dep", ">=26.2"));
            AprismManifest dep = manifest("dep", "26.2.0", Map.of());

            List<ModContainer> result = new DependencyResolver().resolve(List.of(m, dep), Map.of());
            assertThat(result).hasSize(2);
        }
    }
}
