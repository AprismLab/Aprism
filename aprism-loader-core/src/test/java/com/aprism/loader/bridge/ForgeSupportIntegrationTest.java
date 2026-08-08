package com.aprism.loader.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
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
import com.aprism.loader.LoadedModContainer;
import com.aprism.loader.testmods.ForgeRecordingMod;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * End-to-end integration test for the Forge-Support path: a genuine
 * Forge-style mod (a {@code net.minecraftforge.fml.common.Mod}-annotated
 * class with an {@code IEventBus} constructor) is packaged into a jar with
 * {@code META-INF/mods.toml}, discovered in {@code forge-mods/} via the
 * Forge-Support extension, and constructed during the INIT phase via the
 * {@link ForgeEntrypointBridge}.
 *
 * <p>Forge entrypoints are NOT declared in the manifest (they are
 * annotation-discovered), so this test embeds the real {@code @Mod} class
 * bytes into the mod jar and asserts through {@code container.getInstance()}
 * + reflection (the runtime defines a child-classloader copy of the class).
 *
 * @author BlockConnect@StarsailsClover
 */
class ForgeSupportIntegrationTest {

    private static final String MOD_CLASS = "com.aprism.loader.testmods.ForgeRecordingMod";

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        ForgeRecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void forgeModDiscoveredAndConstructedOnInit() throws Exception {
        writeForgeSupportAep(gameRoot.resolve("aprism-extensions/Forge-Support.aep"));
        writeForgeModJar(gameRoot.resolve("forge-mods/forgemod.jar"), "forgemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        assertThat(runtime.getLoaderFolders()).containsEntry("Fo", "forge-mods");
        assertThat(runtime.getMods()).hasSize(1);
        assertThat(runtime.getMods().get(0).getId()).isEqualTo("forgemod");
        assertThat(runtime.getMods().get(0).getLoaderKey()).isEqualTo("Fo");

        // INIT constructs the @Mod class with an injected IEventBus
        runtime.invokeEntrypoints(AprismPhase.INIT);
        LoadedModContainer container = runtime.getMods().get(0);
        assertThat(container.getInstance()).isNotNull();
        assertThat(container.getInstance().getClass().getSimpleName())
                .isEqualTo("ForgeRecordingMod");

        // The injected bus is observable through reflection (child-classloader copy)
        Object bus = container.getInstance().getClass()
                .getDeclaredMethod("getInjectedBus").invoke(container.getInstance());
        assertThat(bus).isNotNull();
        assertThat(bus).isInstanceOf(IEventBus.class);
    }

    @Test
    void forgeNonInitPhasesAreNoOps() throws Exception {
        writeForgeSupportAep(gameRoot.resolve("aprism-extensions/Forge-Support.aep"));
        writeForgeModJar(gameRoot.resolve("forge-mods/forgemod.jar"), "forgemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // PREINIT/SETUP/COMPLETE do not construct a Forge mod
        runtime.invokeEntrypoints(AprismPhase.PREINIT);
        runtime.invokeEntrypoints(AprismPhase.SETUP);
        runtime.invokeEntrypoints(AprismPhase.COMPLETE);
        assertThat(runtime.getMods().get(0).getInstance()).isNull();

        // INIT is the only phase that constructs
        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(runtime.getMods().get(0).getInstance()).isNotNull();
    }

    @Test
    void forgeConstructedOnlyOnce() throws Exception {
        writeForgeSupportAep(gameRoot.resolve("aprism-extensions/Forge-Support.aep"));
        writeForgeModJar(gameRoot.resolve("forge-mods/forgemod.jar"), "forgemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        runtime.invokeEntrypoints(AprismPhase.INIT);
        Object first = runtime.getMods().get(0).getInstance();
        runtime.invokeEntrypoints(AprismPhase.INIT);
        // Repeated INIT must not re-construct (idempotent)
        assertThat(runtime.getMods().get(0).getInstance()).isSameAs(first);
    }

    // ----- fixture helpers -----

    private static void writeForgeSupportAep(Path aepFile) throws IOException {
        Files.createDirectories(aepFile.getParent());
        String json = """
                {
                  "extensionId": "forge-support",
                  "type": "loader-support",
                  "aprismRange": "[26.0.0,27.0.0)",
                  "loaderKey": "Fo",
                  "loaderRange": "[47.0.0,48.0.0)",
                  "mcEdit": null,
                  "mcVersion": null,
                  "entrypoint": "com.aprism.ext.forge.ForgeSupportExtension",
                  "provides": ["forge-loader"],
                  "depends": {}
                }
                """;
        writeZipEntry(aepFile, "aprism.extension.json", json);
    }

    /**
     * Writes a Forge mod jar containing a {@code META-INF/mods.toml} and the
     * real {@code @Mod} class bytes. The class bytes are embedded so the
     * runtime's annotation scan discovers the entrypoint inside the jar.
     */
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

            // Embed the @Mod entrypoint class bytes
            String classPath = MOD_CLASS.replace('.', '/') + ".class";
            zos.putNextEntry(new ZipEntry(classPath));
            try (InputStream in = ForgeRecordingMod.class.getResourceAsStream(
                    "ForgeRecordingMod.class")) {
                zos.write(in.readAllBytes());
            }
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
