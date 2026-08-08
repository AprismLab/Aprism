package com.aprism.loader;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.aprism.manifest.AprismManifest;
import com.aprism.manifest.ManifestParseException;
import com.aprism.manifest.ManifestParser;

/**
 * Scans mod directories for Aprism mod archives and parses their manifests.
 *
 * <p>Per-loader folder separation (see FACT.md 9.15): each mod loader has its
 * own directory under the game instance root. The Aprism native folder is
 * always scanned; loader-specific folders ({@code fabric-mods/},
 * {@code neoforge-mods/}, ...) are scanned only when the corresponding
 * loader-support extension is registered.
 *
 * <p>The {@link #discoverAll(Path, Map)} entry point performs the full
 * per-loader scan. The legacy {@link #discover(Path)} entry point is retained
 * for single-folder scans (Aprism native only).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ModDiscoverer {

    /** Aprism native loader key (no extension needed). */
    public static final String APRISM_NATIVE = "aprism";

    /** Fabric loader key (registered by Fabric-Support.aep). */
    public static final String FABRIC_KEY = "Fa";

    /**
     * The Fabric Loader version that Aprism's Fabric-Support emulates. Fabric
     * mods declare {@code depends.fabricloader} ranges against this value; it
     * mirrors the loader version in {@code gradle/libs.versions.toml}.
     */
    public static final String FABRIC_LOADER_VERSION = "0.16.14";

    /** NeoForge loader key (registered by NeoForge-Support.aep). */
    public static final String NEOFORGE_KEY = "N";

    /**
     * The NeoForge version that Aprism NeoForge-Support emulates. NeoForge
     * mods may declare {@code depends.neoforge} ranges against this value;
     * it mirrors the {@code neoforge} entry in gradle/libs.versions.toml.
     */
    public static final String NEOFORGE_LOADER_VERSION = "21.4.0-beta";

    /** The archive format of a discovered mod. */
    public enum ModFormat { AJE, JAR, LITEMOD }

    /** A mod discovered on disk. */
    public record DiscoveredMod(
            Path path,
            AprismManifest manifest,
            ModFormat format,
            String loaderKey,
            String loaderFolder) {
    }

    private final ManifestParser parser = new ManifestParser();

    /**
     * Discovers Aprism native mods in a single directory. Equivalent to
     * {@code discoverAll(root, Map.of())} but returns only the mods from the
     * given directory tagged as Aprism native.
     *
     * @param modsDir the mods directory
     * @return the list of discovered mods
     */
    public List<DiscoveredMod> discover(Path modsDir) {
        List<DiscoveredMod> out = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            return out;
        }
        scanFolder(modsDir, APRISM_NATIVE, out);
        return out;
    }

    /**
     * Scans the Aprism native {@code mods/} folder plus every loader-specific
     * folder declared by registered loader-support extensions.
     *
     * <p>The {@code loaderFolders} map comes from {@link ExtensionLoader} and
     * maps loader key (e.g. {@code "Fa"}) to the folder name relative to the
     * game root (e.g. {@code "fabric-mods"}).
     *
     * @param gameRoot       the game instance root (contains {@code mods/},
     *                       {@code fabric-mods/}, etc.)
     * @param loaderFolders  the registered loader-support folders (may be empty)
     * @return the discovered mods, sorted by loader key then path
     */
    public List<DiscoveredMod> discoverAll(Path gameRoot, Map<String, String> loaderFolders) {
        List<DiscoveredMod> out = new ArrayList<>();

        // Phase 1: always scan Aprism native mods/ folder
        Path nativeDir = gameRoot.resolve("mods");
        scanFolder(nativeDir, APRISM_NATIVE, out);

        // Phase 2: scan each registered loader folder
        if (loaderFolders != null) {
            for (Map.Entry<String, String> entry : loaderFolders.entrySet()) {
                Path dir = gameRoot.resolve(entry.getValue());
                scanFolder(dir, entry.getKey(), out);
            }
        }

        out.sort((a, b) -> {
            int c = a.loaderKey().compareTo(b.loaderKey());
            if (c != 0) {
                return c;
            }
            return a.path().compareTo(b.path());
        });
        return out;
    }

    private void scanFolder(Path dir, String loaderKey, List<DiscoveredMod> out) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.sorted().forEach(file -> parseFile(file, loaderKey, dir, out));
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan mods directory: " + dir, e);
        }
    }

    /**
     * Parses a single candidate file and appends it to the results if it is a
     * recognized mod archive.
     *
     * @param file       the candidate file
     * @param loaderKey  the loader key for this folder
     * @param folder     the folder being scanned
     * @param out        the accumulator for discovered mods
     */
    private void parseFile(Path file, String loaderKey, Path folder, List<DiscoveredMod> out) {
        String name = file.getFileName().toString().toLowerCase();
        try {
            if (name.endsWith(".aje")) {
                AprismManifest m = readAjeManifest(file);
                if (m != null) {
                    out.add(new DiscoveredMod(file, m, ModFormat.AJE, loaderKey,
                            folder.getFileName().toString()));
                }
            } else if (name.endsWith(".litemod")) {
                AprismManifest m = parser.tryParseLiteLoaderManifest(file).orElse(null);
                if (m != null) {
                    out.add(new DiscoveredMod(file, m, ModFormat.LITEMOD, loaderKey,
                            folder.getFileName().toString()));
                }
            } else if (name.endsWith(".jar")) {
                AprismManifest m = parser.tryParseFabricManifest(file)
                        .or(() -> parser.tryParseNeoForgeManifest(file))
                        .or(() -> parser.tryParseLegacyForgeManifest(file))
                        .orElse(null);
                if (m != null) {
                    out.add(new DiscoveredMod(file, m, ModFormat.JAR, loaderKey,
                            folder.getFileName().toString()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse mod: " + file, e);
        }
    }

    /**
     * Reads the {@code aprism.manifest.json} from the root of a {@code .aje}
     * zip archive.
     *
     * @param ajeFile the .aje archive path
     * @return the parsed manifest, or {@code null} if absent or unreadable
     */
    private AprismManifest readAjeManifest(Path ajeFile) {
        try (FileSystem fs = FileSystems.newFileSystem(ajeFile, (ClassLoader) null)) {
            Path entry = fs.getPath("aprism.manifest.json");
            if (Files.exists(entry)) {
                return parser.parse(entry);
            }
            return null;
        } catch (IOException | ManifestParseException e) {
            return null;
        }
    }

    /**
     * Groups discovered mods by their loader key.
     *
     * @param mods the discovered mods
     * @return a map of loader key to mods (insertion order preserved)
     */
    public static Map<String, List<DiscoveredMod>> groupByLoader(List<DiscoveredMod> mods) {
        Map<String, List<DiscoveredMod>> groups = new LinkedHashMap<>();
        for (DiscoveredMod dm : mods) {
            groups.computeIfAbsent(dm.loaderKey(), k -> new ArrayList<>()).add(dm);
        }
        return groups;
    }
}
