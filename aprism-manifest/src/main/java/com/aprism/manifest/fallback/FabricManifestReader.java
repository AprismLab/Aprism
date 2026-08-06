package com.aprism.manifest.fallback;

import com.aprism.manifest.AprismManifest;
import com.aprism.manifest.ManifestException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a Fabric {@code fabric.mod.json} and projects it into an
 * {@link AprismManifest}.
 *
 * <p>Maps: {@code id -> id}, {@code version}, {@code name -> displayName},
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

        String id = json.has("id") ? json.get("id").getAsString() : null;
        String version = json.has("version") ? json.get("version").getAsString() : null;
        String displayName = json.has("name") ? json.get("name").getAsString() : id;
        String description = json.has("description") ? json.get("description").getAsString() : "";
        String environment = json.has("environment") ? json.get("environment").getAsString() : "*";
        String accessWidener = json.has("accessWidener") ? json.get("accessWidener").getAsString() : null;

        Map<String, List<String>> entrypoints = json.has("entrypoints")
                ? readEntrypoints(json.getAsJsonObject("entrypoints"))
                : Map.of();

        List<String> mixins = json.has("mixins") ? readStringList(json.getAsJsonArray("mixins")) : List.of();
        Map<String, String> depends = json.has("depends") ? readStringMap(json.getAsJsonObject("depends")) : Map.of();
        List<String> provides = json.has("provides") ? readStringList(json.getAsJsonArray("provides")) : List.of();

        if (id == null || id.isBlank()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-005: fabric.mod.json missing 'id'");
        }
        if (version == null || version.isBlank()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-005: fabric.mod.json missing 'version'");
        }

        return new AprismManifest(
                1, id, version, displayName, description, environment,
                entrypoints, mixins, depends, Map.of(),
                accessWidener, provides, Map.of());
    }

    private static Map<String, List<String>> readEntrypoints(JsonObject entrypoints) {
        Map<String, List<String>> out = new HashMap<>();
        for (String phase : entrypoints.keySet()) {
            JsonElement element = entrypoints.get(phase);
            if (element.isJsonArray()) {
                out.put(phase, readStringList(element.getAsJsonArray()));
            } else if (element.isJsonPrimitive()) {
                out.put(phase, List.of(element.getAsString()));
            }
        }
        return out;
    }

    private static List<String> readStringList(JsonArray array) {
        List<String> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                out.add(element.toString());
            } else {
                out.add(element.getAsString());
            }
        }
        return out;
    }

    private static Map<String, String> readStringMap(JsonObject object) {
        Map<String, String> out = new HashMap<>();
        for (String key : object.keySet()) {
            JsonElement element = object.get(key);
            out.put(key, element.isJsonPrimitive() ? element.getAsString() : element.toString());
        }
        return out;
    }
}
