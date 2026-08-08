package com.aprism.loader.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIf;

import com.aprism.api.AprismPhase;
import com.aprism.loader.AprismRuntime;
import com.aprism.loader.testmods.FabricRecordingMod;

/**
 * Cross-repository end-to-end test: loads the {@code Fabric-Support.aep}
 * produced by the AprismRefract {@code fabric} branch (a sibling repository
 * build artifact) through the real Aprism runtime, then loads a genuine
 * Fabric-style mod from {@code fabric-mods/}.
 *
 * <p>This proves the Refract -> Aprism integration loop: the extension jar
 * bundled in the Refract-built {@code .aep} is extracted by Aprism's
 * {@code ExtensionLoader} into the classloader, its entrypoint
 * ({@code com.aprism.refract.fabric.FabricSupportExtension}) registers
 * {@code fabric-mods/}, and a Fabric mod is then discovered and its
 * {@code onInitialize} entrypoint invoked through the Fabric bridge.
 *
 * <p>The test is skipped when the Refract build artifact is absent (the test
 * compiles against the Aprism repo alone); run {@code ./gradlew packageAep}
 * in the AprismRefract fabric branch first.
 *
 * @author BlockConnect@StarsailsClover
 */
class RefractFabricAepE2ETest {

    /** The Refract-built Fabric-Support .aep produced by the fabric branch. */
    private static final Path REFRACT_AEP = Paths.get(
            "..", "..", "AprismRefract", "build", "aprism",
            "Fabric-Support-A[26.0,27.0)-Fa[0.16,0.17)-JE-26.2.aep");

    private static final String FABRIC_MOD_CLASS = "com.aprism.loader.testmods.FabricRecordingMod";

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
        FabricRecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    @EnabledIf("refractAepPresent")
    void refractBuiltAepRegistersFabricFolderAndInvokesMod() throws Exception {
        // Phase 1: install the Refract-built Fabric-Support.aep
        Path extDir = gameRoot.resolve("aprism-extensions");
        Files.createDirectories(extDir);
        Files.copy(REFRACT_AEP, extDir.resolve("Fabric-Support.aep"),
                StandardCopyOption.REPLACE_EXISTING);

        // Phase 2: a genuine Fabric-style mod in fabric-mods/
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"), "fabricmod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // The Refract extension registered the Fabric loader folder
        assertThat(runtime.getLoaderFolders()).containsEntry("Fa", "fabric-mods");
        // The extension entrypoint was instantiated (from the Refract-bundled jar)
        assertThat(runtime.getExtension("fabric-support")).isNotNull();

        // The Fabric mod was discovered and its INIT entrypoint invoked
        assertThat(runtime.getMods()).hasSize(1);
        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(FabricRecordingMod.getGlobalCalls()).contains("main");
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
}
