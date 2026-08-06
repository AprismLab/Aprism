package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * JUnit 5 + AssertJ tests for {@link ModDiscoverer}.
 *
 * <p>Verifies that the per-loader folder separation scheme (FACT.md 9.15)
 * scans the Aprism native {@code mods/} folder plus every registered
 * loader-support folder, and that each discovered mod carries the correct
 * loader key.
 *
 * @author BlockConnect@StarsailsClover
 */
class ModDiscovererTest {

    @TempDir
    Path gameRoot;

    private ModDiscoverer discoverer;

    @BeforeEach
    void setUp() {
        discoverer = new ModDiscoverer();
    }

    @Nested
    class NativeOnlyScan {
        @Test
        void emptyGameRootReturnsEmpty() {
            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discoverAll(gameRoot, Map.of());
            assertThat(mods).isEmpty();
        }

        @Test
        void missingModsFolderReturnsEmpty() {
            // No mods/ folder created
            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discoverAll(gameRoot, Map.of());
            assertThat(mods).isEmpty();
        }

        @Test
        void scansAjeFromModsFolder() throws IOException {
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0");

            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discoverAll(gameRoot, Map.of());

            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).manifest().id()).isEqualTo("alpha");
            assertThat(mods.get(0).loaderKey()).isEqualTo(ModDiscoverer.APRISM_NATIVE);
            assertThat(mods.get(0).loaderFolder()).isEqualTo("mods");
            assertThat(mods.get(0).format()).isEqualTo(ModDiscoverer.ModFormat.AJE);
        }

        @Test
        void multipleAjeSortedByPath() throws IOException {
            writeAje(gameRoot.resolve("mods").resolve("zeta.aje"), "zeta", "1.0.0");
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0");

            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discoverAll(gameRoot, Map.of());

            assertThat(mods).hasSize(2);
            assertThat(mods.get(0).manifest().id()).isEqualTo("alpha");
            assertThat(mods.get(1).manifest().id()).isEqualTo("zeta");
        }
    }

    @Nested
    class PerLoaderScan {
        @Test
        void scansFabricFolderWhenRegistered() throws IOException {
            writeAje(gameRoot.resolve("mods").resolve("native.aje"), "native", "1.0.0");
            writeJar(gameRoot.resolve("fabric-mods").resolve("fabricmod.jar"),
                    "fabric.mod.json", fabricModJson("fabricmod"));

            Map<String, String> loaderFolders = Map.of("Fa", "fabric-mods");
            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discoverAll(gameRoot, loaderFolders);

            assertThat(mods).hasSize(2);
            // Sorted by loader key: "Fa" before "aprism"
            assertThat(mods.get(0).loaderKey()).isEqualTo("Fa");
            assertThat(mods.get(0).manifest().id()).isEqualTo("fabricmod");
            assertThat(mods.get(0).loaderFolder()).isEqualTo("fabric-mods");
            assertThat(mods.get(1).loaderKey()).isEqualTo(ModDiscoverer.APRISM_NATIVE);
            assertThat(mods.get(1).manifest().id()).isEqualTo("native");
        }

        @Test
        void unregisteredLoaderFolderNotScanned() throws IOException {
            // fabric-mods/ exists but no Fabric-Support extension registered
            writeAje(gameRoot.resolve("mods").resolve("native.aje"), "native", "1.0.0");
            writeJar(gameRoot.resolve("fabric-mods").resolve("ignored.jar"),
                    "fabric.mod.json", fabricModJson("ignored"));

            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discoverAll(gameRoot, Map.of());

            // Only the Aprism native mod is discovered
            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).manifest().id()).isEqualTo("native");
        }

        @Test
        void scansAllRegisteredLoaders() throws IOException {
            writeJar(gameRoot.resolve("fabric-mods").resolve("fa.jar"),
                    "fabric.mod.json", fabricModJson("famod"));
            writeJar(gameRoot.resolve("neoforge-mods").resolve("nf.jar"),
                    "META-INF/neoforge.mods.toml", neoforgeModsToml("nfmod"));

            Map<String, String> loaderFolders = Map.of(
                    "Fa", "fabric-mods",
                    "N", "neoforge-mods");
            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discoverAll(gameRoot, loaderFolders);

            assertThat(mods).hasSize(2);
            assertThat(mods).extracting(dm -> dm.loaderKey())
                    .containsExactlyInAnyOrder("Fa", "N");
        }

        @Test
        void missingRegisteredFolderSkipped() throws IOException {
            // Only fabric-mods/ exists; neoforge-mods/ not created
            writeJar(gameRoot.resolve("fabric-mods").resolve("fa.jar"),
                    "fabric.mod.json", fabricModJson("famod"));

            Map<String, String> loaderFolders = Map.of(
                    "Fa", "fabric-mods",
                    "N", "neoforge-mods");
            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discoverAll(gameRoot, loaderFolders);

            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).loaderKey()).isEqualTo("Fa");
        }

        @Test
        void groupByLoaderCollectsByLoaderKey() throws IOException {
            writeAje(gameRoot.resolve("mods").resolve("a1.aje"), "a1", "1.0.0");
            writeAje(gameRoot.resolve("mods").resolve("a2.aje"), "a2", "1.0.0");
            writeJar(gameRoot.resolve("fabric-mods").resolve("f1.jar"),
                    "fabric.mod.json", fabricModJson("f1"));

            Map<String, String> loaderFolders = Map.of("Fa", "fabric-mods");
            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discoverAll(gameRoot, loaderFolders);
            Map<String, List<ModDiscoverer.DiscoveredMod>> grouped =
                    ModDiscoverer.groupByLoader(mods);

            assertThat(grouped).containsOnlyKeys(ModDiscoverer.APRISM_NATIVE, "Fa");
            assertThat(grouped.get(ModDiscoverer.APRISM_NATIVE)).hasSize(2);
            assertThat(grouped.get("Fa")).hasSize(1);
        }
    }

    @Nested
    class SingleFolderScan {
        @Test
        void discoverSingleDirectoryTagsAsAprismNative() throws IOException {
            Path modsDir = gameRoot.resolve("custom-mods");
            writeAje(modsDir.resolve("only.aje"), "only", "1.0.0");

            List<ModDiscoverer.DiscoveredMod> mods = discoverer.discover(modsDir);

            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).loaderKey()).isEqualTo(ModDiscoverer.APRISM_NATIVE);
        }

        @Test
        void discoverMissingDirectoryReturnsEmpty() {
            List<ModDiscoverer.DiscoveredMod> mods =
                    discoverer.discover(gameRoot.resolve("does-not-exist"));
            assertThat(mods).isEmpty();
        }
    }

    /**
     * Writes a synthetic {@code .aje} archive with an {@code aprism.manifest.json}
     * entry declaring the given id and version.
     *
     * @param ajeFile the destination .aje file
     * @param id      the mod id
     * @param version the mod version
     * @throws IOException if the archive cannot be written
     */
    private static void writeAje(Path ajeFile, String id, String version) throws IOException {
        Files.createDirectories(ajeFile.getParent());
        String json = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "%s",
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
                """.formatted(id, version, id);
        writeZip(ajeFile, "aprism.manifest.json", json);
    }

    /**
     * Builds a synthetic Fabric mod jar containing a {@code fabric.mod.json}.
     *
     * @param jarFile the destination jar file
     * @param id      the mod id
     * @throws IOException if the jar cannot be written
     */
    private static void writeJar(Path jarFile, String entry, String content) throws IOException {
        Files.createDirectories(jarFile.getParent());
        writeZip(jarFile, entry, content);
    }

    /**
     * Writes a single-entry zip archive.
     *
     * @param file    the destination file
     * @param entry   the entry name
     * @param content the entry content
     * @throws IOException if the archive cannot be written
     */
    private static void writeZip(Path file, String entry, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    /**
     * Builds a minimal {@code fabric.mod.json} for the given mod id.
     *
     * @param id the mod id
     * @return the JSON content
     */
    private static String fabricModJson(String id) {
        return """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "1.0.0",
                  "name": "%s",
                  "environment": "*"
                }
                """.formatted(id, id);
    }

    /**
     * Builds a minimal {@code neoforge.mods.toml} for the given mod id.
     *
     * @param id the mod id
     * @return the TOML content
     */
    private static String neoforgeModsToml(String id) {
        return """
                modLoader="javafml"
                loaderVersion="[1,)"
                license="MIT"

                [[mods]]
                modId="%s"
                version="1.0.0"
                displayName="%s"
                description="test"
                """.formatted(id, id);
    }
}
