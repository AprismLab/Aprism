package com.aprism.packaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates that an {@code .aje}/{@code .abe} manifest's
 * {@code depends.aprism} floor is consistent with the loader baseline the
 * mod was compiled against (v26.9-Alpha.8).
 *
 * <p><b>Why this exists:</b> a manifest that declares a floor older than the
 * compiled-against loader lets an old loader attempt to load a newer artifact.
 * Between Aprism generations the agent's ASM/MixinExtras supply changes, so
 * such a load fails in confusing ways far from the real cause. The packaging
 * plugin therefore refuses to assemble an archive whose floor is below the
 * declared baseline.
 *
 * <p>The version grammar mirrors {@code com.aprism.manifest.VersionRange}:
 * an optional {@code v} prefix, {@code MAJOR[.MINOR[.PATCH]]} with an optional
 * {@code -Alpha.N} pre-release suffix. Release ordering follows SemVer:
 * {@code 26.8} &gt; {@code 26.8-Alpha.9} &gt; {@code 26.8-Alpha.1}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ManifestBaselineCheck {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** The check outcome: either clean or a list of actionable problems. */
    public record Result(boolean ok, List<String> problems) {

        /** @return a passing result */
        public static Result pass() {
            return new Result(true, List.of());
        }

        /** @return a failing result with one problem */
        public static Result fail(String problem) {
            return new Result(false, List.of(problem));
        }
    }

    /** A parsed Aprism-style version. */
    private record Version(int major, int minor, int patch, int alpha, boolean release)
            implements Comparable<Version> {

        @Override
        public int compareTo(Version other) {
            int byMajor = Integer.compare(major, other.major);
            if (byMajor != 0) {
                return byMajor;
            }
            int byMinor = Integer.compare(minor, other.minor);
            if (byMinor != 0) {
                return byMinor;
            }
            int byPatch = Integer.compare(patch, other.patch);
            if (byPatch != 0) {
                return byPatch;
            }
            // A release outranks any pre-release of the same version.
            if (release != other.release) {
                return release ? 1 : -1;
            }
            return Integer.compare(alpha, other.alpha);
        }
    }

    private ManifestBaselineCheck() {
    }

    /**
     * Validates one manifest's Aprism dependency floor against a baseline.
     *
     * @param dependsAprism the raw {@code depends.aprism} value (may be null
     *        when the manifest omits the dependency entirely)
     * @param baseline the loader version the mod was compiled against
     * @return the check result
     */
    public static Result check(String dependsAprism, String baseline) {
        List<String> problems = new ArrayList<>();
        if (baseline == null || baseline.isBlank()) {
            return Result.fail("aprismPackaging.aprismBaseline is not set: declare "
                    + "the loader version this mod is compiled against");
        }
        Version baselineVersion = parseOrNull(baseline);
        if (baselineVersion == null) {
            return Result.fail("cannot parse aprismPackaging.aprismBaseline: "
                    + baseline);
        }
        if (dependsAprism == null || dependsAprism.isBlank()) {
            return Result.fail("manifest declares no depends.aprism floor; add "
                    + "\"aprism\": \">=" + baseline + "\" so old loaders refuse "
                    + "this artifact");
        }
        String floor = floorOf(dependsAprism);
        if (floor == null) {
            return Result.fail("depends.aprism must declare a floor (>=X) or a "
                    + "bracket range; got: " + dependsAprism);
        }
        Version floorVersion = parseOrNull(floor);
        if (floorVersion == null) {
            return Result.fail("cannot parse the depends.aprism floor: " + floor);
        }
        if (floorVersion.compareTo(baselineVersion) < 0) {
            problems.add("depends.aprism floor " + floor + " is BELOW the "
                    + "compiled-against baseline " + baseline
                    + ": an older loader would attempt to load this artifact "
                    + "and fail in the agent (ASM/MixinExtras supply differs "
                    + "between generations). Raise the floor to >=" + baseline + ".");
        }
        return problems.isEmpty() ? Result.pass()
                : new Result(false, List.copyOf(problems));
    }

    /**
     * Extracts the lower bound of a range expression
     * ({@code >=X}, {@code >X}, {@code [X,Y)}, {@code [X,)}).
     *
     * @param range the range expression
     * @return the lower bound text, or null when none can be found
     */
    static String floorOf(String range) {
        String trimmed = range.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("(")) {
            int comma = trimmed.indexOf(',');
            String lower = comma > 0
                    ? trimmed.substring(1, comma)
                    : trimmed.substring(1);
            return lower.isBlank() ? null : lower.trim();
        }
        for (String prefix : new String[] {">=", ">"}) {
            if (trimmed.startsWith(prefix)) {
                String rest = trimmed.substring(prefix.length()).trim();
                return rest.isBlank() ? null : rest;
            }
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * Parses an Aprism version, returning null instead of throwing so the
     * caller can report an actionable message.
     */
    static Version parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        String numeric = text;
        int alpha = -1;
        boolean release = true;
        int alphaIndex = text.toLowerCase(Locale.ROOT).indexOf("-alpha");
        if (alphaIndex >= 0) {
            numeric = text.substring(0, alphaIndex);
            release = false;
            String suffix = text.substring(alphaIndex + "-alpha".length())
                    .replace(".", "").trim();
            if (suffix.isEmpty()) {
                alpha = 1;
            } else {
                try {
                    alpha = Integer.parseInt(suffix);
                } catch (NumberFormatException malformed) {
                    return null;
                }
            }
        }
        String[] parts = numeric.split("\\.");
        if (parts.length == 0 || parts[0].isBlank()) {
            return null;
        }
        try {
            int major = Integer.parseInt(parts[0].trim());
            int minor = parts.length > 1 && !parts[1].isBlank()
                    ? Integer.parseInt(parts[1].trim()) : 0;
            int patch = parts.length > 2 && !parts[2].isBlank()
                    ? Integer.parseInt(parts[2].trim()) : 0;
            return new Version(major, minor, patch, alpha, release);
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
