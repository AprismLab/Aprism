package com.aprism.harness;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * {@code .aje} packaging-contract verification (v26.9-Alpha.8).
 *
 * <p>Rationale (Despotes Dev report): a mod compiled against Aprism v26.8
 * shipped {@code depends.aprism = ">=26.0-Alpha.1"}. The harness re-derives
 * the floor decision from first principles - independently of the packaging
 * plugin - so a mistake in one implementation cannot mask itself, and it also
 * verifies the archive structure the loader relies on.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AjeManifestContract {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** The verified manifest facts of one archive. */
    public record ManifestFacts(String modId, String version, String aprismFloor,
            String entrypoint, List<String> entries) {
    }

    private AjeManifestContract() {
    }

    /**
     * Reads the {@code aprism.manifest.json} out of an {@code .aje}.
     *
     * @param aje the archive
     * @return the manifest facts
     * @throws IOException when the archive is unreadable or the manifest is
     *         missing/malformed
     */
    public static ManifestFacts read(Path aje) throws IOException {
        try (JarFile archive = new JarFile(aje.toFile())) {
            ZipEntry manifestEntry = archive.getEntry("aprism.manifest.json");
            if (manifestEntry == null) {
                throw new IOException("archive has no aprism.manifest.json: " + aje);
            }
            String json;
            try (InputStream in = archive.getInputStream(manifestEntry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
            String modId = stringOrNull(manifest, "id");
            String version = stringOrNull(manifest, "version");
            String floor = null;
            JsonElement depends = manifest.get("depends");
            if (depends != null && depends.isJsonObject()) {
                JsonElement aprism = depends.getAsJsonObject().get("aprism");
                if (aprism != null && aprism.isJsonPrimitive()) {
                    floor = aprism.getAsString();
                }
            }
            String entrypoint = null;
            JsonElement entrypoints = manifest.get("entrypoints");
            if (entrypoints != null && entrypoints.isJsonObject()) {
                JsonElement main = entrypoints.getAsJsonObject().get("main");
                if (main != null && main.isJsonArray() && !main.getAsJsonArray().isEmpty()) {
                    entrypoint = main.getAsJsonArray().get(0).getAsString();
                }
            }
            List<String> entries = new ArrayList<>();
            archive.stream().forEach(e -> entries.add(e.getName()));
            if (modId == null || version == null) {
                throw new IOException("archive manifest lacks id/version: " + aje);
            }
            return new ManifestFacts(modId, version, floor, entrypoint,
                    List.copyOf(entries));
        }
    }

    /**
     * Verifies the packaging contract beyond the floor check: the archive must
     * embed the mod jar, must not embed the loader itself, and must declare an
     * entrypoint.
     *
     * @param aje the archive
     * @param facts the facts read from the archive
     * @return the problems found (empty when the contract holds)
     */
    public static List<String> verifyStructure(Path aje, ManifestFacts facts) {
        List<String> problems = new ArrayList<>();
        if (facts.entrypoint() == null) {
            problems.add("no entrypoints.main declared");
        }
        boolean hasModJar = facts.entries().contains(facts.modId() + ".jar");
        if (!hasModJar) {
            problems.add("archive does not embed " + facts.modId() + ".jar");
        }
        boolean embedsLoader = facts.entries().stream()
                .anyMatch(n -> n.contains("com/aprism/loader/AprismAgent.class"));
        if (embedsLoader) {
            problems.add("archive embeds the Aprism loader itself; an .aje must "
                    + "not ship the loader");
        }
        return List.copyOf(problems);
    }

    /**
     * Verifies the floor is not below the baseline the mod was compiled
     * against. This duplicates the packaging plugin's decision on purpose.
     *
     * @param facts the archive facts
     * @param baseline the compiled-against loader version
     * @return the problems found (empty when the floor is sound)
     */
    public static List<String> verifyFloor(ManifestFacts facts, String baseline) {
        List<String> problems = new ArrayList<>();
        if (facts.aprismFloor() == null || facts.aprismFloor().isBlank()) {
            problems.add("no depends.aprism floor declared; an old loader would "
                    + "attempt to load this artifact (baseline " + baseline + ")");
            return List.copyOf(problems);
        }
        Integer floor = parseComparable(facts.aprismFloor());
        Integer base = parseComparable(baseline);
        if (floor == null || base == null) {
            problems.add("cannot compare floor '" + facts.aprismFloor()
                    + "' with baseline '" + baseline + "'");
            return List.copyOf(problems);
        }
        if (floor < base) {
            problems.add("declared floor " + facts.aprismFloor()
                    + " is below the compiled baseline " + baseline
                    + " (the Despotes-reported defect class)");
        }
        return List.copyOf(problems);
    }

    /**
     * Extracts a comparable ordinal from an Aprism version expression. Only
     * the initial {@code MAJOR.MINOR} pair matters for the floor/baseline
     * comparison this harness performs; pre-release detail is ignored on
     * purpose so the check stays strictly conservative for the major line.
     *
     * @param expression a version or range expression
     * @return the ordinal, or null when unparseable
     */
    static Integer parseComparable(String expression) {
        if (expression == null) {
            return null;
        }
        String text = expression.trim().replace(">=", "").replace(">", "")
                .replace("[", "").trim();
        int comma = text.indexOf(',');
        if (comma > 0) {
            text = text.substring(0, comma);
        }
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        int dash = text.indexOf('-');
        if (dash > 0) {
            text = text.substring(0, dash);
        }
        String[] parts = text.split("\\.");
        if (parts.length == 0 || parts[0].isBlank()) {
            return null;
        }
        try {
            int major = Integer.parseInt(parts[0].trim());
            int minor = parts.length > 1 && !parts[1].isBlank()
                    ? Integer.parseInt(parts[1].trim()) : 0;
            return major * 100 + minor;
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private static String stringOrNull(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? null
                : element.getAsString();
    }
}
