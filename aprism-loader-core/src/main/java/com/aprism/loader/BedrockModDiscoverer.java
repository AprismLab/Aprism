package com.aprism.loader;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.aprism.manifest.AprismManifest;
import com.aprism.manifest.ManifestParseException;
import com.aprism.manifest.ManifestParser;

/**
 * Scans the Bedrock Edition {@code aprism_mods/} directory for Aprism native
 * mod archives ({@code .abe}) and resolves their native binary paths per
 * platform.
 *
 * <p>Per FACT.md 9.16, BE mod placement is:
 * <ul>
 *   <li>Native mod binaries: {@code com.mojang/aprism_mods/<modid>/native/<platform>/}</li>
 *   <li>Script API sources: {@code com.mojang/behavior_packs/<modid>/scripts/}</li>
 *   <li>BP/RP content: standard Bedrock {@code behavior_packs/} and {@code resource_packs/}</li>
 * </ul>
 *
 * <p>An {@code .abe} archive is a ZIP containing:
 * <ul>
 *   <li>{@code aprism.manifest.json} - the mod manifest</li>
 *   <li>{@code behavior_pack/} - Bedrock behavior pack content</li>
 *   <li>{@code resource_pack/} - Bedrock resource pack content</li>
 *   <li>{@code native/<platform>/} - per-platform native binaries (.dll, .so, .dylib)</li>
 *   <li>{@code scripts/} - Script API JavaScript sources</li>
 * </ul>
 *
 * <p>BE version support starts from 26.x only (FACT.md 9.16 hard scope boundary).
 * Mods targeting pre-26.x BE versions are rejected.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class BedrockModDiscoverer {

    /** The directory under com.mojang/ that holds .abe mod archives. */
    public static final String APRISM_MODS_DIR = "aprism_mods";

    /** Supported BE native platforms. */
    public enum BedrockPlatform {
        WINDOWS("windows", "win32", ".dll"),
        ANDROID("android", "linux", ".so"),
        ANDROID_ROOT("android-root", "linux", ".so"),
        IOS("ios", "darwin", ".dylib"),
        MACOS("macos", "darwin", ".dylib"),
        LINUX("linux", "linux", ".so");

        private final String id;
        private final String osName;
        private final String libExtension;

        BedrockPlatform(String id, String osName, String libExtension) {
            this.id = id;
            this.osName = osName;
            this.libExtension = libExtension;
        }

        /**
         * @return the platform identifier used in the {@code native/<platform>/} path
         */
        public String id() {
            return id;
        }

        /**
         * @return the native library file extension for this platform
         */
        public String libExtension() {
            return libExtension;
        }

        /**
         * Resolves the platform enum from its string id.
         *
         * @param id the platform id (case-insensitive)
         * @return the matching platform, or {@code null} if unknown
         */
        public static BedrockPlatform fromId(String id) {
            if (id == null) {
                return null;
            }
            for (BedrockPlatform p : values()) {
                if (p.id.equalsIgnoreCase(id)) {
                    return p;
                }
            }
            return null;
        }

        /**
         * Detects the current runtime platform.
         *
         * @return the detected platform, or {@code null} if unknown
         */
        public static BedrockPlatform detect() {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                return WINDOWS;
            }
            if (os.contains("mac") || os.contains("darwin")) {
                return MACOS;
            }
            if (os.contains("linux") || os.contains("nix")) {
                return LINUX;
            }
            return null;
        }
    }

    /** A mod discovered in the BE aprism_mods/ directory. */
    public record DiscoveredBedrockMod(
            Path archivePath,
            AprismManifest manifest,
            String modId,
            Map<BedrockPlatform, List<String>> nativeLibraries,
            boolean hasBehaviorPack,
            boolean hasResourcePack,
            boolean hasScripts) {
    }

    private final ManifestParser parser = new ManifestParser();

    /**
     * Scans the {@code aprism_mods/} directory under the given game root for
     * {@code .abe} mod archives, parses their manifests, and resolves native
     * binary paths per platform.
     *
     * @param gameRoot the BE game root (typically {@code com.mojang/})
     * @return the list of discovered BE mods (sorted by mod id)
     */
    public List<DiscoveredBedrockMod> discover(Path gameRoot) {
        List<DiscoveredBedrockMod> out = new ArrayList<>();
        Path modsDir = gameRoot.resolve(APRISM_MODS_DIR);
        if (!Files.isDirectory(modsDir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(modsDir)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".abe"))
                  .sorted()
                  .forEach(p -> parseAbe(p, out));
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan aprism_mods directory: " + modsDir, e);
        }
        out.sort((a, b) -> a.modId().compareTo(b.modId()));
        return out;
    }

    /**
     * Parses a single {@code .abe} archive and appends it to the results if
     * valid.
     *
     * @param abeFile the .abe archive path
     * @param out     the accumulator for discovered mods
     */
    private void parseAbe(Path abeFile, List<DiscoveredBedrockMod> out) {
        try (FileSystem fs = FileSystems.newFileSystem(abeFile, (ClassLoader) null)) {
            Path manifestPath = fs.getPath("aprism.manifest.json");
            if (!Files.exists(manifestPath)) {
                return;
            }
            AprismManifest manifest = parser.parse(manifestPath);
            String modId = manifest.id();

            // Resolve native libraries per platform
            Map<BedrockPlatform, List<String>> nativeLibs = resolveNativeLibraries(fs, modId);

            boolean hasBP = Files.isDirectory(fs.getPath("behavior_pack"));
            boolean hasRP = Files.isDirectory(fs.getPath("resource_pack"));
            boolean hasScripts = Files.isDirectory(fs.getPath("scripts"));

            out.add(new DiscoveredBedrockMod(
                    abeFile, manifest, modId, nativeLibs, hasBP, hasRP, hasScripts));
        } catch (IOException | ManifestParseException e) {
            // skip invalid archives
        }
    }

    /**
     * Scans the {@code native/<platform>/} directory inside an {@code .abe}
     * archive and collects the native library file paths for each platform.
     *
     * @param fs    the archive file system
     * @param modId the mod id (for logging)
     * @return a map of platform to list of library entry paths (may be empty)
     */
    private Map<BedrockPlatform, List<String>> resolveNativeLibraries(FileSystem fs, String modId) {
        Map<BedrockPlatform, List<String>> result = new java.util.HashMap<>();
        Path nativeRoot = fs.getPath("native");
        if (!Files.isDirectory(nativeRoot)) {
            return result;
        }
        for (BedrockPlatform platform : BedrockPlatform.values()) {
            Path platformDir = nativeRoot.resolve(platform.id());
            if (!Files.isDirectory(platformDir)) {
                continue;
            }
            List<String> libs = new ArrayList<>();
            try (Stream<Path> stream = Files.list(platformDir)) {
                stream.filter(p -> p.toString().toLowerCase().endsWith(platform.libExtension()))
                      .forEach(p -> libs.add(p.toString()));
            } catch (IOException e) {
                // skip unreadable directories
            }
            if (!libs.isEmpty()) {
                result.put(platform, libs);
            }
        }
        return result;
    }
}
