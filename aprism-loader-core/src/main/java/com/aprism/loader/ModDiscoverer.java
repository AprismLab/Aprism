package com.aprism.loader;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.aprism.manifest.AprismManifest;
import com.aprism.manifest.ManifestParseException;
import com.aprism.manifest.ManifestParser;

/**
 * Scans a mods directory for Aprism mod archives (and legacy jars) and parses
 * their manifests, returning a sorted list of discovered mods.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ModDiscoverer {

    /** The archive format of a discovered mod. */
    public enum ModFormat { AJE, JAR, LITEMOD }

    /** A mod discovered on disk. */
    public record DiscoveredMod(Path path, AprismManifest manifest, ModFormat format) {
    }

    private final ManifestParser parser = new ManifestParser();

    /**
     * Discovers mods in the given directory, sorted by path.
     *
     * @param modsDir the mods directory
     * @return the list of discovered mods
     */
    public List<DiscoveredMod> discover(Path modsDir) {
        List<DiscoveredMod> mods = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            return mods;
        }
        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.sorted().forEach(file -> parseFile(file, mods));
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan mods directory: " + modsDir, e);
        }
        return mods;
    }

    /**
     * Parses a single candidate file and appends it to the results if it is a
     * recognized mod archive.
     *
     * @param file the candidate file
     * @param out  the accumulator for discovered mods
     */
    private void parseFile(Path file, List<DiscoveredMod> out) {
        String name = file.getFileName().toString().toLowerCase();
        try {
            if (name.endsWith(".aje")) {
                AprismManifest m = readAjeManifest(file);
                if (m != null) {
                    out.add(new DiscoveredMod(file, m, ModFormat.AJE));
                }
            } else if (name.endsWith(".litemod")) {
                AprismManifest m = parser.tryParseLiteLoaderManifest(file).orElse(null);
                if (m != null) {
                    out.add(new DiscoveredMod(file, m, ModFormat.LITEMOD));
                }
            } else if (name.endsWith(".jar")) {
                AprismManifest m = parser.tryParseFabricManifest(file)
                        .or(() -> parser.tryParseNeoForgeManifest(file))
                        .or(() -> parser.tryParseLegacyForgeManifest(file))
                        .orElse(null);
                if (m != null) {
                    out.add(new DiscoveredMod(file, m, ModFormat.JAR));
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
}
