package com.aprism.manifest.fallback;

import com.aprism.manifest.AprismManifest;
import com.aprism.manifest.ManifestException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a NeoForge {@code neoforge.mods.toml} (or legacy {@code mods.toml})
 * and projects it into an {@link AprismManifest}.
 *
 * <p>The skeleton uses a small line-oriented TOML reader sufficient for the
 * flat key/value and {@code [[mods]]} table-array shape that NeoForge uses for
 * its primary mod descriptor. Nested tables beyond the mods array are ignored.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class NeoForgeManifestReader {

    private static final Pattern KV =
            Pattern.compile("^([A-Za-z0-9_.]+)\\s*=\\s*(.+?)\\s*(?:#.*)?$");
    private static final Pattern STRING_VALUE =
            Pattern.compile("^\"((?:[^\"\\\\]|\\\\.)*)\"$");

    private NeoForgeManifestReader() {
    }

    /**
     * Reads and converts a {@code neoforge.mods.toml} from disk.
     *
     * @param file path to the TOML file
     * @return the synthesized Aprism manifest (primary [[mods]] entry)
     * @throws ManifestException.ManifestParseException if the file cannot be read or converted
     */
    public static AprismManifest parse(Path file) throws ManifestException.ManifestParseException {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-001: cannot read neoforge.mods.toml " + file, e);
        }
        return parse(text);
    }

    /**
     * Parses TOML text and converts the first {@code [[mods]]} entry.
     *
     * @param toml the TOML text
     * @return the synthesized Aprism manifest
     * @throws ManifestException.ManifestParseException if no mods entry or required fields are missing
     */
    public static AprismManifest parse(String toml) throws ManifestException.ManifestParseException {
        List<Map<String, String>> modTables = new ArrayList<>();
        Map<String, String> current = null;
        boolean inModsArray = false;

        for (String raw : toml.split("\\R")) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals("[[mods]]")) {
                current = new LinkedHashMap<>();
                modTables.add(current);
                inModsArray = true;
                continue;
            }
            if (line.startsWith("[[")) {
                inModsArray = false;
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                inModsArray = false;
                continue;
            }
            if (!inModsArray || current == null) {
                continue;
            }
            Matcher kv = KV.matcher(line);
            if (kv.matches()) {
                current.put(kv.group(1), unquote(kv.group(2)));
            }
        }

        if (modTables.isEmpty()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-001: neoforge.mods.toml has no [[mods]] entry");
        }
        return convert(modTables.get(0));
    }

    private static AprismManifest convert(Map<String, String> mods)
            throws ManifestException.ManifestParseException {
        AprismManifest m = new AprismManifest();
        m.schemaVersion = 1;
        m.modId = mods.get("modId");
        m.version = mods.getOrDefault("version", "");
        m.displayName = mods.get("displayName");
        m.description = mods.get("description");
        // NeoForge is always dedicated-server-capable on the JE side.
        m.environment = "*";
        if (mods.containsKey("license")) {
            m.custom = Map.of("license", mods.get("license"));
        }
        if (m.modId == null || m.modId.isBlank()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-005: neoforge.mods.toml [[mods]] missing 'modId'");
        }
        if (m.version == null || m.version.isBlank()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-005: neoforge.mods.toml [[mods]] missing 'version'");
        }
        return m;
    }

    private static String stripComment(String line) {
        boolean inString = false;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inString = !inString;
            }
            if (c == '#' && !inString) {
                break;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String unquote(String value) {
        Matcher m = STRING_VALUE.matcher(value.trim());
        if (m.matches()) {
            return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        // Bare value (number, boolean) returned as-is.
        return value.trim();
    }
}
