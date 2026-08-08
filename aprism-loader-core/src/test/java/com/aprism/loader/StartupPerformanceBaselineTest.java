package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.loader.testmods.NeoForgeRecordingMod;

/**
 * Alpha 7 startup performance baseline. Measures the wall-clock time of a
 * full mixed-instance boot ({@code performLoad} over an Aprism-native mod, a
 * Fabric mod, and a NeoForge mod) and records it as the Alpha 7 baseline.
 *
 * <p>This is a <em>measurement</em>, not a regression gate: it logs the
 * duration and asserts only a generous upper bound (the boot must complete far
 * faster than any human-perceptible threshold, proving the two-phase scan,
 * dependency resolution, and extension loading are not pathological). The
 * recorded value becomes the reference for future Alphas to compare against.
 *
 * @author BlockConnect@StarsailsClover
 */
class StartupPerformanceBaselineTest {

    private static final String FABRIC_MOD_CLASS = "com.aprism.loader.testmods.FabricRecordingMod";
    private static final String NEOFORGE_MOD_CLASS = "com.aprism.loader.testmods.NeoForgeRecordingMod";
    private static final String APRISM_MOD_CLASS = "com.aprism.loader.testmods.RecordingMod";

    @TempDir
    Path gameRoot;

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void mixedInstanceBootBaseline() throws Exception {
        writeLoaderSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"),
                "fabric-support", "Fa", "fabric-mods");
        writeLoaderSupportAep(gameRoot.resolve("aprism-extensions/NeoForge-Support.aep"),
                "neoforge-support", "N", "neoforge-mods");
        writeAprismNativeMod(gameRoot.resolve("mods/nativemod.aje"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"), "fabricmod");
        writeNeoForgeModJar(gameRoot.resolve("neoforge-mods/neoforgemod.jar"), "neoforgemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");

        // Warm-up boot (exercises class loading, Mixin init, config parsing)
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
        assertThat(runtime.getMods()).hasSize(3);
        runtime.shutdown();

        // Measured boot (fresh runtime)
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        long t0 = System.nanoTime();
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(runtime.getMods()).hasSize(3);

        // Record the baseline in the build output for cross-Alpha comparison.
        System.out.println("[PERF] Alpha.7 mixed-instance performLoad baseline: "
                + elapsedMs + " ms (3 mods: aprism + fabric + neoforge)");

        // Generous gate: a mixed boot must not exceed 10 seconds. Anything
        // beyond that indicates a pathological regression in scanning,
        // dependency resolution, or extension loading.
        assertThat(elapsedMs)
                .as("mixed-instance boot should complete well under 10 s")
                .isLessThan(10_000);
    }

    // ----- fixture helpers -----

    private static void writeLoaderSupportAep(Path aepFile, String extensionId,
            String loaderKey, String folder) throws IOException {
        Files.createDirectories(aepFile.getParent());
        String json = """
                {
                  "extensionId": "%s",
                  "type": "loader-support",
                  "aprismRange": "[26.0.0,27.0.0)",
                  "loaderKey": "%s",
                  "loaderRange": null,
                  "mcEdit": null,
                  "mcVersion": null,
                  "entrypoint": null,
                  "provides": [],
                  "depends": {}
                }
                """.formatted(extensionId, loaderKey);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(aepFile))) {
            zos.putNextEntry(new ZipEntry("aprism.extension.json"));
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static void writeAprismNativeMod(Path ajeFile) throws IOException {
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
                """.formatted(APRISM_MOD_CLASS);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(ajeFile))) {
            zos.putNextEntry(new ZipEntry("aprism.manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
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
                """.formatted(id, id, FABRIC_MOD_CLASS);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            zos.putNextEntry(new ZipEntry("fabric.mod.json"));
            zos.write(fabricJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static void writeNeoForgeModJar(Path jarFile, String modId) throws IOException {
        Files.createDirectories(jarFile.getParent());
        String toml = """
                modLoader="javafml"
                loaderVersion="[1,)"
                license="MIT"

                [[mods]]
                modId="%s"
                version="1.0.0"
                displayName="%s"
                description="test neoforge mod"
                """.formatted(modId, modId);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            zos.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            zos.write(toml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            String classPath = NEOFORGE_MOD_CLASS.replace('.', '/') + ".class";
            zos.putNextEntry(new ZipEntry(classPath));
            try (InputStream in = NeoForgeRecordingMod.class.getResourceAsStream(
                    "NeoForgeRecordingMod.class")) {
                zos.write(in.readAllBytes());
            }
            zos.closeEntry();
        }
    }
}
