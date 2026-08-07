package com.aprism.loader.remap;

/**
 * Determines the cross-version compatibility profile for a running Minecraft
 * build. Mirrors FACT.md 9.7: the 1.21.11 → 26.1 boundary is where Mojang
 * began shipping unobfuscated jars, so the remapping strategy flips there.
 *
 * <ul>
 *   <li><b>REMAPPED</b> — Minecraft versions before 26.1 (the 1.x line).
 *       The game jar uses obfuscated official names; mods compiled against
 *       Intermediary names must be remapped intermediary → official at load
 *       time.</li>
 *   <li><b>NO_REMAP</b> — Minecraft 26.1 and later. The game jar ships
 *       unobfuscated; mods target the official names directly and the
 *       remapper is a no-op.</li>
 * </ul>
 *
 * @author BlockConnect@StarsailsClover
 */
public enum McProfile {

    /** Pre-26.1 (1.x line): obfuscated, Intermediary remapping required. */
    REMAPPED,

    /** 26.1+: unobfuscated, no remapping required. */
    NO_REMAP;

    /** The first unobfuscated Minecraft release: 26.1. */
    private static final int NO_REMAP_MAJOR = 26;

    /**
     * Selects the profile for a Minecraft version string.
     *
     * <p>Accepts {@code major.minor[.patch]} forms such as {@code 1.21.4},
     * {@code 26.1.2}, and {@code 26.2}. Unparseable or {@code null} input
     * defaults to {@link #NO_REMAP} (the safest assumption for unknown or
     * future builds, which will follow the unobfuscated line).
     *
     * @param mcVersion the Minecraft version string
     * @return the profile for that version
     */
    public static McProfile of(String mcVersion) {
        int major = majorOf(mcVersion);
        if (major <= 0) {
            return NO_REMAP;
        }
        return major >= NO_REMAP_MAJOR ? NO_REMAP : REMAPPED;
    }

    /**
     * @return whether this profile requires Intermediary remapping
     */
    public boolean requiresRemap() {
        return this == REMAPPED;
    }

    /**
     * Selects a {@link Remapper} for this profile. For {@link #NO_REMAP} the
     * returned remapper is the identity; for {@link #REMAPPED} the caller must
     * supply the intermediary→official remapper built from the loaded
     * mappings.
     *
     * @param intermediaryToOfficial the remapper for the remapped profile; ignored for no-remap
     * @return the remapper appropriate to this profile
     */
    public Remapper selectRemapper(Remapper intermediaryToOfficial) {
        return this == NO_REMAP ? Remapper.noop() : intermediaryToOfficial;
    }

    /**
     * Extracts the leading numeric component of a version string, or 0 if the
     * string does not start with a number.
     *
     * @param version the version string
     * @return the major component, or 0 when unparseable
     */
    private static int majorOf(String version) {
        if (version == null) {
            return 0;
        }
        String trimmed = version.trim();
        int i = 0;
        while (i < trimmed.length() && Character.isDigit(trimmed.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(trimmed.substring(0, i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
