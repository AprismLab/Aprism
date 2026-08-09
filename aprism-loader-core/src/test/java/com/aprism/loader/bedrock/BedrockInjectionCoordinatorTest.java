package com.aprism.loader.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.loader.BedrockModDiscoverer.BedrockPlatform;
import com.aprism.loader.LoadedBedrockModContainer;
import com.aprism.manifest.AprismManifest;

/**
 * Tests for the fail-closed {@link BedrockInjectionCoordinator}.
 *
 * @author BlockConnect@StarsailsClover
 */
class BedrockInjectionCoordinatorTest {

    private static final String VALID_DB = """
            {
              "schemaVersion": 1,
              "versions": [
                { "beVersion": "26.2.0", "signatureCount": 42, "supported": true, "notes": "" },
                { "beVersion": "26.1.0", "signatureCount": 40, "supported": false, "notes": "incomplete" }
              ]
            }
            """;

    @TempDir
    Path tempDir;

    private BedrockInjectionCoordinator coordinator;
    private Path dbPath;

    @BeforeEach
    void setUp() throws Exception {
        coordinator = new BedrockInjectionCoordinator();
        dbPath = tempDir.resolve("signatures.json");
        Files.writeString(dbPath, VALID_DB, StandardCharsets.UTF_8);
    }

    private static AprismManifest manifest(String id) {
        return new AprismManifest(1, id, "1.0.0", id, "", "*",
                Map.of(), List.of(), Map.of(), Map.of(), null, List.of(), Map.of());
    }

    private static LoadedBedrockModContainer mod(String id, BedrockPlatform platform, String... entryPaths) {
        return new LoadedBedrockModContainer(manifest(id), null,
                Map.of(platform, List.of(entryPaths)), false, false, false);
    }

    @Test
    void feasiblePlanWithValidDbAndSupportedVersion() {
        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));
        var result = coordinator.coordinate(dbPath, BedrockPlatform.WINDOWS, "26.2.0", mods);

        assertThat(result.attempted()).isTrue();
        assertThat(result.isFeasible()).isTrue();
        assertThat(result.refusalReason()).isNull();
        assertThat(result.plan()).isNotNull();
        assertThat(result.plan().actions()).hasSize(1);
    }

    @Test
    void refusesWhenSignatureDbMissing() {
        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));
        var result = coordinator.coordinate(tempDir.resolve("missing.json"),
                BedrockPlatform.WINDOWS, "26.2.0", mods);

        assertThat(result.isFeasible()).isFalse();
        assertThat(result.refusalReason()).contains("signature database unavailable");
        assertThat(result.plan()).isNull();
    }

    @Test
    void refusesWhenSignatureDbMalformed() throws Exception {
        Path bad = tempDir.resolve("bad.json");
        Files.writeString(bad, "{ not valid json", StandardCharsets.UTF_8);
        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));

        var result = coordinator.coordinate(bad, BedrockPlatform.WINDOWS, "26.2.0", mods);

        assertThat(result.isFeasible()).isFalse();
        assertThat(result.refusalReason()).contains("signature database unavailable");
    }

    @Test
    void refusesWhenPlatformUndetectable() {
        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));
        var result = coordinator.coordinate(dbPath, null, "26.2.0", mods);

        assertThat(result.isFeasible()).isFalse();
        assertThat(result.refusalReason()).contains("unable to detect");
        assertThat(result.plan()).isNull();
    }

    @Test
    void refusesUnknownVersion() {
        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));
        var result = coordinator.coordinate(dbPath, BedrockPlatform.WINDOWS, "99.9.9", mods);

        assertThat(result.isFeasible()).isFalse();
        assertThat(result.refusalReason()).contains("not present in the signature database");
    }

    @Test
    void refusesUnsupportedVersion() {
        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));
        var result = coordinator.coordinate(dbPath, BedrockPlatform.WINDOWS, "26.1.0", mods);

        assertThat(result.isFeasible()).isFalse();
        assertThat(result.refusalReason()).contains("marked unsupported");
    }

    @Test
    void refusesEmptyMods() {
        var result = coordinator.coordinate(dbPath, BedrockPlatform.WINDOWS, "26.2.0", List.of());

        assertThat(result.isFeasible()).isFalse();
        assertThat(result.refusalReason()).contains("no Bedrock mods");
    }

    @Test
    void refusesNoNativeLibsForPlatform() {
        var mods = List.of(mod("alpha", BedrockPlatform.ANDROID, "native/android/alpha.so"));
        var result = coordinator.coordinate(dbPath, BedrockPlatform.WINDOWS, "26.2.0", mods);

        assertThat(result.isFeasible()).isFalse();
        assertThat(result.refusalReason()).contains("no native libraries");
    }

    @Test
    void coordinateForGameRootUsesDefaultDbLocation() throws Exception {
        // Place the DB at the default relative location under a game root.
        Path gameRoot = tempDir.resolve("com.mojang");
        Path dbDir = gameRoot.resolve("aprism-signatures");
        Files.createDirectories(dbDir);
        Files.writeString(dbDir.resolve("signatures.json"), VALID_DB, StandardCharsets.UTF_8);

        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));
        // Platform detection depends on the OS, so only assert the DB was found
        // (no "signature database unavailable" refusal) on this platform.
        var result = coordinator.coordinateForGameRoot(gameRoot, "26.2.0", mods);

        assertThat(result.attempted()).isTrue();
        if (BedrockPlatform.detect() != null) {
            // DB was located; refusal, if any, must not be about the DB.
            if (!result.isFeasible()) {
                assertThat(result.refusalReason()).doesNotContain("signature database unavailable");
            }
        } else {
            assertThat(result.refusalReason()).contains("unable to detect");
        }
    }
}
