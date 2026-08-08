package com.aprism.loader.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.api.AprismPhase;
import com.aprism.loader.AprismRuntime;
import com.aprism.loader.LoadedModContainer;
import com.aprism.loader.testmods.FabricRecordingMod;

/**
 * End-to-end integration test for the Fabric-Support path: a genuine
 * Fabric-style mod (implementing the Fabric entrypoint interfaces) is packaged
 * into a jar with a {@code fabric.mod.json}, discovered in {@code fabric-mods/}
 * via the Fabric-Support extension, and its entrypoints are invoked through the
 * {@link FabricEntrypointBridge}.
 *
 * @author BlockConnect@StarsailsClover
 */
class FabricSupportIntegrationTest {

    private static final String FABRIC_MOD_CLASS = "com.aprism.loader.testmods.FabricRecordingMod";

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        FabricRecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void fabricModDiscoveredAndInvokedViaBridge() throws Exception {
        // Phase 1: Fabric-Support extension registers fabric-mods/
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));

        // Phase 2: a real Fabric-style mod jar in fabric-mods/
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"),
                "fabricmod", List.of("main", "client", "server"));

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // Fabric-Support registered the folder and the mod was discovered
        assertThat(runtime.getLoaderFolders()).containsEntry("Fa", "fabric-mods");
        List<LoadedModContainer> mods = runtime.getMods();
        assertThat(mods).hasSize(1);
        assertThat(mods.get(0).getId()).isEqualTo("fabricmod");
        assertThat(mods.get(0).getLoaderKey()).isEqualTo("Fa");

        // INIT phase bridges to Fabric's onInitialize()
        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(FabricRecordingMod.getGlobalCalls()).containsExactly("main");
    }

    @Test
    void fabricClientAndServerPhasesBridge() throws Exception {
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"),
                "fabricmod", List.of("main", "client", "server"));

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(FabricRecordingMod.getGlobalCalls()).containsExactly("main");
        FabricRecordingMod.resetGlobal();

        runtime.invokeEntrypoints(AprismPhase.CLIENT);
        assertThat(FabricRecordingMod.getGlobalCalls()).containsExactly("client");
        FabricRecordingMod.resetGlobal();

        runtime.invokeEntrypoints(AprismPhase.SERVER);
        assertThat(FabricRecordingMod.getGlobalCalls()).containsExactly("server");
    }

    @Test
    void fabricModPreinitSetupCompleteAreNoOps() throws Exception {
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"),
                "fabricmod", List.of("main", "client", "server"));

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // PREINIT/SETUP/COMPLETE have no Fabric equivalent: nothing invoked
        runtime.invokeEntrypoints(AprismPhase.PREINIT);
        runtime.invokeEntrypoints(AprismPhase.SETUP);
        runtime.invokeEntrypoints(AprismPhase.COMPLETE);
        assertThat(FabricRecordingMod.getGlobalCalls()).isEmpty();
    }

    @Test
    void mixedInstanceFabricAndAprismNativeCoexist() throws Exception {
        // Mixed instance: one Aprism-native mod + one Fabric mod
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"),
                "fabricmod", List.of("main"));
        // An Aprism-native mod packaged as .aje
        writeAjeNativeMod(gameRoot.resolve("mods/nativemod.aje"));

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        assertThat(runtime.getMods()).hasSize(2);

        // INIT: only the Fabric mod's entrypoint is invoked (via the bridge);
        // the native mod's entrypoint does not implement IAprismMod so it is
        // not dispatched on the Aprism lifecycle path.
        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(FabricRecordingMod.getGlobalCalls()).containsExactly("main");
    }

    // ----- fixture helpers -----

    private static void writeFabricSupportAep(Path aepFile) throws IOException {
        Files.createDirectories(aepFile.getParent());
        String json = """
                {
                  "extensionId": "fabric-support",
                  "type": "loader-support",
                  "aprismRange": "[26.0.0,27.0.0)",
                  "loaderKey": "Fa",
                  "loaderRange": "[0.16.0,0.17.0)",
                  "mcEdit": null,
                  "mcVersion": null,
                  "entrypoint": null,
                  "provides": ["fabric-loader"],
                  "depends": {}
                }
                """;
        writeZipEntry(aepFile, "aprism.extension.json", json);
    }

    /**
     * Writes a Fabric mod jar containing only a {@code fabric.mod.json}. The
     * entrypoint class itself is intentionally NOT embedded: {@link FabricRecordingMod}
     * resolves via parent delegation to the same class the test references, so
     * the static call log is observable. This mirrors the {@code RecordingMod}
     * pattern used by {@code AprismRuntimeTest}.
     */
    private static void writeFabricModJar(Path jarFile, String id,
            List<String> entrypointKeys) throws IOException {
        Files.createDirectories(jarFile.getParent());
        StringBuilder entrypoints = new StringBuilder("{");
        for (int i = 0; i < entrypointKeys.size(); i++) {
            if (i > 0) {
                entrypoints.append(",");
            }
            entrypoints.append("\"").append(entrypointKeys.get(i)).append("\":[\"")
                    .append(FABRIC_MOD_CLASS).append("\"]");
        }
        entrypoints.append("}");

        String fabricJson = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "1.0.0",
                  "name": "%s",
                  "environment": "*",
                  "entrypoints": %s
                }
                """.formatted(id, id, entrypoints);

        writeZipEntry(jarFile, "fabric.mod.json", fabricJson);
    }

    /**
     * Writes an Aprism-native mod (.aje) whose manifest entrypoint names
     * {@link FabricRecordingMod} (which does NOT implement {@code IAprismMod},
     * exercising the dispatch path that skips the Aprism lifecycle for it).
     */
    private static void writeAjeNativeMod(Path ajeFile) throws IOException {
        Files.createDirectories(ajeFile.getParent());
        String manifest = """
                {
                  "schemaVersion": 1,
                  "id": "nativemod",
                  "version": "1.0.0",
                  "displayName": "nativemod",
                  "description": "test",
                  "environment": "*",
                  "entrypoints": {"main": ["%s"]},
                  "mixins": [],
                  "depends": {},
                  "platforms": {},
                  "accessWidener": null,
                  "provides": [],
                  "custom": {}
                }
                """.formatted(FABRIC_MOD_CLASS);
        writeZipEntry(ajeFile, "aprism.manifest.json", manifest);
    }

    private static void writeZipEntry(Path file, String entry, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
