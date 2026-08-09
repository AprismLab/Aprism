package com.aprism.loader.remap;

/**
 * A single entry in the supported JE version line. Describes everything the
 * loader must know to prepare a mod for a given Minecraft version: the
 * obfuscation profile (whether Intermediary remapping is required), the Java
 * baseline that version runs on, and where its mapping tables come from.
 *
 * <p>Part of the v26.1-Alpha.7 version-line foundation (goal #1): the
 * registry enumerates the supported line from JE 1.20 through 26.2 so the
 * loader can validate a requested version, select the right profile, and
 * locate the correct mappings without hardcoding per-version logic at the
 * call sites.
 *
 * @param versionId      the canonical version id (e.g. {@code "1.20.1"},
 *                       {@code "26.2"})
 * @param profile        the obfuscation profile for this version
 * @param javaBaseline   the minimum Java major version this Minecraft runs on
 * @param mappingsSource where mapping tables come from for the REMAPPED
 *                       profile ({@code "intermediary"}), or {@code "none"}
 *                       for NO_REMAP versions
 * @author BlockConnect@StarsailsClover
 */
public record VersionLineEntry(
        String versionId,
        McProfile profile,
        int javaBaseline,
        String mappingsSource) {

    /**
     * @return whether this version requires Intermediary remapping
     */
    public boolean requiresRemap() {
        return profile.requiresRemap();
    }
}
