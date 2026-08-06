package com.aprism.manifest.fallback;

import com.aprism.manifest.AprismManifest;
import com.aprism.manifest.ManifestException;
import com.aprism.manifest.ManifestParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads a Fabric {@code fabric.mod.json} and projects it into an
 * {@link AprismManifest}.
 *
 * <p>Maps: {@code id -> modId}, {@code version}, {@code name -> displayName},
 * {@code entrypoints}, {@code mixins}, {@code depends}, {@code accessWidener},
 * {@code environment}. The synthesized manifest is equivalent to an explicit
 * Aprism manifest for resolution and validation, per Document 2 section 5.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class FabricManifestReader {

    private FabricManifestReader() {
    }

    /**
     * Reads and converts a {@code fabric.mod.json} from disk.
     *
     * @param file path to {@code fabric.mod.json}
     * @return the synthesized Aprism manifest
     * @throws ManifestException.ManifestParseException if the file cannot be read or converted
     */
    public static AprismManifest parse(Path file) throws ManifestException.ManifestParseException {
        JsonObject json;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            json = JsonParser.parseString(text).getAsJsonObject();
        } catch (IOException e) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-001: cannot read fabric.mod.json " + file, e);
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-001: fabric.mod.json " + file + " is not a JSON object", e);
        }
        return parse(json);
    }

    /**
     * Converts an already-decoded {@code fabric.mod.json} object.
     *
     * @param json the fabric.mod.json root object
     * @return the synthesized Aprism manifest
     * @throws ManifestException.ManifestParseException if required fields are missing
     */
    public static AprismManifest parse(JsonObject json) throws ManifestException.ManifestParseException {
        if (json == null) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-001: fabric.mod.json root is null");
        }
        AprismManifest m = new AprismManifest();
        m.schemaVersion = 1;

        if (json.has("id")) {
            m.modId = json.get("id").getAsString();
        }
        if (json.has("version")) {
            m.version = json.get("version").getAsString();
        }
        if (json.has("name")) {
            m.displayName = json.get("name").getAsString();
        }
        if (json.has("description")) {
            m.description = json.get("description").getAsString();
        }
        if (json.has("authors")) {
            m.authors = ManifestParser.readObjectMap(json, "authors") == null
                    ? null : new ArrayList<>(ManifestParser.readObjectMap(json, "authors").values());
        }
        if (json.has("environment")) {
            m.environment = json.get("environment").getAsString();
        }
        if (json.has("entrypoints")) {
            m.entrypoints = readEntrypoints(json.getAsJsonObject("entrypoints"));
        }
        if (json.has("mixins")) {
            m.mixins = new ArrayList<>();
            json.getAsJsonArray("mixins").forEach(e -> m.mixins.add(e.isJsonObject() ? e.toString() : e.getAsString()));
        }
        m.depends = ManifestParser.readStringMap(json, "depends");
        m.recommends = ManifestParser.readStringMap(json, "recommends");
        m.suggests = ManifestParser.readStringMap(json, "suggests");
        m.breaks = ManifestParser.readStringMap(json, "breaks");
        m.conflicts = ManifestParser.readStringMap(json, "conflicts");
        if (json.has("accessWidener")) {
            m.accessWidener = json.get("accessWidener").getAsString();
        }
        m.provides = ManifestParser.readStringList(json, "provides");

        if (m.modId == null || m.modId.isBlank()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-005: fabric.mod.json missing 'id'");
        }
        if (m.version == null || m.version.isBlank()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-005: fabric.mod.json missing 'version'");
        }
        return m;
    }

    private static Map<String, List<String>> readEntrypoints(JsonObject entrypoints) {
        Map<String, List<String>> out = new java.util.HashMap<>();
        for (String phase : entrypoints.keySet()) {
            List<String> specs = ManifestParser.readStringList(entrypoints, phase);
            if (specs != null) {
                out.put(phase, specs);
            }
        }
        return out;
    }
}
