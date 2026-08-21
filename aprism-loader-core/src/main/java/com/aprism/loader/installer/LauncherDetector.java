package com.aprism.loader.installer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Detects the user's Minecraft launcher type by inspecting the instance
 * directory structure (v26.6-Alpha.1).
 *
 * <p>The detector looks for characteristic files and directories that
 * identify each launcher:
 * <ul>
 *   <li>Prism Launcher: {@code instance.cfg} + {@code mmc-pack.json}</li>
 *   <li>ATLauncher: {@code instance.json} + {@code minecraft.json}</li>
 *   <li>GDLauncher: {@code config.json} with specific structure</li>
 * </ul>
 *
 * <p>If no launcher-specific files are found, the detector returns
 * {@link LauncherType#GENERIC}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LauncherDetector {

    /**
     * Detects the launcher type for the given instance directory.
     *
     * @param instanceDir the Minecraft instance directory
     * @return the detected launcher type, or GENERIC if unknown
     * @throws IOException if directory access fails
     */
    public LauncherType detect(Path instanceDir) throws IOException {
        Objects.requireNonNull(instanceDir, "instanceDir");

        if (!Files.isDirectory(instanceDir)) {
            return LauncherType.GENERIC;
        }

        // Check for Prism Launcher
        if (Files.isRegularFile(instanceDir.resolve("instance.cfg"))
                && Files.isRegularFile(instanceDir.resolve("mmc-pack.json"))) {
            return LauncherType.PRISM;
        }

        // Check for ATLauncher
        if (Files.isRegularFile(instanceDir.resolve("instance.json"))
                && Files.isRegularFile(instanceDir.resolve("minecraft.json"))) {
            return LauncherType.ATLAUNCHER;
        }

        // Check for GDLauncher
        Path gdConfig = instanceDir.resolve("config.json");
        if (Files.isRegularFile(gdConfig)) {
            // GDLauncher's config.json has a specific structure; we do a
            // lightweight check for the presence of "modpackVersion" or
            // "customJavaArgs" keys to confirm it's GDLauncher format.
            String content = Files.readString(gdConfig);
            if (content.contains("\"modpackVersion\"") || content.contains("\"customJavaArgs\"")) {
                return LauncherType.GD_LAUNCHER;
            }
        }

        // Unknown launcher
        return LauncherType.GENERIC;
    }

    /**
     * Attempts to detect the launcher type from a parent directory containing
     * multiple instances. Returns the most common launcher type found, or
     * GENERIC if no instances are detected.
     *
     * @param instancesDir the parent directory containing instance folders
     * @return the most common launcher type, or GENERIC
     * @throws IOException if directory access fails
     */
    public LauncherType detectFromParent(Path instancesDir) throws IOException {
        Objects.requireNonNull(instancesDir, "instancesDir");

        if (!Files.isDirectory(instancesDir)) {
            return LauncherType.GENERIC;
        }

        int prismCount = 0;
        int atlauncherCount = 0;
        int gdlauncherCount = 0;
        int genericCount = 0;

        try (var stream = Files.list(instancesDir)) {
            for (Path entry : stream.toList()) {
                if (Files.isDirectory(entry)) {
                    LauncherType type = detect(entry);
                    switch (type) {
                        case PRISM -> prismCount++;
                        case ATLAUNCHER -> atlauncherCount++;
                        case GD_LAUNCHER -> gdlauncherCount++;
                        case GENERIC -> genericCount++;
                    }
                }
            }
        }

        // Return the most common type
        int max = Math.max(Math.max(prismCount, atlauncherCount),
                Math.max(gdlauncherCount, genericCount));
        if (max == 0) {
            return LauncherType.GENERIC;
        }
        if (prismCount == max) {
            return LauncherType.PRISM;
        }
        if (atlauncherCount == max) {
            return LauncherType.ATLAUNCHER;
        }
        if (gdlauncherCount == max) {
            return LauncherType.GD_LAUNCHER;
        }
        return LauncherType.GENERIC;
    }
}
