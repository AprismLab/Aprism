package com.aprism.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aprism.manifest.fallback.NeoForgeManifestReader;
import com.google.gson.Gson;

/**
 * Parses Aprism manifests from a variety of sources, including native
 * {@code aprism.manifest.json} files and the legacy manifest formats used by
 * Fabric, NeoForge, Forge, and LiteLoader. The legacy-format methods convert
 * their respective descriptors into an {@link AprismManifest}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ManifestParser {

    private static final Gson GSON = new Gson();

    private static final String FABRIC_MANIFEST = "fabric.mod.json";
    private static final String NEOFORGE_MANIFEST = "META-INF/neoforge.mods.toml";
    private static final String FORGE_MANIFEST = "META-INF/mods.toml";
    private static final String LEGACY_FORGE_MANIFEST = "mcmod.info";
    private static final String LITELOADER_MANIFEST = "litemod.json";

    /**
     * Parses an {@code aprism.manifest.json} file from disk.
     *
     * @param file the manifest file
     * @return the parsed manifest
     * @throws ManifestParseException if the file cannot be read or parsed
     */
    public AprismManifest parse(Path file) throws ManifestParseException {
        try (InputStream stream = Files.newInputStream(file, StandardOpenOption.READ)) {
            return parse(stream);
        } catch (IOException e) {
            throw new ManifestParseException("Failed to read manifest file: " + file, e);
        }
    }

    /**
     * Parses an {@code aprism.manifest.json} from an input stream.
     *
     * @param stream the input stream
     * @return the parsed manifest
     * @throws ManifestParseException if the stream cannot be parsed
     */
    public AprismManifest parse(InputStream stream) throws ManifestParseException {
        try {
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return AprismManifest.fromJson(json);
        } catch (IOException e) {
            throw new ManifestParseException("Failed to read manifest stream", e);
        }
    }

    /**
     * Attempts to parse a Fabric manifest ({@code fabric.mod.json}) from a jar.
     *
     * @param jarFile the jar path
     * @return the converted manifest, or empty if no Fabric manifest is present
     */
    public Optional<AprismManifest> tryParseFabricManifest(Path jarFile) {
        return readJarEntry(jarFile, FABRIC_MANIFEST).map(json -> {
            FabricModJson fmj = GSON.fromJson(json, FabricModJson.class);
            return new AprismManifest(
                    1,
                    nullTo(fmj.id, ""),
                    nullTo(fmj.version, ""),
                    nullTo(fmj.name, fmj.id),
                    nullTo(fmj.description, ""),
                    "*",
                    fmj.entrypoints == null ? Map.of() : fmj.entrypoints,
                    fmj.mixins == null ? List.of() : fmj.mixins,
                    fmj.depends == null ? Map.of() : fmj.depends,
                    Map.of(),
                    fmj.accessWidener,
                    fmj.provides == null ? List.of() : fmj.provides,
                    Map.of());
        });
    }

    /**
     * Attempts to parse a NeoForge manifest ({@code META-INF/neoforge.mods.toml})
     * from a jar. Delegates to {@link NeoForgeManifestReader}, which correctly
     * handles the {@code [[mods]]}, {@code [[mixins]]}, and
     * {@code [[dependencies.*]]} table arrays.
     *
     * @param jarFile the jar path
     * @return the converted manifest, or empty if no NeoForge manifest is present
     */
    public Optional<AprismManifest> tryParseNeoForgeManifest(Path jarFile) {
        return readJarEntry(jarFile, NEOFORGE_MANIFEST).flatMap(toml -> {
            try {
                return Optional.of(NeoForgeManifestReader.parse(toml));
            } catch (ManifestException.ManifestParseException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Attempts to parse a legacy Forge manifest ({@code META-INF/mods.toml} or
     * {@code mcmod.info}) from a jar.
     *
     * @param jarFile the jar path
     * @return the converted manifest, or empty if no Forge manifest is present
     */
    public Optional<AprismManifest> tryParseLegacyForgeManifest(Path jarFile) {
        return readJarEntry(jarFile, FORGE_MANIFEST)
                .map(this::fromModsToml)
                .or(() -> readJarEntry(jarFile, LEGACY_FORGE_MANIFEST).map(this::fromMcmodInfo));
    }

    /**
     * Attempts to parse a LiteLoader manifest ({@code litemod.json}) from a jar.
     *
     * @param jarFile the jar path
     * @return the converted manifest, or empty if no LiteLoader manifest is present
     */
    public Optional<AprismManifest> tryParseLiteLoaderManifest(Path jarFile) {
        return readJarEntry(jarFile, LITELOADER_MANIFEST).map(json -> {
            LiteModJson lm = GSON.fromJson(json, LiteModJson.class);
            return new AprismManifest(
                    1,
                    nullTo(lm.name, ""),
                    nullTo(lm.version, ""),
                    nullTo(lm.displayName, lm.name),
                    nullTo(lm.mcversion, ""),
                    "client",
                    Map.of(),
                    lm.mixinConfigs == null ? List.of() : lm.mixinConfigs,
                    Map.of(),
                    Map.of(),
                    null,
                    List.of(),
                    Map.of());
        });
    }

    /**
     * Converts a simplified {@code mods.toml} document into an
     * {@link AprismManifest}. Only flat key/value pairs are considered.
     *
     * @param toml the mods.toml contents
     * @return the converted manifest
     */
    private AprismManifest fromModsToml(String toml) {
        // TODO: replace with a full TOML parser for nested tables and arrays.
        Map<String, String> kv = parseSimpleToml(toml);
        String modId = nullTo(kv.get("modId"), "");
        return new AprismManifest(
                1,
                modId,
                nullTo(kv.get("version"), ""),
                nullTo(kv.get("displayName"), modId),
                nullTo(kv.get("description"), ""),
                "*",
                Map.of("main", List.of(nullTo(kv.get("entrypoint"), modId))),
                List.of(),
                Map.of(),
                Map.of(),
                null,
                List.of(),
                Map.of());
    }

    /**
     * Converts the first entry of a {@code mcmod.info} JSON array into an
     * {@link AprismManifest}.
     *
     * @param json the mcmod.info contents
     * @return the converted manifest
     */
    private AprismManifest fromMcmodInfo(String json) {
        McmodInfo[] infos = GSON.fromJson(json, McmodInfo[].class);
        if (infos == null || infos.length == 0) {
            return new AprismManifest(1, "", "", "", "", "*", Map.of(), List.of(), Map.of(),
                    Map.of(), null, List.of(), Map.of());
        }
        McmodInfo info = infos[0];
        return new AprismManifest(
                1,
                nullTo(info.modid, ""),
                nullTo(info.version, ""),
                nullTo(info.name, info.modid),
                nullTo(info.description, ""),
                "*",
                Map.of(),
                List.of(),
                Map.of(),
                Map.of(),
                null,
                List.of(),
                Map.of());
    }

    /**
     * Extracts flat {@code key="value"} pairs from a TOML document, skipping
     * comments and table headers.
     *
     * @param toml the TOML contents
     * @return a map of keys to string values
     */
    private Map<String, String> parseSimpleToml(String toml) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String raw : toml.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
        return map;
    }

    /**
     * Reads a text entry from a jar (zip) file.
     *
     * @param jarFile the jar path
     * @param entry   the entry path
     * @return the entry contents, or empty if absent or unreadable
     */
    private Optional<String> readJarEntry(Path jarFile, String entry) {
        try (FileSystem fs = FileSystems.newFileSystem(jarFile, (ClassLoader) null)) {
            Path entryPath = fs.getPath(entry);
            if (Files.exists(entryPath)) {
                return Optional.of(Files.readString(entryPath));
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String nullTo(String value, String fallback) {
        return value == null ? fallback : value;
    }

    /** Minimal Gson model of {@code fabric.mod.json}. */
    private static final class FabricModJson {
        String id;
        String version;
        String name;
        String description;
        String accessWidener;
        Map<String, List<String>> entrypoints;
        List<String> mixins;
        Map<String, String> depends;
        List<String> provides;
    }

    /** Minimal Gson model of {@code litemod.json}. */
    private static final class LiteModJson {
        String name;
        String version;
        String displayName;
        String mcversion;
        List<String> mixinConfigs;
    }

    /** Minimal Gson model of a single {@code mcmod.info} entry. */
    private static final class McmodInfo {
        String modid;
        String name;
        String version;
        String description;
    }
}
