package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

import com.aprism.loader.testmods.RecordingMod;
import com.aprism.loader.testmods.ThrowingMod;

/**
 * Alpha 4 production-hardening tests: a single broken mod must not abort the
 * boot or the lifecycle of the remaining mods, and the startup
 * {@link LoadReport} must capture per-unit outcomes.
 *
 * @author BlockConnect@StarsailsClover
 */
class LoadIsolationTest {

    private static final String RECORDING_MOD = "com.aprism.loader.testmods.RecordingMod";
    private static final String THROWING_MOD = "com.aprism.loader.testmods.ThrowingMod";

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        RecordingMod.resetGlobal();
        ThrowingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void throwingModIsolated_otherModsCompleteLifecycle() throws Exception {
        writeAje(gameRoot.resolve("mods/goodmod.aje"), "goodmod", "1.0.0", RECORDING_MOD);
        writeAje(gameRoot.resolve("mods/badmod.aje"), "badmod", "1.0.0", THROWING_MOD);

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");

        // Must not throw even though badmod's onInitialize throws
        assertThatCode(() -> runtime.bootstrapProduction(gameRoot, null))
                .doesNotThrowAnyException();

        // goodmod still received the full common lifecycle
        assertThat(RecordingMod.getGlobalPhases())
                .contains("PREINIT:goodmod", "INIT:goodmod",
                        "SETUP:goodmod", "COMPLETE:goodmod");

        // badmod reached PREINIT (it throws only in INIT)
        assertThat(ThrowingMod.wasPreinitCalled()).isTrue();
    }

    @Test
    void loadReportAvailableAndRecordsBothMods() throws Exception {
        writeAje(gameRoot.resolve("mods/goodmod.aje"), "goodmod", "1.0.0", RECORDING_MOD);
        writeAje(gameRoot.resolve("mods/badmod.aje"), "badmod", "1.0.0", THROWING_MOD);

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.bootstrapProduction(gameRoot, null);

        LoadReport report = runtime.getLoadReport();
        assertThat(report).isNotNull();
        // Both mods registered successfully during load (isolation happens at
        // entrypoint dispatch, not registration), so both are OK entries.
        assertThat(report.okCount()).isEqualTo(2);
        assertThat(report.entries())
                .extracting(LoadReport.Entry::id)
                .containsExactlyInAnyOrder("goodmod", "badmod");
    }

    @Test
    void loadReportSummaryMentionsLoadedMods() throws Exception {
        writeAje(gameRoot.resolve("mods/goodmod.aje"), "goodmod", "1.0.0", RECORDING_MOD);

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.bootstrapProduction(gameRoot, null);

        String summary = runtime.getLoadReport().toSummary(runtime.getAprismVersion());
        assertThat(summary).contains("goodmod");
        assertThat(summary).contains("Aprism Load Report");
    }

    @Test
    void noModsProducesEmptyReport() throws Exception {
        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.bootstrapProduction(gameRoot, null);

        LoadReport report = runtime.getLoadReport();
        assertThat(report).isNotNull();
        assertThat(report.okCount()).isEqualTo(0);
        assertThat(report.failureCount()).isEqualTo(0);
    }

    // ----- fixture helpers -----

    private static void writeAje(Path ajeFile, String id, String version, String mainEntrypoint)
            throws IOException {
        Files.createDirectories(ajeFile.getParent());
        String json = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "%s",
                  "displayName": "%s",
                  "description": "test",
                  "environment": "*",
                  "entrypoints": {"main":["%s"]},
                  "mixins": [],
                  "depends": {},
                  "platforms": {},
                  "accessWidener": null,
                  "provides": [],
                  "custom": {}
                }
                """.formatted(id, version, id, mainEntrypoint);
        writeZip(ajeFile, "aprism.manifest.json", json);
    }

    private static void writeZip(Path file, String entry, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
