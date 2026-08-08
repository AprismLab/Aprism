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
import com.aprism.loader.testmods.ForgeRecordingMod;

/**
 * Cross-repository end-to-end test: loads the {@code Forge-Support.aep}
 * produced by the AprismRefract {@code forge} branch (a sibling repository
 * build artifact) through the real Aprism runtime, then loads a genuine
 * Forge-style mod from {@code forge-mods/}.
 *
 * <p>This proves the Refract -> Aprism integration loop: the extension jar
 * bundled in the Refract-built {@code .aep} is extracted by Aprism's
 * {@code ExtensionLoader} into the classloader, its entrypoint
 * ({@code com.aprism.refract.forge.ForgeSupportExtension}) registers
 * {@code forge-mods/}, and a Forge mod is then discovered and constructed
 * through the Forge bridge.
 *
 * <p>The test is skipped when the Refract build artifact is absent (the test
 * compiles against the Aprism repo alone); run {@code ./gradlew packageAep}
 * in the AprismRefract forge branch first.
 *
 * @author BlockConnect@StarsailsClover
 */
class RefractForgeAepE2ETest {

    /** The Refract-built Forge-Support .aep produced by the forge branch. */
    private static final Path REFRACT_AEP = Paths.get(
            "..", "..", "AprismRefract", "build", "aprism",
            "Forge-Support-A[26.0,27.0)-Fo[47.0,48.0)-JE-26.2.aep");

    private static final String MOD_CLASS = "com.aprism.loader.testmods.ForgeRecordingMod";

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
        ForgeRecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    @EnabledIf("refractAepPresent")
    void refractBuiltAepRegistersForgeFolderAndLoadsMod() throws Exception {
        // Phase 1: install the Refract-built Forge-Support.aep
        Path extDir = gameRoot.resolve("aprism-extensions");
        Files.createDirectories(extDir);
        Files.copy(REFRACT_AEP, extDir.resolve("Forge-Support.aep"),
                StandardCopyOption.REPLACE_EXISTING);

        // Phase 2: a genuine Forge-style mod in forge-mods/
        writeForgeModJar(gameRoot.resolve("forge-mods/forgemod.jar"), "forgemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // The Refract extension registered the Forge loader folder
        assertThat(runtime.getLoaderFolders()).containsEntry("Fo", "forge-mods");
        // The extension entrypoint was instantiated (from the Refract-bundled jar)
        assertThat(runtime.getExtension("forge-support")).isNotNull();

        // The Forge mod was discovered and constructed during INIT
        assertThat(runtime.getMods()).hasSize(1);
        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(runtime.getMods().get(0).getInstance()).isNotNull();
    }

    private static void writeForgeModJar(Path jarFile, String modId) throws IOException {
        Files.createDirectories(jarFile.getParent());
        String toml = """
                modLoader="javafml"
                loaderVersion="[47,)"
                license="MIT"

                [[mods]]
                modId="%s"
                version="1.0.0"
                displayName="%s"
                description="test forge mod"
                """.formatted(modId, modId);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            zos.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            zos.write(toml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String classPath = MOD_CLASS.replace('.', '/') + ".class";
            zos.putNextEntry(new ZipEntry(classPath));
            try (InputStream in = ForgeRecordingMod.class.getResourceAsStream(
                    "ForgeRecordingMod.class")) {
                zos.write(in.readAllBytes());
            }
            zos.closeEntry();
        }
    }
}
