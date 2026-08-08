package com.aprism.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the Quilt manifest projection
 * ({@link ManifestParser#tryParseQuiltManifest}) covering the
 * {@code quilt_loader} identity block, {@code metadata} display fields, and
 * the three entrypoint declaration forms Quilt allows (bare class name,
 * {@code {"value": ...}} object, and arrays of either). The Quilt-native
 * {@code init} key must project to {@code main}; {@code client} and
 * {@code server} pass through unchanged.
 *
 * @author BlockConnect@StarsailsClover
 */
class QuiltManifestProjectionTest {

    @TempDir
    Path tempDir;

    private final ManifestParser parser = new ManifestParser();

    private Path writeJar(String quiltJson) throws IOException {
        Path jar = tempDir.resolve("quiltmod.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("quilt.mod.json"));
            zos.write(quiltJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return jar;
    }

    @Test
    void projectsIdentityFromQuiltLoaderBlock() throws Exception {
        Path jar = writeJar("""
                {
                  "schema_version": 1,
                  "quilt_loader": {
                    "group": "com.example",
                    "id": "quiltmod",
                    "version": "2.3.4",
                    "metadata": {
                      "name": "Quilt Example",
                      "description": "A Quilt mod"
                    },
                    "entrypoints": {}
                  }
                }
                """);
        AprismManifest m = parser.tryParseQuiltManifest(jar).orElseThrow();
        assertThat(m.id()).isEqualTo("quiltmod");
        assertThat(m.version()).isEqualTo("2.3.4");
        assertThat(m.displayName()).isEqualTo("Quilt Example");
        assertThat(m.description()).isEqualTo("A Quilt mod");
    }

    @Test
    void initKeyProjectsToMain() throws Exception {
        Path jar = writeJar("""
                {
                  "quilt_loader": {
                    "id": "quiltmod",
                    "version": "1.0.0",
                    "entrypoints": { "init": ["com.example.Main"] }
                  }
                }
                """);
        AprismManifest m = parser.tryParseQuiltManifest(jar).orElseThrow();
        assertThat(m.entrypoints()).containsKey("main");
        assertThat(m.entrypoints().get("main")).containsExactly("com.example.Main");
    }

    @Test
    void clientAndServerKeysPassThrough() throws Exception {
        Path jar = writeJar("""
                {
                  "quilt_loader": {
                    "id": "quiltmod",
                    "version": "1.0.0",
                    "entrypoints": {
                      "client": ["com.example.Client"],
                      "server": ["com.example.Server"]
                    }
                  }
                }
                """);
        AprismManifest m = parser.tryParseQuiltManifest(jar).orElseThrow();
        assertThat(m.entrypoints().get("client")).containsExactly("com.example.Client");
        assertThat(m.entrypoints().get("server")).containsExactly("com.example.Server");
    }

    @Test
    void objectFormEntrypointProjectsValue() throws Exception {
        Path jar = writeJar("""
                {
                  "quilt_loader": {
                    "id": "quiltmod",
                    "version": "1.0.0",
                    "entrypoints": {
                      "init": [{ "value": "com.example.Adapted", "adapter": "default" }]
                    }
                  }
                }
                """);
        AprismManifest m = parser.tryParseQuiltManifest(jar).orElseThrow();
        assertThat(m.entrypoints().get("main")).containsExactly("com.example.Adapted");
    }

    @Test
    void mixedArrayEntrypointsProjectAllClassNames() throws Exception {
        Path jar = writeJar("""
                {
                  "quilt_loader": {
                    "id": "quiltmod",
                    "version": "1.0.0",
                    "entrypoints": {
                      "init": [
                        "com.example.Bare",
                        { "value": "com.example.Object" }
                      ]
                    }
                  }
                }
                """);
        AprismManifest m = parser.tryParseQuiltManifest(jar).orElseThrow();
        assertThat(m.entrypoints().get("main"))
                .containsExactly("com.example.Bare", "com.example.Object");
    }

    @Test
    void absentQuiltManifestReturnsEmpty() throws Exception {
        Path jar = tempDir.resolve("plain.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("fabric.mod.json"));
            zos.write("{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertThat(parser.tryParseQuiltManifest(jar)).isEmpty();
    }

    @Test
    void missingMetadataFallsBackToIdForDisplayName() throws Exception {
        Path jar = writeJar("""
                {
                  "quilt_loader": {
                    "id": "quiltmod",
                    "version": "1.0.0",
                    "entrypoints": {}
                  }
                }
                """);
        AprismManifest m = parser.tryParseQuiltManifest(jar).orElseThrow();
        assertThat(m.displayName()).isEqualTo("quiltmod");
        assertThat(m.description()).isEmpty();
    }
}
