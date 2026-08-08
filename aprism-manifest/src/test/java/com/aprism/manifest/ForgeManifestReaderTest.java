package com.aprism.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.aprism.manifest.fallback.ForgeManifestReader;

/**
 * Tests for {@link ForgeManifestReader} covering the real-world TOML shapes
 * legacy Forge mods use: {@code [[mods]]} descriptor, {@code [[mixins]]}
 * config references, {@code [[dependencies.<modid>]]} entries with the
 * Forge-specific {@code mandatory} flag, triple-quoted multi-line description
 * strings, and the regression where a dependency entry's {@code modId}
 * previously overrode the mod's own id under the simple projection.
 *
 * <p>The primary fixture mirrors a genuine Forge mod manifest (JEI-style).
 *
 * @author BlockConnect@StarsailsClover
 */
class ForgeManifestReaderTest {

    /** Mirrors the real Forge mods.toml structure (JEI-style). */
    private static final String FORGE_TOML = """
            modLoader="javafml"
            loaderVersion="[47,)"
            license="MIT"

            [[mods]]
            modId="jei"
            version="11.6.0.1015"
            displayName="Just Enough Items"
            authors="mezz"
            description='''
            JEI is an item and recipe viewing library.
            '''

            [[dependencies.jei]]
                modId="forge"
                mandatory=true
                versionRange="[47,)"
                ordering="NONE"
                side="BOTH"

            [[dependencies.jei]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.20,1.21)"
                ordering="NONE"
                side="BOTH"
            """;

    @Test
    void projectsPrimaryModIdAndVersion() throws Exception {
        AprismManifest m = ForgeManifestReader.parse(FORGE_TOML);
        // Regression: modId must come from [[mods]], not from a dependency entry
        assertThat(m.id()).isEqualTo("jei");
        assertThat(m.version()).isEqualTo("11.6.0.1015");
        assertThat(m.displayName()).isEqualTo("Just Enough Items");
    }

    @Test
    void projectsMandatoryDependencies() throws Exception {
        AprismManifest m = ForgeManifestReader.parse(FORGE_TOML);
        assertThat(m.depends())
                .containsEntry("forge", "[47,)")
                .containsEntry("minecraft", "[1.20,1.21)");
    }

    @Test
    void optionalDependenciesAreExcluded() throws Exception {
        AprismManifest m = ForgeManifestReader.parse("""
                [[mods]]
                modId="example"
                version="1.0.0"

                [[dependencies.example]]
                modId="optionalapi"
                mandatory=false
                versionRange="[1,2)"
                """);
        assertThat(m.depends()).doesNotContainKey("optionalapi");
    }

    @Test
    void mandatoryDefaultsToTrueWhenAbsent() throws Exception {
        AprismManifest m = ForgeManifestReader.parse("""
                [[mods]]
                modId="example"
                version="1.0.0"

                [[dependencies.example]]
                modId="forge"
                versionRange="[47,)"
                """);
        assertThat(m.depends()).containsEntry("forge", "[47,)");
    }

    @Test
    void projectsLicenseIntoCustom() throws Exception {
        AprismManifest m = ForgeManifestReader.parse(FORGE_TOML);
        assertThat(m.custom()).containsEntry("license", "MIT");
    }

    @Test
    void toleratesTripleQuotedMultilineDescription() throws Exception {
        // The multi-line description must not break parsing or leak into the id
        AprismManifest m = ForgeManifestReader.parse(FORGE_TOML);
        assertThat(m.id()).isEqualTo("jei");
    }

    @Test
    void toleratesIndentedDependencyEntries() throws Exception {
        // Real Forge mods.toml files indent dependency keys (see FORGE_TOML)
        AprismManifest m = ForgeManifestReader.parse(FORGE_TOML);
        assertThat(m.depends()).hasSize(2);
    }

    @Test
    void rejectsTomlWithoutModsTable() {
        assertThatThrownBy(() -> ForgeManifestReader.parse("""
                modLoader = "javafml"
                license = "MIT"
                """))
                .isInstanceOf(ManifestException.ManifestParseException.class);
    }

    @Test
    void rejectsModsTableMissingModId() {
        assertThatThrownBy(() -> ForgeManifestReader.parse("""
                [[mods]]
                version = "1.0.0"
                displayName = "x"
                """))
                .isInstanceOf(ManifestException.ManifestParseException.class);
    }
}
