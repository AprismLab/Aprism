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
            if (!range.contains(aprismVersion)) {
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
     * A loaded extension with its manifest and source path.
     *
     * @param manifest   the parsed extension manifest
     * @param sourcePath the path to the .aep file
     */
    public record LoadedExtension(AprismExtensionManifest manifest, Path sourcePath) {
    }
}
