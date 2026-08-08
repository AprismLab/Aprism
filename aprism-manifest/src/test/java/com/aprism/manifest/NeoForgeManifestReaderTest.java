package com.aprism.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.aprism.manifest.fallback.NeoForgeManifestReader;

/**
 * Tests for {@link NeoForgeManifestReader} covering the real-world TOML shapes
 * NeoForge mods use: {@code [[mods]]} descriptor, {@code [[mixins]]} config
 * references, {@code [[dependencies.<modid>]]} entries, triple-quoted
 * multi-line description strings, and the bug regression where a dependency
 * entry's {@code modId} previously overrode the mod's own id.
 *
 * <p>The primary fixture mirrors the genuine FerriteCore NeoForge manifest.
 *
 * @author BlockConnect@StarsailsClover
 */
class NeoForgeManifestReaderTest {

    /** Mirrors the real FerriteCore 7.1.3 neoforge.mods.toml structure. */
    private static final String FERRITECORE_TOML = """
            modLoader = "javafml"
            loaderVersion = "[1,)"

            license = "MIT"
            [[mods]]
            modId = "ferritecore"
            version = "7.1.3"
            displayName = "Ferrite Core"
            authors = "malte0811"
            description = '''
            Reduces memory usage.
            '''
            logoFile = "logo.png"
            [[dependencies.ferritecore]]
            modId = "neoforge"
            type = "required"
            versionRange = "[21.4.156,)"
            ordering = "NONE"
            side = "BOTH"
            [[dependencies.ferritecore]]
            modId = "minecraft"
            type = "required"
            versionRange = "[1.21.4,1.22)"
            ordering = "NONE"
            side = "BOTH"

            [[mixins]]
            config="ferritecore.predicates.mixin.json"
            [[mixins]]
            config="ferritecore.fastmap.mixin.json"
            """;

    @Test
    void projectsPrimaryModIdAndVersion() throws Exception {
        AprismManifest m = NeoForgeManifestReader.parse(FERRITECORE_TOML);
        // Regression: modId must come from [[mods]], not from a dependency entry
        assertThat(m.id()).isEqualTo("ferritecore");
        assertThat(m.version()).isEqualTo("7.1.3");
        assertThat(m.displayName()).isEqualTo("Ferrite Core");
    }

    @Test
    void projectsDependenciesFromDependencyTables() throws Exception {
        AprismManifest m = NeoForgeManifestReader.parse(FERRITECORE_TOML);
        assertThat(m.depends())
                .containsEntry("neoforge", "[21.4.156,)")
                .containsEntry("minecraft", "[1.21.4,1.22)");
    }

    @Test
    void projectsMixinConfigs() throws Exception {
        AprismManifest m = NeoForgeManifestReader.parse(FERRITECORE_TOML);
        assertThat(m.mixins())
                .containsExactly("ferritecore.predicates.mixin.json",
                        "ferritecore.fastmap.mixin.json");
    }

    @Test
    void toleratesTripleQuotedMultilineDescription() throws Exception {
        // The multi-line description must not break parsing or leak into the id
        AprismManifest m = NeoForgeManifestReader.parse(FERRITECORE_TOML);
        assertThat(m.id()).isEqualTo("ferritecore");
    }

    @Test
    void projectsLicenseIntoCustom() throws Exception {
        AprismManifest m = NeoForgeManifestReader.parse(FERRITECORE_TOML);
        assertThat(m.custom()).containsEntry("license", "MIT");
    }

    @Test
    void rejectsTomlWithoutModsTable() {
        assertThatThrownBy(() -> NeoForgeManifestReader.parse("""
                modLoader = "javafml"
                license = "MIT"
                """))
                .isInstanceOf(ManifestException.ManifestParseException.class);
    }

    @Test
    void rejectsModsTableMissingModId() {
        assertThatThrownBy(() -> NeoForgeManifestReader.parse("""
                [[mods]]
                version = "1.0.0"
                displayName = "x"
                """))
                .isInstanceOf(ManifestException.ManifestParseException.class);
    }
}
