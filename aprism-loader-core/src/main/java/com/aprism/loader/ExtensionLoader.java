package com.aprism.loader;

import com.aprism.api.ExtensionContainer;
import com.aprism.api.ExtensionContext;
import com.aprism.api.ExtensionType;
import com.aprism.api.IAprismExtension;
import com.aprism.manifest.AprismExtensionManifest;
import com.aprism.manifest.VersionRange;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Scans the {@code aprism-extensions/} directory, validates each {@code .aep}
 * against the running Aprism + Minecraft version, and loads extensions before
 * any mods are scanned.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ExtensionLoader {

    private static final String EXTENSION_MANIFEST = "aprism.extension.json";

    private final String aprismVersion;
    private final String mcEdit;
    private final String mcVersion;
    private final Map<String, String> loaderFolders = new HashMap<>();
    private final List<LoadedExtension> loaded = new ArrayList<>();

    /**
     * @param aprismVersion the running Aprism Loader version
     * @param mcEdit        the Minecraft edition (JE or BE)
     * @param mcVersion     the running Minecraft version
     */
    public ExtensionLoader(String aprismVersion, String mcEdit, String mcVersion) {
        this.aprismVersion = aprismVersion;
        this.mcEdit = mcEdit;
        this.mcVersion = mcVersion;
    }

    /**
     * Scans the given extensions directory and loads all valid extensions.
     * Resets the internal accumulator on each call so repeated invocations
     * return only the extensions found in the most recent scan.
     *
     * @param extensionsDir the directory containing .aep files
     * @return the list of loaded extensions
     */
    public List<LoadedExtension> load(Path extensionsDir) {
        loaded.clear();
        loaderFolders.clear();
        if (!Files.isDirectory(extensionsDir)) {
            return List.of();
        }
        try (var stream = Files.list(extensionsDir)) {
            stream.filter(p -> p.toString().endsWith(".aep"))
                  .forEach(this::tryLoad);
        } catch (IOException e) {
            Logger.getLogger(ExtensionLoader.class.getName())
                  .warning("Failed to scan extensions directory: " + e.getMessage());
        }
        return List.copyOf(loaded);
    }

    private void tryLoad(Path aepFile) {
        try {
            Optional<AprismExtensionManifest> manifest = readManifest(aepFile);
            if (manifest.isEmpty()) {
                return;
            }
            AprismExtensionManifest m = manifest.get();
            if (!validate(m)) {
                return;
            }
            // For loader-support extensions, register the mod folder
            if ("loader-support".equals(m.type()) && m.loaderKey() != null) {
                String folder = loaderKeyToFolder(m.loaderKey());
                loaderFolders.put(m.loaderKey(), folder);
            }
            loaded.add(new LoadedExtension(m, aepFile));
        } catch (Exception e) {
            Logger.getLogger(ExtensionLoader.class.getName())
                  .warning("Failed to load extension " + aepFile + ": " + e.getMessage());
        }
    }

    private Optional<AprismExtensionManifest> readManifest(Path aepFile) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(aepFile, (ClassLoader) null)) {
            Path manifestPath = fs.getPath(EXTENSION_MANIFEST);
            if (!Files.exists(manifestPath)) {
                return Optional.empty();
            }
            try (InputStream is = Files.newInputStream(manifestPath)) {
                String json = new String(is.readAllBytes());
                return Optional.of(AprismExtensionManifest.fromJson(json));
            }
        }
    }

    private boolean validate(AprismExtensionManifest m) {
        if (m.extensionId() == null || m.extensionId().isBlank()) {
            return false;
        }
        if (m.type() == null) {
            return false;
        }
        if (m.aprismRange() == null) {
            return false;
        }
        try {
            VersionRange range = VersionRange.parse(m.aprismRange());
            if (!range.contains(normalizeAprismVersion(aprismVersion))) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (mcEdit != null && m.mcEdit() != null && !mcEdit.equalsIgnoreCase(m.mcEdit())) {
            return false;
        }
        if (mcVersion != null && m.mcVersion() != null && !mcVersion.equals(m.mcVersion())) {
            return false;
        }
        return true;
    }

    /**
     * Normalizes the running Aprism Loader version into the plain release form
     * used for {@code aprismRange} comparison. The runtime version carries a
     * {@code v} prefix and a stability/prerelease suffix (e.g.
     * {@code v26.0-Alpha.3}); SemVer range matching rejects both. This strips
     * the leading {@code v} and the prerelease/build suffix so that
     * {@code v26.0-Alpha.3} matches against a range written for its release
     * line (e.g. {@code [26.0.0,27.0.0)}).
     *
     * @param version the raw runtime version
     * @return the plain release form, or the input unchanged when it cannot be normalized
     */
    private static String normalizeAprismVersion(String version) {
        if (version == null || version.isBlank()) {
            return version;
        }
        String v = version.trim();
        if (v.startsWith("v")) {
            v = v.substring(1);
        }
        int dash = v.indexOf('-');
        if (dash >= 0) {
            v = v.substring(0, dash);
        }
        int plus = v.indexOf('+');
        if (plus >= 0) {
            v = v.substring(0, plus);
        }
        return v;
    }

    private static String loaderKeyToFolder(String loaderKey) {
        return switch (loaderKey) {
            case "Fa" -> "fabric-mods";
            case "Fo" -> "forge-mods";
            case "N" -> "neoforge-mods";
            case "L" -> "liteloader-mods";
            case "Q" -> "quilt-mods";
            default -> loaderKey.toLowerCase() + "-mods";
        };
    }

    /**
     * @return the map of loader key to mod folder for loaded loader-support extensions
     */
    public Map<String, String> getLoaderFolders() {
        return Map.copyOf(loaderFolders);
    }

    /**
     * Registers a loader-support folder at runtime. Called by
     * {@link ExtensionContextImpl#registerLoaderSupport} when an extension
     * dynamically declares a folder (as opposed to declaring it in its
     * manifest's {@code loaderKey} field).
     *
     * @param loaderKey the loader key (Fa, Fo, N, L, Q, or custom)
     * @param folder    the mod folder name relative to the game root
     */
    public void addLoaderFolder(String loaderKey, String folder) {
        loaderFolders.put(loaderKey, folder);
    }

    /**
     * Lists the names of embedded jar entries in a {@code .aep} archive. The
     * returned entry names can be used with
     * {@link #extractJar(Path, String, Path)} to extract individual jars for
     * classloader registration.
     *
     * @param aepFile the .aep file
     * @return the list of jar entry names inside the archive (may be empty)
     * @throws IOException if the archive cannot be read
     */
    public List<String> listEmbeddedJarNames(Path aepFile) throws IOException {
        List<String> jars = new ArrayList<>();
        try (FileSystem fs = FileSystems.newFileSystem(aepFile, (ClassLoader) null)) {
            Path root = fs.getPath("/");
            try (var stream = Files.walk(root)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                      .forEach(p -> jars.add(p.toString().startsWith("/") ? p.toString().substring(1) : p.toString()));
            }
        }
        return jars;
    }

    /**
     * Extracts a single embedded jar from a {@code .aep} archive to the given
     * target file.
     *
     * @param aepFile   the .aep file
     * @param entryName the jar entry name (from {@link #listEmbeddedJarNames})
     * @param targetFile the destination file
     * @throws IOException if the entry cannot be read or written
     */
    public void extractJar(Path aepFile, String entryName, Path targetFile) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(aepFile, (ClassLoader) null);
             InputStream is = Files.newInputStream(fs.getPath("/" + entryName))) {
            Files.copy(is, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * A loaded extension with its manifest and source path.
     *
     * @param manifest   the parsed extension manifest
     * @param sourcePath the path to the .aep file
     */
    public record LoadedExtension(AprismExtensionManifest manifest, Path sourcePath) {
    }
}
