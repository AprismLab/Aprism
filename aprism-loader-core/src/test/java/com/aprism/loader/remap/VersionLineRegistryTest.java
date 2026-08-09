package com.aprism.loader.remap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Tests the {@link VersionLineRegistry}: the JE 1.20 .. 26.2 version-line
 * foundation (v26.1-Alpha.7, goal #1).
 *
 * @author BlockConnect@StarsailsClover
 */
class VersionLineRegistryTest {

    @Test
    void resolves120PatchToRemappedJava17() {
        Optional<VersionLineEntry> entry = VersionLineRegistry.resolve("1.20.4");
        assertThat(entry).isPresent();
        assertThat(entry.get().profile()).isEqualTo(McProfile.REMAPPED);
        assertThat(entry.get().javaBaseline()).isEqualTo(17);
        assertThat(entry.get().mappingsSource()).isEqualTo("intermediary");
        assertThat(entry.get().requiresRemap()).isTrue();
    }

    @Test
    void resolves121PatchToRemappedJava21() {
        Optional<VersionLineEntry> entry = VersionLineRegistry.resolve("1.21.4");
        assertThat(entry).isPresent();
        assertThat(entry.get().profile()).isEqualTo(McProfile.REMAPPED);
        assertThat(entry.get().javaBaseline()).isEqualTo(21);
        assertThat(entry.get().requiresRemap()).isTrue();
    }

    @Test
    void resolves262ToNoRemapJava25() {
        Optional<VersionLineEntry> entry = VersionLineRegistry.resolve("26.2");
        assertThat(entry).isPresent();
        assertThat(entry.get().profile()).isEqualTo(McProfile.NO_REMAP);
        assertThat(entry.get().javaBaseline()).isEqualTo(25);
        assertThat(entry.get().mappingsSource()).isEqualTo("none");
        assertThat(entry.get().requiresRemap()).isFalse();
    }

    @Test
    void resolves26MinorPrefixToNoRemap() {
        Optional<VersionLineEntry> entry = VersionLineRegistry.resolve("26.1.2");
        assertThat(entry).isPresent();
        assertThat(entry.get().profile()).isEqualTo(McProfile.NO_REMAP);
    }

    @Test
    void rejectsVersionBelowSupportedLine() {
        assertThat(VersionLineRegistry.resolve("1.19.4")).isEmpty();
        assertThat(VersionLineRegistry.resolve("1.12.2")).isEmpty();
        assertThat(VersionLineRegistry.resolve("1.8.9")).isEmpty();
    }

    @Test
    void rejectsNullOrBlankOrUnparseable() {
        assertThat(VersionLineRegistry.resolve(null)).isEmpty();
        assertThat(VersionLineRegistry.resolve("")).isEmpty();
        assertThat(VersionLineRegistry.resolve("   ")).isEmpty();
        assertThat(VersionLineRegistry.resolve("not-a-version")).isEmpty();
    }

    @Test
    void withinSupportedLineBoundaries() {
        assertThat(VersionLineRegistry.isWithinSupportedLine("1.20")).isTrue();
        assertThat(VersionLineRegistry.isWithinSupportedLine("1.21.4")).isTrue();
        assertThat(VersionLineRegistry.isWithinSupportedLine("26.2")).isTrue();
        // Above the explicit window: resolves but is reported as outside it
        assertThat(VersionLineRegistry.isWithinSupportedLine("26.3")).isFalse();
        // Below the line
        assertThat(VersionLineRegistry.isWithinSupportedLine("1.19.4")).isFalse();
    }

    @Test
    void supportedLineHasThreeSegments() {
        assertThat(VersionLineRegistry.supportedLine()).hasSize(3);
        assertThat(VersionLineRegistry.supportedLine().get(0).versionId()).isEqualTo("1.20");
        assertThat(VersionLineRegistry.supportedLine().get(1).versionId()).isEqualTo("1.21");
        assertThat(VersionLineRegistry.supportedLine().get(2).versionId()).isEqualTo("26");
    }

    @Test
    void describeLineMatchesConstants() {
        assertThat(VersionLineRegistry.describeLine())
                .isEqualTo(VersionLineRegistry.LINE_START + " .. " + VersionLineRegistry.LINE_END);
    }

    @Test
    void compareVersionsOrdersNumerically() {
        assertThat(VersionLineRegistry.compareVersions("1.20.4", "1.21")).isNegative();
        assertThat(VersionLineRegistry.compareVersions("26.2", "26.2")).isZero();
        assertThat(VersionLineRegistry.compareVersions("26.3", "26.2")).isPositive();
        assertThat(VersionLineRegistry.compareVersions("1.21", "26.2")).isNegative();
    }
}
