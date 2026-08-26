package com.aprism.loader.contentbind;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Loads Mojang's official {@code client.txt} ProGuard mapping and resolves
 * official Mojang class and static-field names to their runtime (obfuscated)
 * names (v26.8-Alpha.5/6, DEC-PRE261 Option A).
 *
 * <p>On the REMAPPED profile the runtime classes carry obfuscated names.
 * Binding targets written against official names must therefore be
 * translated: {@code official --[client.txt reverse]--> obfuscated
 * (runtime)}. Class lines are {@code obf.qual.Name -> official.qual.Name:};
 * indented member lines are {@code type name -> obf} (fields) or
 * {@code sig -> obf} (methods, skipped here).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class OfficialMappings {
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG = Logger.getLogger("aprism.contentbind");

    private final Map<String, String> officialToRuntime;
    private final Map<String, Map<String, String>> fieldsByOfficialClass;
    private final Map<String, Map<String, String>> methodsByOfficialClass;

    private OfficialMappings(Map<String, String> officialToRuntime,
            Map<String, Map<String, String>> fieldsByOfficialClass,
            Map<String, Map<String, String>> methodsByOfficialClass) {
        this.officialToRuntime = officialToRuntime;
        this.fieldsByOfficialClass = fieldsByOfficialClass;
        this.methodsByOfficialClass = methodsByOfficialClass;
    }

    /**
     * Parses a Mojang {@code client.txt} mapping file.
     *
     * @param clientTxt path to the mapping file
     * @return the loaded mapping, or null when the file is absent
     * @throws IOException when the file exists but cannot be read
     */
    public static OfficialMappings load(Path clientTxt) throws IOException {
        if (clientTxt == null || !Files.isRegularFile(clientTxt)) {
            return null;
        }
        Map<String, String> classes = new HashMap<>(30_000);
        Map<String, Map<String, String>> fields = new HashMap<>();
        Map<String, Map<String, String>> methods = new HashMap<>();
        String lastOfficialClass = null;
        try (BufferedReader br = Files.newBufferedReader(clientTxt,
                StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith(" ")) {
                    // Member line under the last class line.
                    if (lastOfficialClass != null) {
                        if (line.contains("(")) {
                            parseMethodLine(line, lastOfficialClass, methods);
                        } else {
                            parseFieldLine(line, lastOfficialClass, fields);
                        }
                    }
                    continue;
                }
                int arrow = line.indexOf(" -> ");
                if (arrow < 0) {
                    continue;
                }
                // v26.8-Alpha.7 fix: Mojang client.txt class lines are
                // REVERSED vs standard ProGuard: "official -> obfuscated:".
                // Key = LEFT (official), value = RIGHT (runtime obfuscated).
                String officialPart = line.substring(0, arrow);
                String obfPart = line.substring(arrow + 4);
                if (obfPart.endsWith(":")) {
                    obfPart = obfPart.substring(0, obfPart.length() - 1);
                }
                // Keep the LAST occurrence (inner-class lines may repeat).
                classes.put(officialPart, obfPart);
                lastOfficialClass = officialPart;
            }
        }
        int size = classes.size();
        LOG.info("OfficialMappings loaded: " + size + " classes, "
                + fields.values().stream().mapToInt(Map::size).sum()
                + " fields");
        return new OfficialMappings(classes, fields, methods);
    }

    /**
     * Parses an indented method line: {@code sig -> obf} where sig is
     * {@code returnType officialName(args)}. Keyed by official name only -
     * overloads collapse to the last entry (binder targets have unique names).
     */
    private static void parseMethodLine(String line, String officialClass,
            Map<String, Map<String, String>> methods) {
        int arrow = line.indexOf(" -> ");
        if (arrow < 0) {
            return;
        }
        String officialSig = line.substring(0, arrow).trim();
        int sp = officialSig.lastIndexOf(' ');
        if (sp > 0) {
            officialSig = officialSig.substring(sp + 1);
        }
        int paren = officialSig.indexOf('(');
        if (paren <= 0) {
            return;
        }
        String officialName = officialSig.substring(0, paren);
        String obfName = line.substring(arrow + 4).trim();
        int obfParen = obfName.indexOf('(');
        if (obfParen > 0) {
            obfName = obfName.substring(0, obfParen);
        }
        methods.computeIfAbsent(officialClass, k -> new HashMap<>())
                .put(officialName, obfName);
    }

    /**
     * Resolves an official method name within an official class to its
     * runtime name. Returns the input unchanged when unmapped.
     */
    public String runtimeMethodName(String officialClassName,
            String officialMethodName) {
        Map<String, String> methods =
                methodsByOfficialClass.get(officialClassName);
        return methods == null ? officialMethodName
                : methods.getOrDefault(officialMethodName, officialMethodName);
    }

    private static void parseFieldLine(String line, String officialClass,
            Map<String, Map<String, String>> fields) {
        int arrow = line.indexOf(" -> ");
        if (arrow < 0) {
            return;
        }
        String officialField = line.substring(0, arrow).trim();
        int sp = officialField.lastIndexOf(' ');
        if (sp > 0) {
            officialField = officialField.substring(sp + 1);
        }
        String obfField = line.substring(arrow + 4).trim();
        fields.computeIfAbsent(officialClass, k -> new HashMap<>())
                .put(officialField, obfField);
    }

    /**
     * Resolves an official Mojang class name to its runtime name.
     *
     * @param officialName e.g. {@code net.minecraft.core.registries.BuiltInRegistries}
     * @return the runtime (obfuscated) name, or the input unchanged when the
     *         name is not in the mapping (e.g. already-runtime or library)
     */
    public String runtimeName(String officialName) {
        return officialToRuntime.getOrDefault(officialName, officialName);
    }

    /**
     * Resolves an official static field name within an official class to its
     * runtime name. Returns the input unchanged when unmapped.
     *
     * @param officialClassName the official class name
     * @param officialFieldName e.g. {@code ITEM}
     * @return the runtime field name
     */
    public String runtimeFieldName(String officialClassName,
            String officialFieldName) {
        Map<String, String> fields =
                fieldsByOfficialClass.get(officialClassName);
        return fields == null ? officialFieldName
                : fields.getOrDefault(officialFieldName, officialFieldName);
    }

    /**
     * @return the number of mapped classes
     */
    public int size() {
        return officialToRuntime.size();
    }
}
