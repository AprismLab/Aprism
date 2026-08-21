package com.aprism.loader.installer;

/**
 * Supported launcher types for Aprism installation (v26.6-Alpha.1).
 *
 * <p>Each launcher has its own configuration format and installation path.
 * The installer detects the launcher type and generates appropriate
 * configuration files.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum LauncherType {

    /**
     * Prism Launcher (and forks like PolyMC, MultiMC). Uses JSON instance
     * configuration with {@code libraries} array for javaagent injection.
     */
    PRISM("Prism Launcher", "instance.cfg", "mmc-pack.json"),

    /**
     * ATLauncher. Uses JSON instance configuration with {@code loaders}
     * and {@code libraries} arrays.
     */
    ATLAUNCHER("ATLauncher", "instance.json", "minecraft.json"),

    /**
     * GDLauncher. Uses a simpler JSON configuration format.
     */
    GD_LAUNCHER("GDLauncher", "config.json", null),

    /**
     * Generic/unknown launcher. The installer generates a standalone
     * launch script (batch/shell) with the javaagent argument.
     */
    GENERIC("Generic", null, null);

    private final String displayName;
    private final String instanceConfigFile;
    private final String packConfigFile;

    LauncherType(String displayName, String instanceConfigFile, String packConfigFile) {
        this.displayName = displayName;
        this.instanceConfigFile = instanceConfigFile;
        this.packConfigFile = packConfigFile;
    }

    /**
     * @return human-readable launcher name for display in reports
     */
    public String displayName() {
        return displayName;
    }

    /**
     * @return the instance-level configuration file name, or null if not
     *         applicable (e.g. GENERIC)
     */
    public String instanceConfigFile() {
        return instanceConfigFile;
    }

    /**
     * @return the pack-level configuration file name, or null if not
     *         applicable
     */
    public String packConfigFile() {
        return packConfigFile;
    }
}
