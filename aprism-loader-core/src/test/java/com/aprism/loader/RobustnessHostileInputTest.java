package com.aprism.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.api.registry.ResourceKey;
import com.aprism.manifest.ManifestParser;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * QA-R hostile-input suite (v26.8-Alpha.1): adversarial probes against the
 * loader's parsing, key validation, and archive handling. Each test documents
 * a finding; failures here become v26.8 hardening work items.
 */
class RobustnessHostileInputTest {

    @TempDir
    Path tempDir;

    // --- ResourceKey validation ---

    @Test
    void resourceKeyRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourceKey.parse("aprism:../../evil"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceKey.parse("../../evil:key"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceKey.parse("aprism:..\\evil"));
    }

    @Test
    void resourceKeyRejectsNullBytesAndUnicode() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourceKey.parse("aprism:evi\u0000l"));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceKey.parse("aprism:邪恶"));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceKey("EVIL", "x"));
    }

    @Test
    void resourceKeyRejectsMalformedCombinations() {
        assertThrows(IllegalArgumentException.class, () -> ResourceKey.parse(":name"));
        assertThrows(IllegalArgumentException.class, () -> ResourceKey.parse("ns:"));
        assertThrows(IllegalArgumentException.class, () -> ResourceKey.parse("nocolon"));
        assertThrows(NullPointerException.class, () -> ResourceKey.parse(null));
    }

    // --- Manifest parsing hostility ---

    @Test
    void manifestParserRejectsGarbageJson() {
        Path bad = tempDir.resolve("bad.json");
        assertDoesNotThrow(() -> Files.writeString(bad, "{not json at all;;;"));
        assertThrows(Exception.class, () -> new ManifestParser().parse(bad));
    }

    @Test
    void manifestParserRejectsMissingId() {
        Path bad = tempDir.resolve("noid.json");
        assertDoesNotThrow(() -> Files.writeString(bad,
                "{\"schemaVersion\":1,\"version\":\"1.0\"}"));
        assertThrows(Exception.class, () -> new ManifestParser().parse(bad));
    }

    @Test
    void manifestParserHandlesHugeIdField() throws Exception {
        Path big = tempDir.resolve("big.json");
        String huge = "x".repeat(2_000_000);
        Files.writeString(big, "{\"id\":\"" + huge + "\",\"version\":\"1.0\"}");
        // Must not hang or OOM; outcome (accept/reject) is a finding.
        assertDoesNotThrow(() -> {
            try {
                new ManifestParser().parse(big);
            } catch (Exception expected) {
                // rejection is acceptable
            }
        });
    }

    // --- Archive hostility ---

    @Test
    void nonZipAjeFileIsRejectedGracefully() {
        Path fake = tempDir.resolve("fake.aje");
        assertDoesNotThrow(() -> Files.writeString(fake, "this is not a zip"));
        assertThrows(Exception.class, () ->
                new ExtensionLoader("v26.8-Alpha.1", "JE", "26.2").listEmbeddedJarNames(fake));
    }

    @Test
    void zipSlipEntryCannotEscapeTempDir() throws Exception {
        // Craft an .aep whose entry name attempts directory traversal.
        Path hostile = tempDir.resolve("hostile.aep");
        Path escaped = tempDir.resolve("ESCAPED_MARKER.txt");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(hostile))) {
            zos.putNextEntry(new ZipEntry("../ESCAPED_MARKER.txt"));
            zos.write("pwn".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("legit.jar"));
            zos.write("jar".getBytes());
            zos.closeEntry();
        }
        ExtensionLoader loader = new ExtensionLoader("v26.8-Alpha.1", "JE", "26.2");
        List<String> names = loader.listEmbeddedJarNames(hostile);
        // The traversal entry must not have escaped during listing/extraction.
        loader.extractJar(hostile, "../ESCAPED_MARKER.txt",
                tempDir.resolve("out.jar"));
        assertFalse(Files.exists(tempDir.resolve("ESCAPED_MARKER.txt")),
                "zip-slip: traversal entry escaped the archive sandbox");
        assertFalse(Files.exists(escaped.getParent().resolve("..").resolve("ESCAPED_MARKER.txt")));
    }

    @Test
    void duplicateContentKeyRejectedByRegistry() {
        var reg = new com.aprism.loader.registry.TypedRegistryImpl<
                com.aprism.api.registry.ItemContent>();
        var k = ResourceKey.parse("aprism:dup");
        reg.register(k, new com.aprism.api.registry.ItemContent(k, 8));
        assertThrows(IllegalArgumentException.class,
                () -> reg.register(k, new com.aprism.api.registry.ItemContent(k, 16)));
    }
}
