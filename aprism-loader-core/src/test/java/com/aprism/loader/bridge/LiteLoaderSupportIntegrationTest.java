package com.aprism.loader.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
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
import com.aprism.loader.testmods.LiteModRecordingMod;

/**
 * End-to-end integration test for the LiteLoader-Support path: a genuine
 * LiteLoader-style mod (implementing
 * {@code com.mumfrey.liteloader.core.LiteMod}) is packaged into a
 * {@code .litemod} archive with {@code litemod.json}, discovered in
 * {@code liteloader-mods/} via the LiteLoader-Support extension, and its
 * {@code init(File)} entrypoint invoked during the INIT phase via the
 * {@link LiteLoaderEntrypointBridge}.
 *
 * <p>LiteLoader entrypoints are NOT declared in the manifest; the mod class
 * is discovered by bytecode scanning for the {@code LiteMod} interface.
 *
 * @author BlockConnect@StarsailsClover
 */
class LiteLoaderSupportIntegrationTest {

    private static final String MOD_CLASS = "com.aprism.loader.testmods.LiteModRecordingMod";

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        LiteModRecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void liteModDiscoveredAndInitInvoked() throws Exception {
        writeLiteLoaderSupportAep(gameRoot.resolve("aprism-extensions/LiteLoader-Support.aep"));
        writeLiteMod(gameRoot.resolve("liteloader-mods/litemod.litemod"), "litemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        assertThat(runtime.getLoaderFolders()).containsEntry("L", "liteloader-mods");
        assertThat(runtime.getMods()).hasSize(1);
        assertThat(runtime.getMods().get(0).getId()).isEqualTo("litemod");
        assertThat(runtime.getMods().get(0).getLoaderKey()).isEqualTo("L");

        // INIT discovers the LiteMod class and invokes init(File)
        runtime.invokeEntrypoints(AprismPhase.INIT);
        LoadedModContainer container = runtime.getMods().get(0);
        assertThat(container.getInstance()).isNotNull();
        assertThat(container.getInstance().getClass().getSimpleName())
                .isEqualTo("LiteModRecordingMod");

        // init(File) was invoked with the mod's config folder
        Object received = container.getInstance().getClass()
                .getDeclaredMethod("getReceivedConfigFolder").invoke(container.getInstance());
        assertThat(received).isInstanceOf(File.class);
        assertThat(((File) received).getName()).isEqualTo("litemod");
    }

    @Test
    void liteModNonInitPhasesAreNoOps() throws Exception {
        writeLiteLoaderSupportAep(gameRoot.resolve("aprism-extensions/LiteLoader-Support.aep"));
        writeLiteMod(gameRoot.resolve("liteloader-mods/litemod.litemod"), "litemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        runtime.invokeEntrypoints(AprismPhase.PREINIT);
        runtime.invokeEntrypoints(AprismPhase.SETUP);
        runtime.invokeEntrypoints(AprismPhase.COMPLETE);
        assertThat(runtime.getMods().get(0).getInstance()).isNull();

        runtime.invokeEntrypoints(AprismPhase.INIT);
        assertThat(runtime.getMods().get(0).getInstance()).isNotNull();
    }

    @Test
    void liteModInitInvokedOnlyOnce() throws Exception {
        writeLiteLoaderSupportAep(gameRoot.resolve("aprism-extensions/LiteLoader-Support.aep"));
        writeLiteMod(gameRoot.resolve("liteloader-mods/litemod.litemod"), "litemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        runtime.invokeEntrypoints(AprismPhase.INIT);
        Object first = runtime.getMods().get(0).getInstance();
        runtime.invokeEntrypoints(AprismPhase.INIT);
        // Repeated INIT must not re-invoke init(File) (idempotent)
        assertThat(runtime.getMods().get(0).getInstance()).isSameAs(first);
    }

    // ----- fixture helpers -----

    private static void writeLiteLoaderSupportAep(Path aepFile) throws IOException {
        Files.createDirectories(aepFile.getParent());
        String json = """
                {
                  "extensionId": "liteloader-support",
                  "type": "loader-support",
                  "aprismRange": "[26.0.0,27.0.0)",
                  "loaderKey": "L",
                  "loaderRange": "[1.12.0,1.13.0)",
                  "mcEdit": null,
                  "mcVersion": null,
                  "entrypoint": "com.aprism.ext.liteloader.LiteLoaderSupportExtension",
                  "provides": ["liteloader"],
                  "depends": {}
                }
                """;
        writeZipEntry(aepFile, "aprism.extension.json", json);
    }

    /**
     * Writes a {@code .litemod} archive containing a {@code litemod.json} and
     * the real {@code LiteMod}-implementing class bytes. The class bytes are
     * embedded so the runtime's interface scan discovers the entrypoint.
     */
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

            // Embed the LiteMod entrypoint class bytes
            String classPath = MOD_CLASS.replace('.', '/') + ".class";
            zos.putNextEntry(new ZipEntry(classPath));
            try (InputStream in = LiteModRecordingMod.class.getResourceAsStream(
                    "LiteModRecordingMod.class")) {
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
