package com.aprism.loader.modmenu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.loader.AprismRuntime;

/**
 * Tests for the native Aprism mod list (v26.2-Alpha.2, goal #7):
 * {@link ModListRegistry} unit behaviour and the runtime wiring that rebuilds
 * the registry from loaded containers and load-report failures after every
 * {@code performLoad}.
 *
 * @author BlockConnect@StarsailsClover
 */
class ModListTest {

    @Nested
    class RegistryUnit {

        private ModListEntry mod(String id, ModListState state) {
            return ModListEntry.of(id, "1.0.0", id, "desc", "mod", "aprism", id + ".aje", state);
        }

        private ModListEntry ext(String id, ModListState state) {
            return ModListEntry.of(id, "", id, "loader-support", "extension", "Fa", id + ".aep", state);
        }

        @Test
        void registerAndLookup() {
            ModListRegistry registry = new ModListRegistry();
            registry.register(mod("examplemod", ModListState.LOADED));

            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.get("examplemod")).isPresent();
            assertThat(registry.get("missing")).isEmpty();
        }

        @Test
        void getAllSortedById() {
            ModListRegistry registry = new ModListRegistry();
            registry.register(mod("zeta", ModListState.LOADED));
            registry.register(mod("alpha", ModListState.LOADED));
            registry.register(mod("mid", ModListState.LOADED));

            assertThat(registry.getAll()).extracting(ModListEntry::id)
                    .containsExactly("alpha", "mid", "zeta");
        }

        @Test
        void kindFiltersSplitModsAndExtensions() {
            ModListRegistry registry = new ModListRegistry();
            registry.register(mod("mod-a", ModListState.LOADED));
            registry.register(ext("ext-a", ModListState.LOADED));

            assertThat(registry.getMods()).extracting(ModListEntry::id).containsExactly("mod-a");
            assertThat(registry.getExtensions()).extracting(ModListEntry::id).containsExactly("ext-a");
        }

        @Test
        void failedFilterReturnsOnlyFailed() {
            ModListRegistry registry = new ModListRegistry();
            registry.register(mod("ok", ModListState.LOADED));
            registry.register(mod("broken", ModListState.FAILED));

            assertThat(registry.getFailed()).extracting(ModListEntry::id).containsExactly("broken");
        }

        @Test
        void rebuildReplacesContents() {
            ModListRegistry registry = new ModListRegistry();
            registry.register(mod("old", ModListState.LOADED));
            registry.rebuild(Map.of("new", mod("new", ModListState.LOADED)));

            assertThat(registry.getAll()).extracting(ModListEntry::id).containsExactly("new");
        }

        @Test
        void clearDropsEverything() {
            ModListRegistry registry = new ModListRegistry();
            registry.register(mod("a", ModListState.LOADED));
            registry.clear();

            assertThat(registry.size()).isZero();
            assertThat(registry.getAll()).isEmpty();
        }
    }

    @Nested
    class RuntimeIntegration {

        @TempDir
        Path gameRoot;

        @AfterEach
        void tearDown() {
            AprismRuntime.instance().shutdown();
        }

        @Test
        void loadedModAppearsAsLoadedEntry() throws Exception {
            writeAje(gameRoot.resolve("mods/examplemod.aje"), "examplemod");

            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.2.0", "JE", "26.2");
            runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            ModListEntry entry = runtime.getModList().get("examplemod").orElse(null);
            assertThat(entry).isNotNull();
            assertThat(entry.kind()).isEqualTo("mod");
            assertThat(entry.loaderKey()).isEqualTo("aprism");
            assertThat(entry.state()).isEqualTo(ModListState.LOADED);
            assertThat(entry.source()).isEqualTo("examplemod.aje");
        }

        @Test
        void loadedExtensionAppearsAsExtensionEntry() throws Exception {
            writeLoaderSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));

            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.2.0", "JE", "26.2");
            runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            ModListEntry entry = runtime.getModList().get("fabric-support").orElse(null);
            assertThat(entry).isNotNull();
            assertThat(entry.isExtension()).isTrue();
            assertThat(entry.loaderKey()).isEqualTo("Fa");
            assertThat(entry.state()).isEqualTo(ModListState.LOADED);
        }

        @Test
        void failedExtensionAppearsAsFailedEntry() throws Exception {
            writeLoaderSupportAep(gameRoot.resolve("aprism-extensions/Fabric-Support.aep"));
            writeDependentAep(gameRoot.resolve("aprism-extensions/Dep.aep"));

            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.2.0", "JE", "26.2");
            runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            ModListEntry failed = runtime.getModList().get("dep-ext").orElse(null);
            assertThat(failed).isNotNull();
            assertThat(failed.isFailed()).isTrue();
            assertThat(runtime.getModList().getFailed())
                    .extracting(ModListEntry::id)
                    .contains("dep-ext");
        }

        @Test
        void modListClearedOnShutdown() throws Exception {
            writeAje(gameRoot.resolve("mods/examplemod.aje"), "examplemod");

            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.2.0", "JE", "26.2");
            runtime.performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
            assertThat(runtime.getModList().size()).isPositive();

            runtime.shutdown();
            assertThat(runtime.getModList().size()).isZero();
        }

        // ----- fixture helpers -----

        private static void writeAje(Path ajeFile, String id) throws IOException {
            Files.createDirectories(ajeFile.getParent());
            String json = """
                    {
                      "schemaVersion": 1,
                      "id": "%s",
                      "version": "1.0.0",
                      "displayName": "%s",
                      "description": "test",
                      "environment": "*",
                      "entrypoints": {},
                      "mixins": [],
                      "depends": {},
                      "platforms": {},
                      "accessWidener": null,
                      "provides": [],
                      "custom": {}
                    }
                    """.formatted(id, id);
            writeZip(ajeFile, "aprism.manifest.json", json);
        }

        private static void writeLoaderSupportAep(Path aepFile) throws IOException {
            Files.createDirectories(aepFile.getParent());
            String json = """
                    {
                      "extensionId": "fabric-support",
                      "type": "loader-support",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": "Fa",
                      "loaderRange": "[0.16.0,0.17.0)",
                      "mcEdit": null,
                      "mcVersion": null,
                      "entrypoint": null,
                      "provides": ["fabric-loader"],
                      "depends": {}
                    }
                    """;
            writeZip(aepFile, "aprism.extension.json", json);
        }

        private static void writeDependentAep(Path aepFile) throws IOException {
            Files.createDirectories(aepFile.getParent());
            String json = """
                    {
                      "extensionId": "dep-ext",
                      "type": "api-extension",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": null,
                      "mcVersion": null,
                      "entrypoint": null,
                      "provides": [],
                      "depends": {"ghost-ext": "*"}
                    }
                    """;
            writeZip(aepFile, "aprism.extension.json", json);
        }

        private static void writeZip(Path file, String entry, String content) throws IOException {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
                zos.putNextEntry(new ZipEntry(entry));
                zos.write(content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }
}
