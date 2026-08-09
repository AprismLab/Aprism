package com.aprism.loader.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.manifest.ManifestParseException;

/**
 * Tests for the fail-closed {@link BedrockSignatureDbLoader}.
 *
 * @author BlockConnect@StarsailsClover
 */
class BedrockSignatureDbLoaderTest {

    @TempDir
    Path tempDir;

    private BedrockSignatureDbLoader loader;

    @BeforeEach
    void setUp() {
        loader = new BedrockSignatureDbLoader();
    }

    private static final String VALID_DB = """
            {
              "schemaVersion": 1,
              "generated": "2026-08-09T00:00:00Z",
              "generator": "bedrock-analyzer",
              "versions": [
                { "beVersion": "26.2.0", "signatureCount": 42, "supported": true, "notes": "" },
                { "beVersion": "26.1.0", "signatureCount": 40, "supported": false, "notes": "incomplete sigs" }
              ]
            }
            """;

    @Test
    void parsesValidDbFromReader() throws Exception {
        BedrockVersionDatabase db = loader.parse(new StringReader(VALID_DB));
        assertThat(db.size()).isEqualTo(2);
        assertThat(db.isSupported("26.2.0")).isTrue();
        assertThat(db.isSupported("26.1.0")).isFalse();
        assertThat(db.lookup("26.2.0").get().signatureCount()).isEqualTo(42);
    }

    @Test
    void parsesValidDbFromDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("signatures.json");
        Files.writeString(file, VALID_DB, StandardCharsets.UTF_8);
        BedrockVersionDatabase db = loader.load(file);
        assertThat(db.size()).isEqualTo(2);
        assertThat(db.isSupported("26.2.0")).isTrue();
    }

    @Test
    void ignoresUnknownTopLevelAndEntryFields() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "someFutureField": { "x": 1 },
                  "versions": [
                    { "beVersion": "26.2.0", "signatureCount": 42, "supported": true, "newField": "ignored" }
                  ]
                }
                """;
        BedrockVersionDatabase db = loader.parse(new StringReader(json));
        assertThat(db.size()).isEqualTo(1);
        assertThat(db.isSupported("26.2.0")).isTrue();
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> loader.parse(new StringReader("{ not valid json")))
                .isInstanceOf(ManifestParseException.class)
                .hasMessageContaining("Malformed");
    }

    @Test
    void rejectsEmptyDocument() {
        assertThatThrownBy(() -> loader.parse(new StringReader("")))
                .isInstanceOf(ManifestParseException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsMissingVersionsArray() {
        assertThatThrownBy(() -> loader.parse(new StringReader("{ \"schemaVersion\": 1 }")))
                .isInstanceOf(ManifestParseException.class)
                .hasMessageContaining("versions");
    }

    @Test
    void rejectsNewerSchemaVersion() {
        String json = """
                { "schemaVersion": 99, "versions": [] }
                """;
        assertThatThrownBy(() -> loader.parse(new StringReader(json)))
                .isInstanceOf(ManifestParseException.class)
                .hasMessageContaining("newer");
    }

    @Test
    void rejectsEntryMissingBeVersion() {
        String json = """
                { "schemaVersion": 1, "versions": [ { "signatureCount": 1, "supported": true } ] }
                """;
        assertThatThrownBy(() -> loader.parse(new StringReader(json)))
                .isInstanceOf(ManifestParseException.class)
                .hasMessageContaining("beVersion");
    }

    @Test
    void rejectsEntryMissingSignatureCount() {
        String json = """
                { "schemaVersion": 1, "versions": [ { "beVersion": "26.2.0", "supported": true } ] }
                """;
        assertThatThrownBy(() -> loader.parse(new StringReader(json)))
                .isInstanceOf(ManifestParseException.class)
                .hasMessageContaining("signatureCount");
    }

    @Test
    void rejectsEntryMissingSupported() {
        String json = """
                { "schemaVersion": 1, "versions": [ { "beVersion": "26.2.0", "signatureCount": 42 } ] }
                """;
        assertThatThrownBy(() -> loader.parse(new StringReader(json)))
                .isInstanceOf(ManifestParseException.class)
                .hasMessageContaining("supported");
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> loader.load(tempDir.resolve("does-not-exist.json")))
                .isInstanceOf(ManifestParseException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void emptyVersionsArrayYieldsEmptyDb() throws Exception {
        BedrockVersionDatabase db = loader.parse(new StringReader("{ \"schemaVersion\": 1, \"versions\": [] }"));
        assertThat(db.size()).isZero();
    }
}
