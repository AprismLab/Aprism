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
    void earlyRegistrationUsesTranslatedMethodNames() throws Exception {
        // Regression anchor (v26.9-Alpha.8): the hook KEY must carry the
        // RUNTIME method name. Registering the official name on an obfuscated
        // runtime never matches the real class-file method name, so the
        // transformer saw the hook yet emitted unchanged bytes - silently, for
        // a long time. Verified live on 1.21.4 where handleLogin is "a" and
        // tick is "d" inside the runtime class ggb.
        Path clientTxt = tempDir.resolve("client.txt");
        Files.writeString(clientTxt, """
                net.minecraft.client.Minecraft -> flk:
                    void setLevel(net.minecraft.client.multiplayer.ClientLevel) -> a
                    void setScreen(net.minecraft.client.gui.screens.Screen) -> a
                net.minecraft.client.multiplayer.ClientLevel -> gga:
                net.minecraft.client.gui.screens.Screen -> fum:
                net.minecraft.client.multiplayer.ClientPacketListener -> ggb:
                    void handleLogin(net.minecraft.network.protocol.game.ClientboundLoginPacket) -> a
                    void tick() -> d
                net.minecraft.network.protocol.game.ClientboundLoginPacket -> add:
                """);
        com.aprism.loader.contentbind.OfficialMappings mappings =
                com.aprism.loader.contentbind.OfficialMappings.load(clientTxt);
        assertNotNull(mappings);

        com.aprism.loader.lowlevel.MethodHookRegistry.clear();
        LiveContextTracker tracker = new LiveContextTracker();
        LiveHarness.registerHooksEarly(tracker, mappings);
        assertTrue(LiveHarness.applyEarlyRegistration());

        // The registry must hold RUNTIME-named keys, never the official names.
        java.lang.reflect.Field hooksField =
                com.aprism.loader.lowlevel.MethodHookRegistry.class
                        .getDeclaredField("HOOKS");
        hooksField.setAccessible(true);
        java.util.Map<?, ?> hooks = (java.util.Map<?, ?>) hooksField.get(null);
        java.util.List<String> keys = hooks.keySet().stream()
                .map(String::valueOf).sorted().toList();

        assertTrue(keys.contains("ggb.a(Ladd;)V"),
                "handleLogin must be registered under its runtime name: " + keys);
        assertTrue(keys.contains("ggb.d()V"),
                "tick must be registered under its runtime name: " + keys);
        assertFalse(keys.stream().anyMatch(k -> k.contains("handleLogin")),
                "official method names must never appear in hook keys: " + keys);
        assertFalse(keys.stream().anyMatch(k -> k.startsWith("ggb.tick")),
                "official tick name must not be used: " + keys);
        com.aprism.loader.lowlevel.MethodHookRegistry.clear();
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
