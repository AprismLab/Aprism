package com.aprism.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Functional tests for the Aprism packaging plugin, executed via the Gradle
 * TestKit against a real (generated) project. Verifies that {@code packageAje}
 * produces an archive that matches the canonical {@code .aje} structure:
 *
 * <pre>
 * +-- aprism.manifest.json
 * +-- &lt;modid&gt;.jar          (root, named after the manifest id)
 * +-- mixins/               (when present)
 * +-- checksums.txt next to the archive
 * </pre>
 *
 * <p>No per-loader subdirectories or {@code jars/} collections may appear.
 *
 * @author BlockConnect@StarsailsClover
 */
class PackageAjeTaskTest {

    @TempDir
    Path projectDir;

    private static final String MANIFEST = """
            {
              "schemaVersion": 1,
              "id": "examplemod",
              "version": "1.0.0",
              "displayName": "Example Mod",
              "description": "Aprism example mod",
              "environment": "*",
              "entrypoints": {"main": ["com.example.ExampleMod"]},
              "mixins": [],
              "depends": {},
              "platforms": {},
              "accessWidener": null,
              "provides": [],
              "custom": {}
            }
            """;

    @Test
    void packageAjeProducesCanonicalStructure() throws IOException {
        // Generated project: java plugin + aprism packaging plugin
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'examplemod'\n");
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.aprism.packaging'
                }
                group = 'com.example'
                version = '1.0.0'
                aprismPackaging {
                    manifestFile = 'aprism.manifest.json'
                }
                """);
        Files.writeString(projectDir.resolve("aprism.manifest.json"), MANIFEST);

        // One source file so the jar is non-empty
        Path src = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(src);
        Files.writeString(src.resolve("ExampleMod.java"), """
                package com.example;
                public final class ExampleMod {
                    public ExampleMod() { }
                }
                """);

        // A mixin config so mixins/ is exercised
        Path mixinDir = projectDir.resolve("src/main/mixins");
        Files.createDirectories(mixinDir);
        Files.writeString(mixinDir.resolve("example.mixins.json"),
                "{\"package\": \"com.example.mixin\", \"mixins\": []}");

        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("packageAje", "--stacktrace")
                .forwardOutput()
                .build();

        Path aje = projectDir.resolve("build/aprism/examplemod-1.0.0.aje");
        assertThat(aje).exists();

        Set<String> entries = new HashSet<>();
        try (ZipFile zf = new ZipFile(aje.toFile())) {
            var e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                entries.add(entry.getName());
            }
        }

        // Canonical structure
        assertThat(entries)
                .as("manifest at archive root")
                .contains("aprism.manifest.json");
        assertThat(entries)
                .as("main jar named <modid>.jar at archive root")
                .contains("examplemod.jar");
        assertThat(entries)
                .as("mixin config under mixins/")
                .contains("mixins/example.mixins.json");

        // Structural purity: no per-loader subdirs, no jars/ collection
        for (String name : entries) {
            assertThat(name)
                    .as("no loader-specific or jars/ entries allowed")
                    .doesNotContain("fabric/")
                    .doesNotContain("neoforge/")
                    .doesNotStartWith("jars/");
        }

        // checksums.txt written next to the archive
        Path checksums = projectDir.resolve("build/aprism/checksums.txt");
        assertThat(checksums).exists();
        assertThat(Files.readString(checksums, StandardCharsets.UTF_8))
                .contains("examplemod-1.0.0.aje")
                .contains("aprism.manifest.json")
                .contains("examplemod.jar");
    }

    @Test
    void packageAjeFailsWithoutManifest() throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'nomanifest'\n");
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.aprism.packaging'
                }
                aprismPackaging {
                    manifestFile = 'missing-manifest.json'
                }
                """);

        var result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("packageAje")
                .forwardOutput()
                .buildAndFail();

        // The manifest is a required @InputFile: Gradle fails the task with a
        // missing-input validation error before the task action runs.
        assertThat(result.getOutput())
                .contains("property 'manifestFile' specifies file")
                .contains("which doesn't exist");
    }
}
