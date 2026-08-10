package com.aprism.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the manifest settings schema (v26.2-Alpha.3, goal #7 part 2):
 * type parsing ({@link SettingType}) and declaration reading
 * ({@link SettingsDeclarationReader}).
 *
 * @author BlockConnect@StarsailsClover
 */
class SettingsDeclarationTest {

    @Nested
    class TypeParsing {
        @Test
        void knownTypeNamesResolve() {
            assertThat(SettingType.fromName("string")).isEqualTo(SettingType.STRING);
            assertThat(SettingType.fromName("integer")).isEqualTo(SettingType.INTEGER);
            assertThat(SettingType.fromName("int")).isEqualTo(SettingType.INTEGER);
            assertThat(SettingType.fromName("double")).isEqualTo(SettingType.DOUBLE);
            assertThat(SettingType.fromName("boolean")).isEqualTo(SettingType.BOOLEAN);
            assertThat(SettingType.fromName("enum")).isEqualTo(SettingType.ENUM);
        }

        @Test
        void caseInsensitiveAndPadded() {
            assertThat(SettingType.fromName("  INTEGER ")).isEqualTo(SettingType.INTEGER);
            assertThat(SettingType.fromName("Boolean")).isEqualTo(SettingType.BOOLEAN);
        }

        @Test
        void unknownFallsBackToString() {
            assertThat(SettingType.fromName("banana")).isEqualTo(SettingType.STRING);
            assertThat(SettingType.fromName(null)).isEqualTo(SettingType.STRING);
            assertThat(SettingType.fromName("")).isEqualTo(SettingType.STRING);
        }
    }

    @Nested
    class DeclarationReading {

        private AprismManifest manifestWithSettings(Map<String, Object> settings) {
            Map<String, Object> aprism = Map.of("settings", settings);
            return new AprismManifest(1, "examplemod", "1.0.0", "Example", "", "*",
                    Map.of(), List.of(), Map.of(), Map.of(), null, List.of(),
                    Map.of("aprism", aprism));
        }

        @Test
        void readsTypedDeclarations() {
            AprismManifest manifest = manifestWithSettings(Map.of(
                    "maxDistance", Map.of("type", "integer", "default", 32.0, "label", "Max distance"),
                    "enabled", Map.of("type", "boolean", "default", true)));

            List<SettingDeclaration> declarations = SettingsDeclarationReader.read(manifest);

            assertThat(declarations).hasSize(2);
            SettingDeclaration distance = declarations.stream()
                    .filter(d -> d.key().equals("maxDistance")).findFirst().orElseThrow();
            assertThat(distance.type()).isEqualTo(SettingType.INTEGER);
            assertThat(distance.defaultValue()).isEqualTo(32L);
            assertThat(distance.label()).isEqualTo("Max distance");

            SettingDeclaration enabled = declarations.stream()
                    .filter(d -> d.key().equals("enabled")).findFirst().orElseThrow();
            assertThat(enabled.type()).isEqualTo(SettingType.BOOLEAN);
            assertThat(enabled.defaultValue()).isEqualTo(true);
        }

        @Test
        void readsEnumOptions() {
            AprismManifest manifest = manifestWithSettings(Map.of(
                    "mode", Map.of("type", "enum", "default", "fast",
                            "options", List.of("fast", "fancy"))));

            List<SettingDeclaration> declarations = SettingsDeclarationReader.read(manifest);

            assertThat(declarations).hasSize(1);
            SettingDeclaration mode = declarations.get(0);
            assertThat(mode.type()).isEqualTo(SettingType.ENUM);
            assertThat(mode.defaultValue()).isEqualTo("fast");
            assertThat(mode.options()).containsExactly("fast", "fancy");
            assertThat(mode.hasOptions()).isTrue();
        }

        @Test
        void integerDefaultNarrowedFromDouble() {
            AprismManifest manifest = manifestWithSettings(Map.of(
                    "count", Map.of("type", "integer", "default", 7.0)));

            SettingDeclaration declaration = SettingsDeclarationReader.read(manifest).get(0);
            assertThat(declaration.defaultValue()).isEqualTo(7L);
        }

        @Test
        void emptyWhenNoCustomBlock() {
            AprismManifest manifest = new AprismManifest(1, "examplemod", "1.0.0",
                    "Example", "", "*", Map.of(), List.of(), Map.of(), Map.of(),
                    null, List.of(), Map.of());

            assertThat(SettingsDeclarationReader.read(manifest)).isEmpty();
        }

        @Test
        void emptyWhenAprismSettingsAbsent() {
            AprismManifest manifest = new AprismManifest(1, "examplemod", "1.0.0",
                    "Example", "", "*", Map.of(), List.of(), Map.of(), Map.of(),
                    null, List.of(), Map.of("unrelated", "value"));

            assertThat(SettingsDeclarationReader.read(manifest)).isEmpty();
        }

        @Test
        void skipsMalformedEntries() {
            Map<String, Object> settings = new java.util.LinkedHashMap<>();
            settings.put("good", Map.of("type", "string", "default", "hello"));
            settings.put("bad", "not-a-map");
            AprismManifest manifest = manifestWithSettings(settings);

            List<SettingDeclaration> declarations = SettingsDeclarationReader.read(manifest);
            assertThat(declarations).hasSize(1);
            assertThat(declarations.get(0).key()).isEqualTo("good");
        }

        @Test
        void nullManifestYieldsEmpty() {
            assertThat(SettingsDeclarationReader.read(null)).isEmpty();
        }
    }
}
