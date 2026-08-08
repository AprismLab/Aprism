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
 * Tests for the LiteLoader manifest projection
 * ({@link ManifestParser#tryParseLiteLoaderManifest}). LiteLoader mods declare
 * identity in {@code litemod.json} ({@code name}, {@code version},
 * {@code mcversion}, {@code revision}, {@code author}, {@code description}).
 * The projection maps {@code name} to the mod id, surfaces {@code mcversion}
 * and {@code revision} into {@code custom}, and declares the {@code client}
 * environment (LiteLoader is a client-side loader).
 *
 * @author BlockConnect@StarsailsClover
 */
class LiteLoaderManifestProjectionTest {

    @TempDir
    Path tempDir;

    private final ManifestParser parser = new ManifestParser();

    private Path writeLiteMod(String json) throws IOException {
        Path jar = tempDir.resolve("mod.litemod");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("litemod.json"));
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return jar;
    }

    @Test
    void projectsIdentityFields() throws Exception {
        Path jar = writeLiteMod("""
                {
                  "name": "voxelmap",
                  "version": "1.12.2",
                  "mcversion": "1.12.2",
                  "revision": 3,
                  "author": "MamiyaOtaru",
                  "description": "A minimap mod"
                }
                """);
        AprismManifest m = parser.tryParseLiteLoaderManifest(jar).orElseThrow();
        assertThat(m.id()).isEqualTo("voxelmap");
        assertThat(m.version()).isEqualTo("1.12.2");
        assertThat(m.displayName()).isEqualTo("voxelmap");
        assertThat(m.description()).isEqualTo("A minimap mod");
        assertThat(m.environment()).isEqualTo("client");
    }

    @Test
    void surfacesMcversionRevisionAuthorIntoCustom() throws Exception {
        Path jar = writeLiteMod("""
                {
                  "name": "voxelmap",
                  "version": "1.0",
                  "mcversion": "1.12.2",
                  "revision": 3,
                  "author": "MamiyaOtaru"
                }
                """);
        AprismManifest m = parser.tryParseLiteLoaderManifest(jar).orElseThrow();
        assertThat(m.custom()).containsEntry("mcversion", "1.12.2");
        assertThat(m.custom()).containsEntry("revision", "3");
        assertThat(m.custom()).containsEntry("author", "MamiyaOtaru");
    }

    @Test
    void absentOptionalFieldsDoNotLeakIntoCustom() throws Exception {
        Path jar = writeLiteMod("""
                { "name": "minmod", "version": "1.0" }
                """);
        AprismManifest m = parser.tryParseLiteLoaderManifest(jar).orElseThrow();
        assertThat(m.custom()).doesNotContainKeys("mcversion", "revision", "author");
        assertThat(m.description()).isEmpty();
    }

    @Test
    void absentLitemodJsonReturnsEmpty() throws Exception {
        Path jar = tempDir.resolve("plain.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("fabric.mod.json"));
            zos.write("{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertThat(parser.tryParseLiteLoaderManifest(jar)).isEmpty();
    }
}
