package com.aprism.loader.remap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link McProfile}: the pre-26.1 vs 26.1+ boundary that decides
 * whether Intermediary remapping is required.
 *
 * @author BlockConnect@StarsailsClover
 */
class McProfileTest {

    @Test
    void oneTwentyOneFourIsRemapped() {
        assertThat(McProfile.of("1.21.4")).isEqualTo(McProfile.REMAPPED);
        assertThat(McProfile.of("1.21.4").requiresRemap()).isTrue();
    }

    @Test
    void legacyVersionsAreRemapped() {
        assertThat(McProfile.of("1.16.5")).isEqualTo(McProfile.REMAPPED);
        assertThat(McProfile.of("1.20.1")).isEqualTo(McProfile.REMAPPED);
        assertThat(McProfile.of("1.21.10")).isEqualTo(McProfile.REMAPPED);
    }

    @Test
    void twentySixAndLaterAreNoRemap() {
        assertThat(McProfile.of("26.2")).isEqualTo(McProfile.NO_REMAP);
        assertThat(McProfile.of("26.1.2")).isEqualTo(McProfile.NO_REMAP);
        assertThat(McProfile.of("27.0")).isEqualTo(McProfile.NO_REMAP);
        assertThat(McProfile.of("26.2").requiresRemap()).isFalse();
    }

    @Test
    void theTwentySixOneBoundary() {
        // 26.1 is the first unobfuscated release; 26.0 would still be remapped
        assertThat(McProfile.of("26.1")).isEqualTo(McProfile.NO_REMAP);
        assertThat(McProfile.of("25.9")).isEqualTo(McProfile.REMAPPED);
    }

    @Test
    void unparseableDefaultsToNoRemap() {
        assertThat(McProfile.of(null)).isEqualTo(McProfile.NO_REMAP);
        assertThat(McProfile.of("")).isEqualTo(McProfile.NO_REMAP);
        assertThat(McProfile.of("unknown")).isEqualTo(McProfile.NO_REMAP);
        assertThat(McProfile.of("abc.def")).isEqualTo(McProfile.NO_REMAP);
    }

    @Test
    void selectRemapperNoRemapReturnsIdentity() {
        Remapper sentinel = TinyRemapper.intermediaryToOfficial(mappings());
        Remapper selected = McProfile.NO_REMAP.selectRemapper(sentinel);
        assertThat(selected).isSameAs(Remapper.noop());
    }

    @Test
    void selectRemapperRemappedReturnsSuppliedRemapper() {
        Remapper sentinel = TinyRemapper.intermediaryToOfficial(mappings());
        Remapper selected = McProfile.REMAPPED.selectRemapper(sentinel);
        assertThat(selected).isSameAs(sentinel);
    }

    private static TinyMappings mappings() {
        try {
            return TinyMappings.parse(new java.io.StringReader(
                    "tiny\t2\t0\tofficial\tintermediary\nc\ta\tb\n"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
