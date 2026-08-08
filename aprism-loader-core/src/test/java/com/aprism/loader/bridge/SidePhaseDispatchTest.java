package com.aprism.loader.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.loader.AprismRuntime;
import com.aprism.loader.testmods.FabricRecordingMod;

/**
 * Tests for distribution-side phase dispatch in production bootstrap:
 * {@code bootstrapProduction(gameRoot, side)} dispatches the CLIENT or SERVER
 * phase after the common lifecycle.
 *
 * @author BlockConnect@StarsailsClover
 */
class SidePhaseDispatchTest {

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
    void clientSideDispatchesClientPhase() throws Exception {
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"),
                "fabricmod", java.util.List.of("main", "client"));

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.bootstrapProduction(gameRoot, "client");

        // common lifecycle (main) + client side phase
        assertThat(FabricRecordingMod.getGlobalCalls())
                .containsExactly("main", "client");
    }

    @Test
    void serverSideDispatchesServerPhase() throws Exception {
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"),
                "fabricmod", java.util.List.of("main", "server"));

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.bootstrapProduction(gameRoot, "server");

        assertThat(FabricRecordingMod.getGlobalCalls())
                .containsExactly("main", "server");
    }

    @Test
    void nullSideSkipsSidePhase() throws Exception {
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"),
                "fabricmod", java.util.List.of("main", "client", "server"));

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.bootstrapProduction(gameRoot, null);

        // only the common lifecycle (main); no side phase
        assertThat(FabricRecordingMod.getGlobalCalls()).containsExactly("main");
    }

    @Test
    void unrecognizedSideSkipsSidePhase() throws Exception {
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"),
                "fabricmod", java.util.List.of("main", "client"));

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.bootstrapProduction(gameRoot, "bogus");

        assertThat(FabricRecordingMod.getGlobalCalls()).containsExactly("main");
    }

    @Test
    void legacyBootstrapOverloadSkipsSidePhase() throws Exception {
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"),
                "fabricmod", java.util.List.of("main", "client"));

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        // legacy single-arg overload: no side dispatch
        runtime.bootstrapProduction(gameRoot);

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

    private static void writeFabricModJar(Path jarFile, String id,
            java.util.List<String> entrypointKeys) throws IOException {
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

    private static void writeZipEntry(Path file, String entry, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
