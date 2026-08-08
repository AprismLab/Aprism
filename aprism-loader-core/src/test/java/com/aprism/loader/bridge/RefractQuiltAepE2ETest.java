package com.aprism.loader.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIf;

import com.aprism.api.AprismPhase;
import com.aprism.loader.AprismRuntime;
import com.aprism.loader.testmods.QuiltRecordingMod;

/**
 * Cross-repository end-to-end test: loads the {@code Quilt-Support.aep}
 * produced by the AprismRefract {@code quilt} branch (a sibling repository
 * build artifact) through the real Aprism runtime, then loads a genuine
 * Quilt-style mod from {@code quilt-mods/}.
 *
 * <p>This proves the Refract -> Aprism integration loop: the extension jar
 * bundled in the Refract-built {@code .aep} is extracted by Aprism's
 * {@code ExtensionLoader} into the classloader, its entrypoint
 * ({@code com.aprism.refract.quilt.QuiltSupportExtension}) registers
 * {@code quilt-mods/}, and a Quilt mod is then discovered and its
 * {@code init} entrypoint invoked through the Fabric-convention bridge.
 *
 * <p>The test is skipped when the Refract build artifact is absent (the test
 * compiles against the Aprism repo alone); run {@code ./gradlew packageAep}
 * in the AprismRefract quilt branch first.
 *
 * @author BlockConnect@StarsailsClover
 */
class RefractQuiltAepE2ETest {

    /** The Refract-built Quilt-Support .aep produced by the quilt branch. */
    private static final Path REFRACT_AEP = Paths.get(
            "..", "..", "AprismRefract", "build", "aprism",
            "Quilt-Support-A[26.0,27.0)-Q[0.29,0.30)-JE-26.2.aep");

    private static final String MOD_CLASS = "com.aprism.loader.testmods.QuiltRecordingMod";

    @TempDir
    Path gameRoot;

    /**
     * @return whether the Refract build artifact exists (enables the test)
     */
    static boolean refractAepPresent() {
        return Files.isRegularFile(REFRACT_AEP);
    }

    @BeforeEach
    void setUp() {
        QuiltRecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    @EnabledIf("refractAepPresent")
    void refractBuiltAepRegistersQuiltFolderAndInvokesMod() throws Exception {
        // Phase 1: install the Refract-built Quilt-Support.aep
        Path extDir = gameRoot.resolve("aprism-extensions");
        Files.createDirectories(extDir);
        Files.copy(REFRACT_AEP, extDir.resolve("Quilt-Support.aep"),
                StandardCopyOption.REPLACE_EXISTING);

        // Phase 2: a genuine Quilt-style mod in quilt-mods/
        writeQuiltModJar(gameRoot.resolve("quilt-mods/quiltmod.jar"), "quiltmod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // The Refract extension registered the Quilt loader folder
        assertThat(runtime.getLoaderFolders()).containsEntry("Q", "quilt-mods");
        // The extension entrypoint was instantiated (from the Refract-bundled jar)
        assertThat(runtime.getExtension("quilt-support")).isNotNull();

        // The Quilt mod was discovered and its INIT entrypoint invoked
        assertThat(runtime.getMods()).hasSize(1);
        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(QuiltRecordingMod.getGlobalCalls()).contains("main");
    }

    private static void writeQuiltModJar(Path jarFile, String id) throws IOException {
        Files.createDirectories(jarFile.getParent());
        String quiltJson = """
                {
                  "schema_version": 1,
                  "quilt_loader": {
                    "group": "com.aprism.loader.testmods",
                    "id": "%s",
                    "version": "1.0.0",
                    "metadata": { "name": "%s" },
                    "entrypoints": { "init": ["%s"] }
                  }
                }
                """.formatted(id, id, MOD_CLASS);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            zos.putNextEntry(new ZipEntry("quilt.mod.json"));
            zos.write(quiltJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
