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
 * <p>Uses a small line-oriented TOML reader sufficient for the shapes NeoForge
 * uses: the {@code [[mods]]} table array (primary descriptor), the
 * {@code [[mixins]]} table array (mixin config references), and the
 * {@code [[dependencies.<modid>]]} table arrays (dependency declarations with
 * {@code modId} + {@code versionRange}). Top-level scalar keys (such as
 * {@code license}) are captured for projection into {@code custom}. Nested
 * tables beyond these are ignored. Multi-line triple-quoted strings
 * ({@code '''}) are tolerated: their continuation lines are skipped until the
 * closing quote.
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
     * Parses TOML text and converts the first {@code [[mods]]} entry,
     * projecting {@code [[mixins]]} configs and {@code [[dependencies.*]]}
     * entries alongside it.
     *
     * @param toml the TOML text
     * @return the synthesized Aprism manifest
     * @throws ManifestException.ManifestParseException if no mods entry or required fields are missing
     */
    public static AprismManifest parse(String toml) throws ManifestException.ManifestParseException {
        List<Map<String, String>> modTables = new ArrayList<>();
        List<String> mixinConfigs = new ArrayList<>();
        Map<String, String> depends = new LinkedHashMap<>();
        Map<String, String> topLevel = new LinkedHashMap<>();

        Map<String, String> currentMod = null;
        Map<String, String> currentDep = null;
        Section section = Section.NONE;
        boolean inMultilineString = false;

        for (String raw : toml.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Multi-line triple-quoted string handling. While inside one,
            // consume lines until a line containing the closing ''' appears.
            if (inMultilineString) {
                if (line.contains("'''")) {
                    inMultilineString = false;
                }
                continue;
            }

            // A standalone ''' line outside a multi-line string is the
            // CLOSING quote of an already-consumed value (e.g. description).
            // It must not be treated as an opening quote.
            if (line.equals("'''") || line.equals("'''")) {
                continue;
            }

            String stripped = stripComment(line).trim();
            if (stripped.isEmpty()) {
                continue;
            }

            if (stripped.equals("[[mods]]")) {
                flushDependency(depends, currentDep);
                currentDep = null;
                currentMod = new LinkedHashMap<>();
                modTables.add(currentMod);
                section = Section.MODS;
                continue;
            }
            if (stripped.startsWith("[[dependencies")) {
                flushDependency(depends, currentDep);
                currentDep = new LinkedHashMap<>();
                section = Section.DEPENDENCIES;
                continue;
            }
            if (stripped.equals("[[mixins]]")) {
                flushDependency(depends, currentDep);
                currentDep = null;
                section = Section.MIXINS;
                continue;
            }
            if (stripped.startsWith("[[")) {
                flushDependency(depends, currentDep);
                currentDep = null;
                section = Section.NONE;
                continue;
            }
            if (stripped.startsWith("[") && stripped.endsWith("]")) {
                flushDependency(depends, currentDep);
                currentDep = null;
                section = Section.NONE;
                continue;
            }

            Matcher kv = KV.matcher(stripped);
            if (!kv.matches()) {
                continue;
            }
            String key = kv.group(1);
            String rawValue = kv.group(2);

            // Detect a triple-quoted string VALUE that opens here. If it does
            // not close on the same line, enter multi-line consumption.
            if (rawValue.trim().startsWith("'''")) {
                String body = rawValue.trim().substring(3);
                if (!body.contains("'''")) {
                    inMultilineString = true;
                }
                continue;
            }

            String value = unquote(rawValue);
            switch (section) {
                case MODS -> {
                    if (currentMod != null) {
                        currentMod.put(key, value);
                    }
                }
                case MIXINS -> {
                    if ("config".equals(key)) {
                        mixinConfigs.add(value);
                    }
                }
                case DEPENDENCIES -> {
                    if (currentDep != null) {
                        currentDep.put(key, value);
                    }
                }
                default -> topLevel.put(key, value);
            }
        }
        // Flush a trailing dependency entry (file may end inside the table)
        flushDependency(depends, currentDep);

        if (modTables.isEmpty()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-001: neoforge.mods.toml has no [[mods]] entry");
        }
        return convert(modTables.get(0), mixinConfigs, depends, topLevel);
    }

    private enum Section { NONE, MODS, MIXINS, DEPENDENCIES }

    private static void flushDependency(Map<String, String> depends, Map<String, String> dep) {
        if (dep == null) {
            return;
        }
        String depId = dep.get("modId");
        String range = dep.getOrDefault("versionRange", "*");
        if (depId != null && !depId.isBlank()) {
            depends.putIfAbsent(depId, range);
        }
    }

    private static AprismManifest convert(Map<String, String> mods,
            List<String> mixinConfigs, Map<String, String> depends,
            Map<String, String> topLevel)
            throws ManifestException.ManifestParseException {
        String modId = mods.get("modId");
        String version = mods.getOrDefault("version", "");
        String displayName = mods.get("displayName");
        String description = mods.get("description");
        String environment = "*";
        // license is a top-level key in neoforge.mods.toml
        String license = topLevel.get("license");
        Map<String, Object> custom = license != null
                ? Map.of("license", license)
                : Map.of();

        if (modId == null || modId.isBlank()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-005: neoforge.mods.toml [[mods]] missing 'modId'");
        }
        if (version == null || version.isBlank()) {
            throw new ManifestException.ManifestParseException(
                    "CHKAPRISM-MANIFEST-005: neoforge.mods.toml [[mods]] missing 'version'");
        }

        return new AprismManifest(
                1, modId, version,
                displayName != null ? displayName : modId,
                description != null ? description : "",
                environment,
                Map.of(), List.copyOf(mixinConfigs), Map.copyOf(depends), Map.of(),
                null, List.of(), custom);
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
