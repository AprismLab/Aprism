package com.aprism.loader.bedrock;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.aprism.manifest.ManifestParseException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Loads a {@link BedrockVersionDatabase} from a JSON signature database file.
 *
 * <p>This is the persistence half of the BE injection version database
 * (FACT.md 9.8 fail-closed, FACT.md 9.9 signature DB). Later milestones source
 * the database from the BedrockAnalyzer header_generator pipeline; this loader
 * is the stable on-disk format Aprism consumes.
 *
 * <p><b>On-disk schema (schema version 1):</b>
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "generated": "2026-08-09T00:00:00Z",   // optional
 *   "generator": "bedrock-analyzer",        // optional
 *   "versions": [
 *     { "beVersion": "26.2.0", "signatureCount": 42, "supported": true,  "notes": "" },
 *     { "beVersion": "26.1.0", "signatureCount": 40, "supported": false, "notes": "incomplete sigs" }
 *   ]
 * }
 * </pre>
 *
 * <p><b>Fail-closed semantics.</b> {@link #load} throws
 * {@link ManifestParseException} (so callers treat it exactly like a bad
 * manifest) for: missing file, unreadable file, malformed JSON, missing or
 * wrong-type {@code versions} array, a {@code schemaVersion} newer than this
 * loader understands, or any version entry missing a {@code beVersion},
 * {@code signatureCount}, or {@code supported} field. Unknown extra fields are
 * ignored for forward compatibility.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class BedrockSignatureDbLoader {

    /** The on-disk schema version this loader understands. */
    public static final int SCHEMA_VERSION = 1;

    /** The top-level JSON key holding the version entries. */
    private static final String KEY_VERSIONS = "versions";
    private static final String KEY_SCHEMA_VERSION = "schemaVersion";

    private final Gson gson = new Gson();

    /**
     * Loads a version database from a JSON file on disk.
     *
     * @param path the path to the signature database JSON file
     * @return the populated version database
     * @throws ManifestParseException if the file is missing, unreadable,
     *         malformed, has an unsupported schema version, or any version
     *         entry is missing required fields
     */
    public BedrockVersionDatabase load(Path path) throws ManifestParseException {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.exists(path)) {
            throw new ManifestParseException("Signature database not found: " + path);
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException e) {
            throw new ManifestParseException("Failed to read signature database: " + path, e);
        }
    }

    /**
     * Parses a version database from a JSON reader (used by {@link #load} and
     * by tests).
     *
     * @param reader the JSON source
     * @return the populated version database
     * @throws ManifestParseException on malformed JSON, missing/wrong-type
     *         fields, unsupported schema version, or incomplete entries
     */
    public BedrockVersionDatabase parse(Reader reader) throws ManifestParseException {
        Root root;
        try {
            root = gson.fromJson(reader, Root.class);
        } catch (JsonSyntaxException e) {
            throw new ManifestParseException("Malformed signature database JSON", e);
        }
        if (root == null) {
            throw new ManifestParseException("Signature database is empty");
        }
        if (root.schemaVersion != null && root.schemaVersion > SCHEMA_VERSION) {
            throw new ManifestParseException(
                    "Signature database schema version " + root.schemaVersion
                            + " is newer than supported (" + SCHEMA_VERSION + ")");
        }
        if (root.versions == null) {
            throw new ManifestParseException("Signature database is missing the 'versions' array");
        }

        BedrockVersionDatabase db = new BedrockVersionDatabase();
        List<VersionEntryDto> entries = new ArrayList<>();
        for (VersionEntryDto dto : root.versions) {
            validate(dto);
            entries.add(dto);
        }
        for (VersionEntryDto dto : entries) {
            db.register(new BedrockVersionDatabase.VersionEntry(
                    dto.beVersion, dto.signatureCount, dto.supported,
                    dto.notes == null ? "" : dto.notes));
        }
        return db;
    }

    private static void validate(VersionEntryDto dto) throws ManifestParseException {
        if (dto.beVersion == null || dto.beVersion.isBlank()) {
            throw new ManifestParseException("Signature database entry is missing 'beVersion'");
        }
        if (dto.signatureCount == null) {
            throw new ManifestParseException(
                    "Signature database entry is missing 'signatureCount' (beVersion=" + dto.beVersion + ")");
        }
        if (dto.supported == null) {
            throw new ManifestParseException(
                    "Signature database entry is missing 'supported' (beVersion=" + dto.beVersion + ")");
        }
    }

    /** Top-level JSON document. Unknown fields are ignored by Gson. */
    private static final class Root {
        Integer schemaVersion;
        List<VersionEntryDto> versions;
    }

    /** One version entry. Unknown fields are ignored by Gson. */
    private static final class VersionEntryDto {
        String beVersion;
        Integer signatureCount;
        Boolean supported;
        String notes;
    }
}
