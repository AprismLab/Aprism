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

import com.aprism.api.AprismPhase;
import com.aprism.loader.AprismRuntime;
import com.aprism.loader.testmods.QuiltRecordingMod;

/**
 * End-to-end integration test for the Quilt-Support path: a genuine
 * Quilt-style mod (implementing {@code net.fabricmc.api.ModInitializer} via
 * Quilt's built-in Fabric compatibility layer) is packaged into a jar with
 * {@code quilt.mod.json}, discovered in {@code quilt-mods/} via the
 * Quilt-Support extension, and its entrypoint invoked through the
 * Fabric-convention bridge during INIT.
 *
 * @author BlockConnect@StarsailsClover
 */
class QuiltSupportIntegrationTest {

    private static final String MOD_CLASS = "com.aprism.loader.testmods.QuiltRecordingMod";

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        QuiltRecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void quiltModDiscoveredAndInitialized() throws Exception {
        writeQuiltSupportAep(gameRoot.resolve("aprism-extensions/Quilt-Support.aep"));
        writeQuiltModJar(gameRoot.resolve("quilt-mods/quiltmod.jar"), "quiltmod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        assertThat(runtime.getLoaderFolders()).containsEntry("Q", "quilt-mods");
        assertThat(runtime.getMods()).hasSize(1);
        assertThat(runtime.getMods().get(0).getId()).isEqualTo("quiltmod");
        assertThat(runtime.getMods().get(0).getLoaderKey()).isEqualTo("Q");

        // The quilt_loader "init" entrypoint key projects to "main"
        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(QuiltRecordingMod.getGlobalCalls()).contains("main");
    }

    @Test
    void quiltClientEntrypointInvokedOnClientPhase() throws Exception {
        writeQuiltSupportAep(gameRoot.resolve("aprism-extensions/Quilt-Support.aep"));
        writeQuiltModJarWithClient(gameRoot.resolve("quilt-mods/quiltmod.jar"), "quiltmod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        runtime.invokeEntrypoints(AprismPhase.CLIENT);
        assertThat(QuiltRecordingMod.getGlobalCalls()).contains("client");
    }

    @Test
    void quiltModCoexistsWithFabricModInSeparateFolders() throws Exception {
        // Both loader-support extensions must be registered for both folders to scan
        writeQuiltSupportAep(gameRoot.resolve("aprism-extensions/Quilt-Support.aep"));
        writeFabricSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
        writeQuiltModJar(gameRoot.resolve("quilt-mods/quiltmod.jar"), "quiltmod");
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"), "fabricmod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        assertThat(runtime.getLoaderFolders())
                .containsEntry("Q", "quilt-mods")
                .containsEntry("Fa", "fabric-mods");
        assertThat(runtime.getMods()).hasSize(2);
        assertThat(runtime.getMods())
                .extracting(m -> m.getId())
                .containsExactlyInAnyOrder("quiltmod", "fabricmod");
    }

    // ----- fixture helpers -----

    private static void writeQuiltSupportAep(Path aepFile) throws IOException {
        Files.createDirectories(aepFile.getParent());
        String json = """
                {
                  "extensionId": "quilt-support",
                  "type": "loader-support",
                  "aprismRange": "[26.0.0,27.0.0)",
                  "loaderKey": "Q",
                  "loaderRange": "[0.29.0,0.30.0)",
                  "mcEdit": null,
                  "mcVersion": null,
                  "entrypoint": "com.aprism.ext.quilt.QuiltSupportExtension",
                  "provides": ["quilt-loader"],
                  "depends": {}
                }
                """;
        writeZipEntry(aepFile, "aprism.extension.json", json);
    }

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
                  "entrypoint": "com.aprism.ext.fabric.FabricSupportExtension",
                  "provides": ["fabric-loader"],
                  "depends": {}
                }
                """;
        writeZipEntry(aepFile, "aprism.extension.json", json);
    }

    private static void writeQuiltModJar(Path jarFile, String id) throws IOException {
        writeQuiltModJarInternal(jarFile, id, false);
    }

    private static void writeQuiltModJarWithClient(Path jarFile, String id) throws IOException {
        writeQuiltModJarInternal(jarFile, id, true);
    }

    private static void writeQuiltModJarInternal(Path jarFile, String id, boolean withClient)
            throws IOException {
        Files.createDirectories(jarFile.getParent());
        String clientBlock = withClient
                ? ", \"client\": [\"" + MOD_CLASS + "\"]"
                : "";
        String quiltJson = """
                {
                  "schema_version": 1,
                  "quilt_loader": {
                    "group": "com.aprism.loader.testmods",
                    "id": "%s",
                    "version": "1.0.0",
                    "metadata": { "name": "%s" },
                    "entrypoints": { "init": ["%s"]%s }
                  }
                }
                """.formatted(id, id, MOD_CLASS, clientBlock);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            zos.putNextEntry(new ZipEntry("quilt.mod.json"));
            zos.write(quiltJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static void writeFabricModJar(Path jarFile, String id) throws IOException {
        Files.createDirectories(jarFile.getParent());
        String fabricJson = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "1.0.0",
                  "name": "%s",
                  "environment": "*",
                  "entrypoints": {"main": ["%s"]}
                }
                """.formatted(id, id, MOD_CLASS);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            zos.putNextEntry(new ZipEntry("fabric.mod.json"));
            zos.write(fabricJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static void writeZipEntry(Path file, String entry, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
