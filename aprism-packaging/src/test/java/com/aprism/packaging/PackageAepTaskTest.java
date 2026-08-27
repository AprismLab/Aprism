package com.aprism.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TestKit coverage for the optional AprismWarp editor catalog in an AEP.
 *
 * @author BlockConnect@StarsailsClover
 */
class PackageAepTaskTest {

    @TempDir
    Path projectDir;

    @Test
    void packageAepIncludesEditorManifestAtArchiveRoot() throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'extension'\n");
        Files.writeString(projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.aprism.packaging'
                }
                aprismPackaging {
                    manifestFile = 'aprism.extension.json'
                    editorManifestFile = 'aprismwarp.editor.json'
                }
                packageAep {
                    inputJars = files('extension.jar')
                    outputFile = file('build/aprism/extension.aep')
                }
                """);
        Files.writeString(projectDir.resolve("aprism.extension.json"), "{}\n");
        Files.writeString(projectDir.resolve("aprismwarp.editor.json"), """
                {
                  "schema": "aprismwarp.aep-editor/v1",
                  "extensionId": "example-extension",
                  "requires": {
                    "aprismRange": ">=26.8.0",
                    "workTypes": ["AprismExtension"]
                  },
                  "capabilities": []
                }
                """);
        Files.writeString(projectDir.resolve("extension.jar"), "placeholder");

        GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("packageAep", "--stacktrace")
                .forwardOutput()
                .build();

        Path aep = projectDir.resolve("build/aprism/extension.aep");
        assertThat(aep).exists();
        try (ZipFile zip = new ZipFile(aep.toFile())) {
            assertThat(zip.getEntry("aprism.extension.json")).isNotNull();
            assertThat(zip.getEntry("aprismwarp.editor.json")).isNotNull();
            assertThat(zip.getEntry("extension.jar")).isNotNull();
        }
    }
}
