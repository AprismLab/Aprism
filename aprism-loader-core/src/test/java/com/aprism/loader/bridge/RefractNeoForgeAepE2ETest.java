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
import com.aprism.loader.testmods.NeoForgeRecordingMod;

/**
 * Cross-repository end-to-end test: loads the {@code NeoForge-Support.aep}
 * produced by the AprismRefract {@code neoforge} branch (a sibling repository
 * build artifact) through the real Aprism runtime, then loads a genuine
 * NeoForge-style mod from {@code neoforge-mods/}.
 *
 * <p>This proves the Refract -> Aprism integration loop: the extension jar
 * bundled in the Refract-built {@code .aep} is extracted by Aprism's
 * {@code ExtensionLoader} into the classloader, its entrypoint
 * ({@code com.aprism.refract.neoforge.NeoForgeSupportExtension}) registers
 * {@code neoforge-mods/}, and a NeoForge mod is then discovered and
 * constructed.
 *
 * <p>The test is skipped when the Refract build artifact is absent (the test
 * compiles against the Aprism repo alone); run
 * {@code ./gradlew packageAep} in the AprismRefract neoforge branch first.
 *
 * @author BlockConnect@StarsailsClover
 */
class RefractNeoForgeAepE2ETest {

    /** The Refract-built NeoForge-Support .aep produced by the neoforge branch. */
    private static final Path REFRACT_AEP = Paths.get(
            "..", "..", "AprismRefract", "build", "aprism",
            "NeoForge-Support-A[26.0,27.0)-N[21.4,21.5)-JE-26.2.aep");

    private static final String MOD_CLASS = "com.aprism.loader.testmods.NeoForgeRecordingMod";

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
        NeoForgeRecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    @EnabledIf("refractAepPresent")
    void refractBuiltAepRegistersNeoForgeFolderAndLoadsMod() throws Exception {
        // Phase 1: install the Refract-built NeoForge-Support.aep
        Path extDir = gameRoot.resolve("aprism-extensions");
        Files.createDirectories(extDir);
        Files.copy(REFRACT_AEP, extDir.resolve("NeoForge-Support.aep"),
                StandardCopyOption.REPLACE_EXISTING);

        // Phase 2: a genuine NeoForge-style mod in neoforge-mods/
        writeNeoForgeModJar(gameRoot.resolve("neoforge-mods/neoforgemod.jar"), "neoforgemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // The Refract extension registered the NeoForge loader folder
        assertThat(runtime.getLoaderFolders()).containsEntry("N", "neoforge-mods");
        // The extension entrypoint was instantiated (from the Refract-bundled jar)
        assertThat(runtime.getExtension("neoforge-support")).isNotNull();

        // The NeoForge mod was discovered and constructed during INIT
        assertThat(runtime.getMods()).hasSize(1);
        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(runtime.getMods().get(0).getInstance()).isNotNull();
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

            String classPath = MOD_CLASS.replace('.', '/') + ".class";
            zos.putNextEntry(new ZipEntry(classPath));
            try (InputStream in = NeoForgeRecordingMod.class.getResourceAsStream(
                    "NeoForgeRecordingMod.class")) {
                zos.write(in.readAllBytes());
            }
            zos.closeEntry();
        }
    }
}
