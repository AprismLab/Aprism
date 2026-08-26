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
 * official Mojang class names to their runtime (obfuscated) names
 * (v26.8-Alpha.5, DEC-PRE261 Option A foundation).
 *
 * <p>On the REMAPPED profile the runtime classes carry obfuscated names.
 * Binding targets written against official names must therefore be
 * translated: {@code official --[client.txt reverse]--> obfuscated
 * (runtime)}. Member mappings are skipped; classes suffice for binder
 * targets.
 *
 * <p>File format (ProGuard): class lines are
 * {@code obf.qual.Name -> official.qual.Name:}; member lines are indented.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class OfficialMappings {
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG = Logger.getLogger("aprism.contentbind");

    private final Map<String, String> officialToRuntime;

    private OfficialMappings(Map<String, String> officialToRuntime) {
        this.officialToRuntime = officialToRuntime;
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
        Map<String, String> map = new HashMap<>(30_000);
        try (BufferedReader br = Files.newBufferedReader(clientTxt,
                StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith(" ") || line.startsWith("#")) {
                    continue; // members/comments skipped; classes only
                }
                int arrow = line.indexOf(" -> ");
                if (arrow < 0) {
                    continue;
                }
                String officialPart = line.substring(arrow + 4);
                if (officialPart.endsWith(":")) {
                    officialPart = officialPart.substring(0,
                            officialPart.length() - 1);
                }
                // Keep the LAST occurrence (inner-class lines may repeat).
                map.put(officialPart, line.substring(0, arrow));
            }
        }
        int size = map.size();
        LOG.info("OfficialMappings loaded: " + size + " class entries");
        return new OfficialMappings(map);
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
     * @return the number of mapped classes
     */
    public int size() {
        return officialToRuntime.size();
    }
}
