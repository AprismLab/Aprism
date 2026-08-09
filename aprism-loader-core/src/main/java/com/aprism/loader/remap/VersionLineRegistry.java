package com.aprism.loader.remap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Enumerates the JE version line Aprism supports and resolves a concrete
 * Minecraft version string into a {@link VersionLineEntry}.
 *
 * <p>Part of the v26.1-Alpha.7 version-line foundation (goal #1). The line
 * spans JE {@code 1.20} through {@code 26.2}. It is expressed as a list of
 * contiguous <em>segments</em>, each covering a {@code major.minor} prefix
 * with a fixed obfuscation profile, Java baseline, and mappings source. This
 * avoids enumerating every patch release while still giving the loader exact,
 * per-segment characteristics.
 *
 * <p>Segments (in order):
 * <ul>
 *   <li>{@code 1.20.x} — REMAPPED, Java 17, Intermediary mappings</li>
 *   <li>{@code 1.21.x} — REMAPPED, Java 21, Intermediary mappings</li>
 *   <li>{@code 26.x}  — NO_REMAP, Java 25, no mappings (unobfuscated)</li>
 * </ul>
 *
 * <p>Resolution matches the version's {@code major.minor} prefix against the
 * segments. Versions whose major component is below 1.20 (e.g. {@code 1.19})
 * resolve to {@link Optional#empty()} — they are below the supported line.
 * Versions at or above the 26 line resolve to the NO_REMAP segment (the
 * unobfuscated line continues indefinitely), but
 * {@link #isWithinSupportedLine(String)} reports whether the version falls
 * within the explicitly supported {@code 1.20 .. 26.2} window.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class VersionLineRegistry {

    /** A contiguous segment of the version line. */
    private record Segment(String minorPrefix, McProfile profile, int javaBaseline,
            String mappingsSource) {
    }

    private static final List<Segment> SEGMENTS = List.of(
            new Segment("1.20", McProfile.REMAPPED, 17, "intermediary"),
            new Segment("1.21", McProfile.REMAPPED, 21, "intermediary"),
            new Segment("26", McProfile.NO_REMAP, 25, "none"));

    /** Lowest supported version on the line. */
    public static final String LINE_START = "1.20";

    /** Highest explicitly supported version on the line. */
    public static final String LINE_END = "26.2";

    private VersionLineRegistry() {
    }

    /**
     * Resolves a Minecraft version string to its {@link VersionLineEntry}.
     *
     * <p>Matching is by {@code major.minor} prefix. {@code 1.20.4} matches the
     * {@code 1.20} segment; {@code 26.2} and any later {@code 26.x} match the
     * {@code 26} segment. Versions below {@code 1.20} (e.g. {@code 1.19.4})
     * return {@link Optional#empty()}.
     *
     * @param mcVersion the Minecraft version string
     * @return the entry, or empty when the version is below the supported line
     */
    public static Optional<VersionLineEntry> resolve(String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank()) {
            return Optional.empty();
        }
        String prefix = majorMinorOf(mcVersion.trim());
        if (prefix.isEmpty()) {
            return Optional.empty();
        }
        for (Segment segment : SEGMENTS) {
            if (prefix.equals(segment.minorPrefix()) || prefix.startsWith(segment.minorPrefix() + ".")) {
                return Optional.of(new VersionLineEntry(
                        mcVersion.trim(), segment.profile(),
                        segment.javaBaseline(), segment.mappingsSource()));
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the version falls within the explicitly supported window
     * {@code [1.20, 26.2]}. Versions above {@code 26.2} resolve to the
     * NO_REMAP segment but are reported as outside the explicit window.
     *
     * @param mcVersion the Minecraft version string
     * @return true when the version is within {@code 1.20 .. 26.2}
     */
    public static boolean isWithinSupportedLine(String mcVersion) {
        Optional<VersionLineEntry> entry = resolve(mcVersion);
        if (entry.isEmpty()) {
            return false;
        }
        return compareVersions(entry.get().versionId(), LINE_END) <= 0;
    }

    /**
     * @return an unmodifiable snapshot of the supported line as entries, one
     *         per segment, using the segment prefix as the version id
     */
    public static List<VersionLineEntry> supportedLine() {
        List<VersionLineEntry> line = new ArrayList<>();
        for (Segment segment : SEGMENTS) {
            line.add(new VersionLineEntry(segment.minorPrefix(), segment.profile(),
                    segment.javaBaseline(), segment.mappingsSource()));
        }
        return List.copyOf(line);
    }

    /**
     * Human-readable description of the supported line, e.g.
     * {@code "1.20 .. 26.2"}.
     *
     * @return the supported line description
     */
    public static String describeLine() {
        return LINE_START + " .. " + LINE_END;
    }

    /**
     * Extracts the {@code major.minor} prefix of a version string, e.g.
     * {@code 1.20} from {@code 1.20.4}, {@code 26.2} from {@code 26.2}.
     * Returns an empty string when the version has no parseable numeric major.
     */
    private static String majorMinorOf(String version) {
        String[] parts = version.split("\\.");
        if (parts.length == 0 || !isNumeric(parts[0])) {
            return "";
        }
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + "." + (isNumeric(parts[1]) ? parts[1] : "0");
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two dotted version strings numerically component by component.
     *
     * @return negative if a &lt; b, 0 if equal, positive if a &gt; b
     */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length && isNumeric(pa[i]) ? Integer.parseInt(pa[i]) : 0;
            int vb = i < pb.length && isNumeric(pb[i]) ? Integer.parseInt(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }
}
