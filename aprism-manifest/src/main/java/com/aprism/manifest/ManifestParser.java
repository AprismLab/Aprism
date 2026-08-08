package com.aprism.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aprism.manifest.fallback.ForgeManifestReader;
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
    private static final String QUILT_MANIFEST = "quilt.mod.json";
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
     * Attempts to parse a Quilt manifest ({@code quilt.mod.json}) from a jar.
     *
     * <p>Quilt's manifest is a superset of Fabric's: identity fields live under
     * {@code quilt_loader} (id, version, entrypoints) and display metadata
     * under {@code quilt_loader.metadata} (name, description). Entrypoint keys
     * follow the Fabric convention ({@code init}, {@code client},
     * {@code server}), so the projected manifest reuses Fabric-style
     * entrypoint dispatch through the Quilt bridge.
     *
     * @param jarFile the jar path
     * @return the converted manifest, or empty if no Quilt manifest is present
     */
    public Optional<AprismManifest> tryParseQuiltManifest(Path jarFile) {
        return readJarEntry(jarFile, QUILT_MANIFEST).map(json -> {
            QuiltModJson qmj = GSON.fromJson(json, QuiltModJson.class);
            QuiltModJson.Loader loader = qmj.quilt_loader != null
                    ? qmj.quilt_loader : new QuiltModJson.Loader();
            QuiltModJson.Metadata meta = loader.metadata != null
                    ? loader.metadata : new QuiltModJson.Metadata();
            String id = nullTo(loader.id, "");
            Map<String, List<String>> entrypoints = normalizeQuiltEntrypoints(loader.entrypoints);
            return new AprismManifest(
                    1,
                    id,
                    nullTo(loader.version, ""),
                    nullTo(meta.name, id),
                    nullTo(meta.description, ""),
                    "*",
                    entrypoints,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    null,
                    List.of(),
                    Map.of());
        });
    }

    /**
     * Normalizes Quilt entrypoint declarations into the simple class-name list
     * form used by {@link AprismManifest}. Quilt allows each entrypoint key to
     * map to a bare class name, an object ({@code {"value": "...",
     * "adapter": "default"}}), or an array of either; all forms project to the
     * class name. The Quilt-native {@code init} key is projected to the
     * {@code main} key so the common lifecycle dispatch reaches it;
     * {@code client} and {@code server} pass through unchanged.
     *
     * @param entrypoints the raw entrypoint map (may be {@code null})
     * @return the normalized entrypoint map
     */
    private Map<String, List<String>> normalizeQuiltEntrypoints(
            Map<String, com.google.gson.JsonElement> entrypoints) {
        if (entrypoints == null) {
            return Map.of();
        }
        Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : entrypoints.entrySet()) {
            String key = "init".equals(e.getKey()) ? "main" : e.getKey();
            List<String> values = new java.util.ArrayList<>();
            collectQuiltEntrypointValues(e.getValue(), values);
            if (!values.isEmpty()) {
                out.put(key, values);
            }
        }
        return out;
    }

    /**
     * Recursively collects entrypoint class names from a Quilt entrypoint JSON
     * element, which may be a primitive string, an object with a {@code value}
     * member, or an array of either.
     *
     * @param element the entrypoint JSON element
     * @param out     the accumulator for class names
     */
    private void collectQuiltEntrypointValues(com.google.gson.JsonElement element,
            List<String> out) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (com.google.gson.JsonElement item : element.getAsJsonArray()) {
                collectQuiltEntrypointValues(item, out);
            }
        } else if (element.isJsonPrimitive()) {
            out.add(element.getAsString());
        } else if (element.isJsonObject()) {
            com.google.gson.JsonObject obj = element.getAsJsonObject();
            if (obj.has("value") && obj.get("value").isJsonPrimitive()) {
                out.add(obj.get("value").getAsString());
            }
        }
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
     * {@code mcmod.info}) from a jar. The {@code mods.toml} path delegates to
     * {@link ForgeManifestReader}, which correctly handles the
     * {@code [[mods]]}, {@code [[mixins]]}, and {@code [[dependencies.*]]}
     * table arrays (the simple key/value projection previously grabbed the
     * wrong {@code modId} on multi-entry files).
     *
     * @param jarFile the jar path
     * @return the converted manifest, or empty if no Forge manifest is present
     */
    public Optional<AprismManifest> tryParseLegacyForgeManifest(Path jarFile) {
        return readJarEntry(jarFile, FORGE_MANIFEST)
                .flatMap(toml -> {
                    try {
                        return Optional.of(ForgeManifestReader.parse(toml));
                    } catch (ManifestException.ManifestParseException e) {
                        return Optional.empty();
                    }
                })
                .or(() -> readJarEntry(jarFile, LEGACY_FORGE_MANIFEST).map(this::fromMcmodInfo));
    }

    /**
     * Attempts to parse a LiteLoader manifest ({@code litemod.json}) from a jar.
     *
     * @param jarFile the jar path
     * @return the converted manifest, or empty if no LiteLoader manifest is present
     */
    /**
     * Attempts to parse a LiteLoader manifest ({@code litemod.json}) from a jar.
     *
     * <p>LiteLoader manifests do NOT declare an entrypoint class; the mod's
     * main class is discovered by bytecode scanning for the {@code LiteMod}
     * interface (see {@code LiteLoaderEntrypointBridge}). The {@code mcversion}
     * field is the target Minecraft version and is projected into
     * {@code custom}; the {@code revision} field is surfaced in {@code custom}
     * as well.
     *
     * @param jarFile the jar path
     * @return the converted manifest, or empty if no LiteLoader manifest is present
     */
    public Optional<AprismManifest> tryParseLiteLoaderManifest(Path jarFile) {
        return readJarEntry(jarFile, LITELOADER_MANIFEST).map(json -> {
            LiteModJson lm = GSON.fromJson(json, LiteModJson.class);
            java.util.Map<String, Object> custom = new java.util.LinkedHashMap<>();
            if (lm.mcversion != null) {
                custom.put("mcversion", lm.mcversion);
            }
            if (lm.revision != null) {
                custom.put("revision", lm.revision);
            }
            if (lm.author != null) {
                custom.put("author", lm.author);
            }
            return new AprismManifest(
                    1,
                    nullTo(lm.name, ""),
                    nullTo(lm.version, ""),
                    nullTo(lm.displayName, lm.name),
                    nullTo(lm.description, ""),
                    "client",
                    Map.of(),
                    lm.mixinConfigs == null ? List.of() : lm.mixinConfigs,
                    Map.of(),
                    Map.of(),
                    null,
                    List.of(),
                    custom);
        });
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
        String description;
        String author;
        String revision;
        List<String> mixinConfigs;
    }

    /** Minimal Gson model of {@code quilt.mod.json}. */
    private static final class QuiltModJson {
        Loader quilt_loader;

        static final class Loader {
            String id;
            String version;
            Metadata metadata;
            Map<String, com.google.gson.JsonElement> entrypoints;
        }

        static final class Metadata {
            String name;
            String description;
        }
    }

    /** Minimal Gson model of a single {@code mcmod.info} entry. */
    private static final class McmodInfo {
        String modid;
        String name;
        String version;
        String description;
    }
}
