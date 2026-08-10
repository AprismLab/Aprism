package com.aprism.loader.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.manifest.SettingDeclaration;
import com.aprism.manifest.SettingType;

/**
 * Tests for the per-mod settings store and the central settings registry
 * (v26.2-Alpha.3, goal #7 part 2): defaults, typed validation, persistence
 * round-trip, and resilience against invalid persisted values.
 *
 * @author BlockConnect@StarsailsClover
 */
class SettingsRegistryTest {

    private static ModSettings modSettings() {
        ModSettings settings = new ModSettings("examplemod");
        settings.declare(SettingDeclaration.of("maxDistance", SettingType.INTEGER, 32L, "Max distance"));
        settings.declare(SettingDeclaration.of("scale", SettingType.DOUBLE, 1.5, "Scale"));
        settings.declare(SettingDeclaration.of("enabled", SettingType.BOOLEAN, true, "Enabled"));
        settings.declare(SettingDeclaration.of("name", SettingType.STRING, "default-name", "Name"));
        settings.declare(new SettingDeclaration("mode", SettingType.ENUM, "fast", "Mode",
                java.util.List.of("fast", "fancy")));
        return settings;
    }

    @Nested
    class DefaultsAndAccess {
        @Test
        void declaredDefaultsAreActive() {
            ModSettings settings = modSettings();

            assertThat(settings.getLong("maxDistance", -1)).isEqualTo(32L);
            assertThat(settings.getDouble("scale", -1)).isEqualTo(1.5);
            assertThat(settings.getBoolean("enabled", false)).isTrue();
            assertThat(settings.getString("name")).isEqualTo("default-name");
            assertThat(settings.getString("mode")).isEqualTo("fast");
        }

        @Test
        void unknownKeyFallsBack() {
            ModSettings settings = modSettings();

            assertThat(settings.getLong("missing", 99)).isEqualTo(99);
            assertThat(settings.getDouble("missing", 9.9)).isEqualTo(9.9);
            assertThat(settings.getBoolean("missing", true)).isTrue();
            assertThat(settings.get("missing")).isNull();
        }
    }

    @Nested
    class TypedValidation {
        @Test
        void setValidatesAgainstType() {
            ModSettings settings = modSettings();
            settings.set("maxDistance", 64);
            settings.set("scale", "2.5");
            settings.set("enabled", "false");
            settings.set("name", 42);

            assertThat(settings.getLong("maxDistance", -1)).isEqualTo(64L);
            assertThat(settings.getDouble("scale", -1)).isEqualTo(2.5);
            assertThat(settings.getBoolean("enabled", true)).isFalse();
            assertThat(settings.getString("name")).isEqualTo("42");
        }

        @Test
        void setRejectsUndeclaredKey() {
            ModSettings settings = modSettings();
            assertThatThrownBy(() -> settings.set("ghost", 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not declared");
        }

        @Test
        void setRejectsTypeViolation() {
            ModSettings settings = modSettings();
            assertThatThrownBy(() -> settings.set("maxDistance", "not-a-number"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> settings.set("enabled", "maybe"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void setRejectsEnumOutsideOptions() {
            ModSettings settings = modSettings();
            assertThatThrownBy(() -> settings.set("mode", "ultra"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("one of");

            settings.set("mode", "fancy");
            assertThat(settings.getString("mode")).isEqualTo("fancy");
        }

        @Test
        void setMarksDirty() {
            ModSettings settings = modSettings();
            assertThat(settings.isDirty()).isFalse();
            settings.set("maxDistance", 100);
            assertThat(settings.isDirty()).isTrue();
            settings.markClean();
            assertThat(settings.isDirty()).isFalse();
        }
    }

    @Nested
    class Persistence {

        @TempDir
        Path configDir;

        @Test
        void roundTripPersistsUserValues() throws Exception {
            SettingsRegistry registry = new SettingsRegistry();
            registry.bindStorage(configDir);
            ModSettings settings = registry.register(manifestWithSettings());
            settings.set("maxDistance", 128);
            settings.set("mode", "fancy");
            registry.persistAll();

            Path file = configDir.resolve("examplemod.json");
            assertThat(file).exists();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(json).contains("maxDistance").contains("128").contains("fancy");

            // A fresh registry reads the persisted values back over defaults
            SettingsRegistry reloaded = new SettingsRegistry();
            reloaded.bindStorage(configDir);
            ModSettings reloadedSettings = reloaded.register(manifestWithSettings());
            assertThat(reloadedSettings.getLong("maxDistance", -1)).isEqualTo(128L);
            assertThat(reloadedSettings.getString("mode")).isEqualTo("fancy");
            // untouched settings keep their defaults
            assertThat(reloadedSettings.getBoolean("enabled", false)).isTrue();
        }

        @Test
        void invalidPersistedValueKeepsDefault() throws Exception {
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("examplemod.json"),
                    "{\"maxDistance\": \"not-a-number\", \"mode\": \"ultra\"}",
                    StandardCharsets.UTF_8);

            SettingsRegistry registry = new SettingsRegistry();
            registry.bindStorage(configDir);
            ModSettings settings = registry.register(manifestWithSettings());

            assertThat(settings.getLong("maxDistance", -1)).isEqualTo(32L);
            assertThat(settings.getString("mode")).isEqualTo("fast");
        }

        @Test
        void corruptedJsonFallsBackToDefaults() throws Exception {
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("examplemod.json"), "{ this is not json",
                    StandardCharsets.UTF_8);

            SettingsRegistry registry = new SettingsRegistry();
            registry.bindStorage(configDir);
            ModSettings settings = registry.register(manifestWithSettings());

            assertThat(settings.getLong("maxDistance", -1)).isEqualTo(32L);
        }

        @Test
        void persistOnlyDirtyStores() throws Exception {
            SettingsRegistry registry = new SettingsRegistry();
            registry.bindStorage(configDir);
            registry.register(manifestWithSettings());
            registry.persistAll();

            assertThat(configDir.resolve("examplemod.json")).doesNotExist();
        }

        private static com.aprism.manifest.AprismManifest manifestWithSettings() {
            java.util.Map<String, Object> settings = new java.util.LinkedHashMap<>();
            settings.put("maxDistance", java.util.Map.of("type", "integer", "default", 32.0));
            settings.put("scale", java.util.Map.of("type", "double", "default", 1.5));
            settings.put("enabled", java.util.Map.of("type", "boolean", "default", true));
            settings.put("name", java.util.Map.of("type", "string", "default", "default-name"));
            settings.put("mode", java.util.Map.of("type", "enum", "default", "fast",
                    "options", java.util.List.of("fast", "fancy")));
            return new com.aprism.manifest.AprismManifest(1, "examplemod", "1.0.0",
                    "Example", "", "*", java.util.Map.of(), java.util.List.of(),
                    java.util.Map.of(), java.util.Map.of(), null, java.util.List.of(),
                    java.util.Map.of("aprism", java.util.Map.of("settings", settings)));
        }
    }
}
