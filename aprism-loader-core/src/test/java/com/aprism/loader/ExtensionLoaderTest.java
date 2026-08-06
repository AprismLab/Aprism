package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.manifest.AprismExtensionManifest;

/**
 * JUnit 5 + AssertJ tests for {@link ExtensionLoader}.
 *
 * <p>Builds synthetic {@code .aep} zip archives with an
 * {@code aprism.extension.json} manifest and verifies version-range filtering,
 * loader-support folder registration, and validation behavior.
 *
 * @author BlockConnect@StarsailsClover
 */
class ExtensionLoaderTest {

    @TempDir
    Path tempDir;

    private Path extensionsDir;

    @BeforeEach
    void setUp() throws IOException {
        extensionsDir = tempDir.resolve("aprism-extensions");
        Files.createDirectories(extensionsDir);
    }

    @AfterEach
    void tearDown() {
        // @TempDir handles cleanup
    }

    @Nested
    class BasicLoading {
        @Test
        void loadsValidExtension() throws IOException {
            writeAep(extensionsDir.resolve("Fabric-Support-A[26.0.0,27.0.0)-Fa[0.16.0,0.17.0)-JE-1.21.4.aep"),
                    """
                    {
                      "extensionId": "fabric-support",
                      "type": "loader-support",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": "Fa",
                      "loaderRange": "[0.16.0,0.17.0)",
                      "mcEdit": "JE",
                      "mcVersion": "1.21.4",
                      "entrypoint": "com.example.FabricSupport",
                      "provides": ["fabric-loader"],
                      "depends": {}
                    }
                    """);

            // aprismVersion "26.0.0" satisfies range "[26.0.0,27.0.0)"
            // (note: "26.0.0-Alpha.1" is a prerelease and is < "26.0.0" per SemVer)
            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(extensionsDir);

            assertThat(loaded).hasSize(1);
            AprismExtensionManifest m = loaded.get(0).manifest();
            assertThat(m.extensionId()).isEqualTo("fabric-support");
            assertThat(m.type()).isEqualTo("loader-support");
            assertThat(m.loaderKey()).isEqualTo("Fa");
        }

        @Test
        void emptyDirectoryReturnsEmpty() {
            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(extensionsDir);
            assertThat(loaded).isEmpty();
        }

        @Test
        void missingDirectoryReturnsEmpty() {
            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(tempDir.resolve("does-not-exist"));
            assertThat(loaded).isEmpty();
        }

        @Test
        void nonAepFilesIgnored() throws IOException {
            Files.writeString(extensionsDir.resolve("readme.txt"), "not an extension");
            Files.writeString(extensionsDir.resolve("random.jar"), "not a zip");
            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(extensionsDir);
            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    class VersionValidation {
        @Test
        void aprismVersionOutOfRangeRejected() throws IOException {
            writeAep(extensionsDir.resolve("Out-Of-Range.aep"),
                    """
                    {
                      "extensionId": "out-of-range",
                      "type": "api-extension",
                      "aprismRange": "[27.0.0,28.0.0)",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": null,
                      "mcVersion": null,
                      "entrypoint": "com.example.OutOfRange",
                      "provides": [],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(extensionsDir);
            assertThat(loaded).isEmpty();
        }

        @Test
        void aprismVersionInRangeAccepted() throws IOException {
            writeAep(extensionsDir.resolve("In-Range.aep"),
                    """
                    {
                      "extensionId": "in-range",
                      "type": "api-extension",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": null,
                      "mcVersion": null,
                      "entrypoint": "com.example.InRange",
                      "provides": [],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loader = new ExtensionLoader("26.5.2", "JE", "1.21.4");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(extensionsDir);
            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).manifest().extensionId()).isEqualTo("in-range");
        }

        @Test
        void mcEditMismatchRejected() throws IOException {
            writeAep(extensionsDir.resolve("BE-Only.aep"),
                    """
                    {
                      "extensionId": "be-only",
                      "type": "platform-adapter",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": "BE",
                      "mcVersion": "26.2",
                      "entrypoint": "com.example.BeOnly",
                      "provides": [],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(extensionsDir);
            assertThat(loaded).isEmpty();
        }

        @Test
        void mcVersionMismatchRejected() throws IOException {
            writeAep(extensionsDir.resolve("Wrong-Ver.aep"),
                    """
                    {
                      "extensionId": "wrong-ver",
                      "type": "api-extension",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": "JE",
                      "mcVersion": "1.21.4",
                      "entrypoint": "com.example.WrongVer",
                      "provides": [],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.10");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(extensionsDir);
            assertThat(loaded).isEmpty();
        }

        @Test
        void nullMcEditAcceptsAnyEdition() throws IOException {
            writeAep(extensionsDir.resolve("Any-Edition.aep"),
                    """
                    {
                      "extensionId": "any-edition",
                      "type": "api-extension",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": null,
                      "mcVersion": null,
                      "entrypoint": "com.example.AnyEdition",
                      "provides": [],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loaderBe = new ExtensionLoader("26.0.0", "BE", "26.2");
            assertThat(loaderBe.load(extensionsDir)).hasSize(1);

            ExtensionLoader loaderJe = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            assertThat(loaderJe.load(extensionsDir)).hasSize(1);
        }
    }

    @Nested
    class LoaderSupportFolders {
        @Test
        void registersFabricFolder() throws IOException {
            writeAep(extensionsDir.resolve("Fabric-Support.aep"),
                    """
                    {
                      "extensionId": "fabric-support",
                      "type": "loader-support",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": "Fa",
                      "loaderRange": "[0.16.0,0.17.0)",
                      "mcEdit": "JE",
                      "mcVersion": "1.21.4",
                      "entrypoint": "com.example.FabricSupport",
                      "provides": ["fabric-loader"],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            loader.load(extensionsDir);

            Map<String, String> folders = loader.getLoaderFolders();
            assertThat(folders).containsEntry("Fa", "fabric-mods");
        }

        @Test
        void registersAllKnownLoaders() throws IOException {
            writeAep(extensionsDir.resolve("Fabric.aep"), loaderSupportJson("Fa"));
            writeAep(extensionsDir.resolve("Forge.aep"), loaderSupportJson("Fo"));
            writeAep(extensionsDir.resolve("NeoForge.aep"), loaderSupportJson("N"));
            writeAep(extensionsDir.resolve("LiteLoader.aep"), loaderSupportJson("L"));
            writeAep(extensionsDir.resolve("Quilt.aep"), loaderSupportJson("Q"));

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            loader.load(extensionsDir);

            Map<String, String> folders = loader.getLoaderFolders();
            assertThat(folders).containsEntry("Fa", "fabric-mods");
            assertThat(folders).containsEntry("Fo", "forge-mods");
            assertThat(folders).containsEntry("N", "neoforge-mods");
            assertThat(folders).containsEntry("L", "liteloader-mods");
            assertThat(folders).containsEntry("Q", "quilt-mods");
        }

        @Test
        void nonLoaderSupportDoesNotRegisterFolder() throws IOException {
            writeAep(extensionsDir.resolve("Api-Ext.aep"),
                    """
                    {
                      "extensionId": "api-ext",
                      "type": "api-extension",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": null,
                      "mcVersion": null,
                      "entrypoint": "com.example.ApiExt",
                      "provides": ["extra-api"],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            loader.load(extensionsDir);

            assertThat(loader.getLoaderFolders()).isEmpty();
            assertThat(loader.load(extensionsDir)).hasSize(1);
        }

        @Test
        void multipleLoaderSupportExtensionsAllRegister() throws IOException {
            writeAep(extensionsDir.resolve("Fabric.aep"), loaderSupportJson("Fa"));
            writeAep(extensionsDir.resolve("NeoForge.aep"), loaderSupportJson("N"));

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(extensionsDir);

            assertThat(loaded).hasSize(2);
            assertThat(loader.getLoaderFolders()).hasSize(2);
        }
    }

    @Nested
    class ManifestValidation {
        @Test
        void missingManifestSkipped() throws IOException {
            // .aep with no aprism.extension.json entry
            Path aep = extensionsDir.resolve("Empty.aep");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(aep))) {
                zos.putNextEntry(new ZipEntry("other.txt"));
                zos.write("data".getBytes());
                zos.closeEntry();
            }

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            assertThat(loader.load(extensionsDir)).isEmpty();
        }

        @Test
        void missingExtensionIdRejected() throws IOException {
            writeAep(extensionsDir.resolve("No-Id.aep"),
                    """
                    {
                      "extensionId": "",
                      "type": "api-extension",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": null,
                      "mcVersion": null,
                      "entrypoint": "com.example.NoId",
                      "provides": [],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            assertThat(loader.load(extensionsDir)).isEmpty();
        }

        @Test
        void invalidAprismRangeRejected() throws IOException {
            writeAep(extensionsDir.resolve("Bad-Range.aep"),
                    """
                    {
                      "extensionId": "bad-range",
                      "type": "api-extension",
                      "aprismRange": "not-a-range",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": null,
                      "mcVersion": null,
                      "entrypoint": "com.example.BadRange",
                      "provides": [],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            assertThat(loader.load(extensionsDir)).isEmpty();
        }

        @Test
        void sourcePathRecorded() throws IOException {
            Path aepPath = extensionsDir.resolve("Tracked.aep");
            writeAep(aepPath,
                    """
                    {
                      "extensionId": "tracked",
                      "type": "api-extension",
                      "aprismRange": "[26.0.0,27.0.0)",
                      "loaderKey": null,
                      "loaderRange": null,
                      "mcEdit": null,
                      "mcVersion": null,
                      "entrypoint": "com.example.Tracked",
                      "provides": [],
                      "depends": {}
                    }
                    """);

            ExtensionLoader loader = new ExtensionLoader("26.0.0", "JE", "1.21.4");
            List<ExtensionLoader.LoadedExtension> loaded = loader.load(extensionsDir);
            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).sourcePath()).isEqualTo(aepPath);
        }
    }

    /**
     * Builds a minimal loader-support extension JSON for the given loader key.
     *
     * @param loaderKey the loader key (Fa, Fo, N, L, Q)
     * @return the JSON manifest
     */
    private static String loaderSupportJson(String loaderKey) {
        return """
                {
                  "extensionId": "%s-support",
                  "type": "loader-support",
                  "aprismRange": "[26.0.0,27.0.0)",
                  "loaderKey": "%s",
                  "loaderRange": "[1.0.0,2.0.0)",
                  "mcEdit": null,
                  "mcVersion": null,
                  "entrypoint": "com.example.%sSupport",
                  "provides": ["%s-loader"],
                  "depends": {}
                }
                """.formatted(loaderKey, loaderKey, loaderKey, loaderKey);
    }

    /**
     * Writes a {@code .aep} archive containing a single
     * {@code aprism.extension.json} entry with the given manifest JSON.
     *
     * @param aepFile the destination .aep file
     * @param json    the manifest JSON content
     * @throws IOException if the archive cannot be written
     */
    private static void writeAep(Path aepFile, String json) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(aepFile))) {
            zos.putNextEntry(new ZipEntry("aprism.extension.json"));
            zos.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
