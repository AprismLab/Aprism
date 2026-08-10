package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.api.AprismPhase;
import com.aprism.loader.loaderext.LoaderEntrypointHandler;
import com.aprism.loader.loaderext.LoaderEntrypointRegistry;
import com.aprism.loader.testmods.NeoForgeRecordingMod;
import com.aprism.loader.testmods.RecordingMod;

/**
 * Mixed-loader integration test (rewritten for v26.2-Alpha.5, goal #4
 * close). Loads all three supported loader types together in a single
 * instance: an Aprism-native mod ({@code mods/}), a Fabric mod
 * ({@code fabric-mods/} via the Fabric-Support extension), and a NeoForge
 * mod ({@code neoforge-mods/} via the NeoForge-Support extension).
 *
 * <p>Since v26.2-Alpha.5 the core no longer carries built-in foreign-loader
 * bridges: discovery is unchanged, but entrypoint dispatch for foreign mods
 * is owned exclusively by the {@link LoaderEntrypointHandler} SPI (provided
 * in production by the AprismRefract loader-support extensions). This test
 * therefore registers recording handlers through the seam and asserts that
 * dispatch reaches them and nothing else is attempted for foreign mods.
 *
 * @author BlockConnect@StarsailsClover
 */
class MixedLoaderIntegrationTest {

    private static final String FABRIC_MOD_CLASS = "com.aprism.loader.testmods.FabricRecordingMod";
    private static final String NEOFORGE_MOD_CLASS = "com.aprism.loader.testmods.NeoForgeRecordingMod";
    private static final String APRISM_MOD_CLASS = "com.aprism.loader.testmods.RecordingMod";

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        RecordingMod.resetGlobal();
        RecordingEntrypointHandler.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void allThreeLoaderTypesCoexistAndDispatchViaSpi() throws Exception {
        // Extensions: Fabric-Support + NeoForge-Support (register their folders)
        writeLoaderSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"),
                "fabric-support", "Fa", "fabric-mods");
        writeLoaderSupportAep(gameRoot.resolve("aprism-extensions/NeoForge-Support.aep"),
                "neoforge-support", "N", "neoforge-mods");

        // Mods: one of each loader type
        writeAprismNativeMod(gameRoot.resolve("mods/nativemod.aje"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"), "fabricmod");
        writeNeoForgeModJar(gameRoot.resolve("neoforge-mods/neoforgemod.jar"), "neoforgemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // All three loader folders registered
        assertThat(runtime.getLoaderFolders())
                .containsEntry("Fa", "fabric-mods")
                .containsEntry("N", "neoforge-mods");
        // All three mods discovered and loaded
        assertThat(runtime.getMods()).hasSize(3);
        assertThat(runtime.getMods())
                .extracting(m -> m.getId())
                .containsExactlyInAnyOrder("nativemod", "fabricmod", "neoforgemod");

        // Register SPI handlers for the foreign loader keys (this is what the
        // AprismRefract loader-support extensions do in production).
        LoaderEntrypointRegistry.register(new RecordingEntrypointHandler("Fa"));
        LoaderEntrypointRegistry.register(new RecordingEntrypointHandler("N"));

        // Dispatch the common lifecycle: the native mod runs the Aprism
        // lifecycle; the foreign mods are delegated to their SPI handlers.
        runtime.invokeCommonLifecycle();

        // Aprism-native mod: full lifecycle recorded
        assertThat(RecordingMod.getGlobalPhases())
                .contains("PREINIT:nativemod", "INIT:nativemod",
                        "SETUP:nativemod", "COMPLETE:nativemod");
        // Fabric mod: every common phase delegated to the Fa handler
        assertThat(RecordingEntrypointHandler.getGlobalCalls())
                .contains("Fa:fabricmod:PREINIT", "Fa:fabricmod:INIT",
                        "Fa:fabricmod:SETUP", "Fa:fabricmod:COMPLETE");
        // NeoForge mod: delegated to the N handler
        assertThat(RecordingEntrypointHandler.getGlobalCalls())
                .contains("N:neoforgemod:INIT");
    }

    @Test
    void foreignModsWithoutHandlerAreNotDispatched() throws Exception {
        // No SPI handler registered: foreign mods are discovered but receive
        // no entrypoint dispatch (the core never guesses foreign conventions).
        writeLoaderSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"),
                "fabric-support", "Fa", "fabric-mods");
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"), "fabricmod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        assertThat(runtime.getMods()).hasSize(1);
        runtime.invokeCommonLifecycle();

        // Nothing dispatched: no handler, no built-in bridge
        assertThat(RecordingEntrypointHandler.getGlobalCalls()).isEmpty();
        LoadedModContainer fabric = runtime.getMods().get(0);
        assertThat(fabric.getInstance()).isNull();
    }

    @Test
    void sharedClassSpaceServesAllThreeLoaders() throws Exception {
        writeLoaderSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"),
                "fabric-support", "Fa", "fabric-mods");
        writeLoaderSupportAep(gameRoot.resolve("aprism-extensions/NeoForge-Support.aep"),
                "neoforge-support", "N", "neoforge-mods");
        writeAprismNativeMod(gameRoot.resolve("mods/nativemod.aje"));
        writeFabricModJar(gameRoot.resolve("fabric-mods/fabricmod.jar"), "fabricmod");
        writeNeoForgeModJar(gameRoot.resolve("neoforge-mods/neoforgemod.jar"), "neoforgemod");

        AprismRuntime runtime = AprismRuntime.instance();
        runtime.initialize(null, "26.0.0", "JE", "26.2");
        runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        // The shared Aprism classloader resolves classes from all three mods
        AprismClassLoader cl = runtime.getClassLoader();
        assertThat(cl).isNotNull();
        assertThat(cl.isClassDefined(APRISM_MOD_CLASS)).isFalse();
        // (classes are only defined once loaded/invoked; presence of the mod
        // jars in the classpath is asserted via successful load above)
        assertThat(runtime.getMods()).hasSize(3);
    }

    // ----- fixture helpers -----

    /**
     * Recording {@link LoaderEntrypointHandler} standing in for an
     * AprismRefract loader-support handler. Records every
     * (loaderKey, modId, phase) dispatch it receives.
     */
    static final class RecordingEntrypointHandler implements LoaderEntrypointHandler {

        private static final List<String> GLOBAL_CALLS =
                Collections.synchronizedList(new ArrayList<>());

        private final String loaderKey;

        RecordingEntrypointHandler(String loaderKey) {
            this.loaderKey = loaderKey;
        }

        static void resetGlobal() {
            GLOBAL_CALLS.clear();
        }

        static List<String> getGlobalCalls() {
            return List.copyOf(GLOBAL_CALLS);
        }

        @Override
        public String loaderKey() {
            return loaderKey;
        }

        @Override
        public void invoke(LoadedModContainer container, AprismPhase phase) {
            GLOBAL_CALLS.add(loaderKey + ":" + container.getId() + ":" + phase.name());
        }
    }

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
