package com.aprism.loader.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
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
import com.aprism.loader.testmods.LiteModRecordingMod;

/**
 * Cross-repository end-to-end test: loads the {@code LiteLoader-Support.aep}
 * produced by the AprismRefract {@code liteloader} branch (a sibling
 * repository build artifact) through the real Aprism runtime, then loads a
 * genuine LiteLoader-style mod from {@code liteloader-mods/}.
 *
 * <p>This proves the Refract -> Aprism integration loop: the extension jar
 * bundled in the Refract-built {@code .aep} is extracted by Aprism's
 * {@code ExtensionLoader} into the classloader, its entrypoint
 * ({@code com.aprism.refract.liteloader.LiteLoaderSupportExtension})
 * registers {@code liteloader-mods/}, and a {@code .litemod} mod is then
 * discovered and its {@code init(File)} invoked through the LiteLoader
 * bridge.
 *
 * <p>The test is skipped when the Refract build artifact is absent (the test
 * compiles against the Aprism repo alone); run {@code ./gradlew packageAep}
 * in the AprismRefract liteloader branch first.
 *
 * @author BlockConnect@StarsailsClover
 */
class RefractLiteLoaderAepE2ETest {

    /** The Refract-built LiteLoader-Support .aep from the liteloader branch. */
    private static final Path REFRACT_AEP = Paths.get(
            "..", "..", "AprismRefract", "build", "aprism",
            "LiteLoader-Support-A[26.0,27.0)-L[1.12,1.13)-JE-26.2.aep");

    private static final String MOD_CLASS = "com.aprism.loader.testmods.LiteModRecordingMod";

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
        LiteModRecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    @EnabledIf("refractAepPresent")
    void refractBuiltAepRegistersLiteLoaderFolderAndLoadsMod() throws Exception {
        // Phase 1: install the Refract-built LiteLoader-Support.aep
        Path extDir = gameRoot.resolve("aprism-extensions");
        Files.createDirectories(extDir);
        Files.copy(REFRACT_AEP, extDir.resolve("LiteLoader-Support.aep"),
                StandardCopyOption.REPLACE_EXISTING);

        // Phase 2: a genuine LiteLoader-style mod in liteloader-mods/
        writeLiteMod(gameRoot.resolve("liteloader-mods/litemod.litemod"), "litemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // The Refract extension registered the LiteLoader folder
        assertThat(runtime.getLoaderFolders()).containsEntry("L", "liteloader-mods");
        // The extension entrypoint was instantiated (from the Refract-bundled jar)
        assertThat(runtime.getExtension("liteloader-support")).isNotNull();

        // The LiteLoader mod was discovered and its init(File) invoked in INIT
        assertThat(runtime.getMods()).hasSize(1);
        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(runtime.getMods().get(0).getInstance()).isNotNull();
    }

    private static void writeLiteMod(Path archiveFile, String modId) throws IOException {
        Files.createDirectories(archiveFile.getParent());
        String json = """
                {
                  "name": "%s",
                  "version": "1.0.0",
                  "mcversion": "1.12.2",
                  "revision": 1,
                  "author": "test",
                  "description": "test liteloader mod"
                }
                """.formatted(modId);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archiveFile))) {
            zos.putNextEntry(new ZipEntry("litemod.json"));
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String classPath = MOD_CLASS.replace('.', '/') + ".class";
            zos.putNextEntry(new ZipEntry(classPath));
            try (InputStream in = LiteModRecordingMod.class.getResourceAsStream(
                    "LiteModRecordingMod.class")) {
                zos.write(in.readAllBytes());
            }
            zos.closeEntry();
        }
    }
}
