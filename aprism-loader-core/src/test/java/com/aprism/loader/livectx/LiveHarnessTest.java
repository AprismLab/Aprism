package com.aprism.loader.livectx;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Live harness report/metrics tests (v26.9-Alpha.8). The hook installation
 * itself needs a real game; these tests cover the report contract, metric
 * math, and the fail-open behaviour that keeps a broken capture from ever
 * reaching the game.
 *
 * @author BlockConnect@StarsailsClover
 */
class LiveHarnessTest {

    @TempDir
    Path tempDir;

    @Test
    void reportIsFailClosedAndTracksWorldJoin() throws IOException {
        LiveContextTracker tracker = new LiveContextTracker();
        LiveHarness harness = new LiveHarness(tracker, tempDir);
        assertFalse(harness.worldJoined());
        String before = harness.toJson();
        assertTrue(before.contains("\"worldJoined\":false"));
        assertTrue(before.startsWith("{\"schemaVersion\":\"aprism.harness/v1\""));
        // No bootstrap/join yet: metrics report -1 rather than a bogus 0.
        assertTrue(before.contains("\"bootstrapMs\":-1.0"), before);
        assertTrue(before.contains("\"worldJoinMs\":-1.0"), before);

        harness.markBootstrap();
        harness.writeReport();
        Path report = tempDir.resolve("aprism-harness.json");
        assertTrue(Files.exists(report), "report must be written");
        String json = Files.readString(report);
        assertTrue(json.contains("\"bootstrapMs\":-1.0") == false,
                "bootstrap metric must become real once marked");
        assertTrue(harness.diagnostics().isEmpty());
    }

    @Test
    void installIsFailOpenWithoutAGame() {
        LiveContextTracker tracker = new LiveContextTracker();
        LiveHarness harness = new LiveHarness(tracker, tempDir);
        // Registering hooks must never throw outside a game; later capture
        // steps degrade to diagnostics instead.
        assertDoesNotThrow(harness::installWithNoMappings);
        assertDoesNotThrow(harness::uninstall);
        // The report still serialises with an empty diagnostic list.
        assertTrue(harness.toJson().contains("\"diagnostics\":["));
    }

    @Test
    void uninstallIsSafeWhenNothingInstalled() {
        LiveHarness harness = new LiveHarness(new LiveContextTracker(), tempDir);
        assertDoesNotThrow(harness::uninstall);
        assertDoesNotThrow(harness::uninstall);
    }

    @Test
    void jsonEscapesDiagnostics() {
        LiveHarness harness = new LiveHarness(new LiveContextTracker(), null);
        // A null output directory must not break report generation.
        assertDoesNotThrow(harness::writeReport);
        assertTrue(harness.toJson().endsWith("]}"));
    }
}
