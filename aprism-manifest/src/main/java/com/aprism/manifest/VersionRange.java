package com.aprism.manifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and evaluates Aprism version range expressions.
 *
 * <p>Supports the operators documented in Document 2 section 6:
 * <ul>
 *   <li>Comparators: {@code >=1.0.0}, {@code >1.0.0}, {@code <=1.0.0}, {@code <1.0.0}</li>
 *   <li>Tilde: {@code ~1.2} or {@code ~1.2.3} (patch range within minor)</li>
 *   <li>Caret: {@code ^1.2.3} (compatible range within major)</li>
 *   <li>Exact: {@code 1.0.0}</li>
 *   <li>Comma-AND: {@code >=1.0.0,<2.0.0}</li>
 *   <li>Maven brackets: {@code [1.0,2.0)}, {@code [1.0,]}, {@code [1.0.0]}</li>
 * </ul>
 * A version satisfies the range when it satisfies every clause (AND).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class VersionRange {

    private static final Pattern SEMVER =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$");

    private final List<Clause> clauses;
    private final String original;

    private VersionRange(String original, List<Clause> clauses) {
        this.original = original;
        this.clauses = List.copyOf(clauses);
    }

    /**
     * Parses a range expression.
     *
     * @param range the range string, or {@code "*"} / {@code ""} for "any version"
     * @return the parsed range
     * @throws IllegalArgumentException if the syntax is invalid (CHKAPRISM-RANGE-001)
     */
    public static VersionRange parse(String range) {
        String trimmed = range == null ? "" : range.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) {
            return new VersionRange(trimmed, List.of());
        }

        List<Clause> clauses = new ArrayList<>();

        if (trimmed.startsWith("[") || trimmed.startsWith("(")) {
            parseMavenBracket(trimmed, clauses);
        } else {
            for (String part : trimmed.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                clauses.addAll(parseComparator(p));
            }
        }

        if (clauses.isEmpty()) {
            throw new IllegalArgumentException("CHKAPRISM-RANGE-001: empty range '" + range + "'");
        }
        return new VersionRange(trimmed, clauses);
    }

    /**
     * Tests whether a version string satisfies this range.
     *
     * @param version the candidate version
     * @return {@code true} if the version is within range
     */
    public boolean contains(String version) {
        if (clauses.isEmpty()) {
            return true; // "any"
        }
        SemVer candidate = SemVer.parse(version);
        for (Clause clause : clauses) {
            if (!clause.test(candidate)) {
                return false;
            }
        }
        return true;
    }

    /** @return the original range text */
    @Override
    public String toString() {
        return original;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VersionRange vr && Objects.equals(original, vr.original);
    }

    @Override
    public int hashCode() {
        return Objects.hash(original);
    }

    // ---- internal parsing ------------------------------------------------

    private static void parseMavenBracket(String expr, List<Clause> out) {
        char left = expr.charAt(0);
        char right = expr.charAt(expr.length() - 1);
        if ((left != '[' && left != '(') || (right != ']' && right != ')')) {
            throw new IllegalArgumentException(
                    "CHKAPRISM-RANGE-001: malformed Maven bracket '" + expr + "'");
        }
        String inner = expr.substring(1, expr.length() - 1).trim();
        if (inner.isEmpty()) {
            throw new IllegalArgumentException(
                    "CHKAPRISM-RANGE-001: empty Maven bracket '" + expr + "'");
        }
        // Exact form [1.0.0]
        if (inner.indexOf(',') < 0) {
            SemVer v = SemVer.parse(inner);
            out.add(new Clause(v, true, true));
            out.add(new Clause(v, false, true));
            return;
        }
        String[] parts = inner.split(",", -1);
        String lo = parts[0].trim();
        String hi = parts.length > 1 ? parts[1].trim() : "";
        if (!lo.isEmpty()) {
            out.add(new Clause(SemVer.parse(lo), true, left == '['));
        }
        if (!hi.isEmpty()) {
            out.add(new Clause(SemVer.parse(hi), false, right == ']'));
        }
    }

    /**
     * Parses a single comparator token. Tilde and caret expand into two
     * clauses (lower inclusive + upper exclusive).
     */
    private static List<Clause> parseComparator(String expr) {
        List<Clause> out = new ArrayList<>();
        if (expr.startsWith(">=")) {
            out.add(new Clause(SemVer.parse(expr.substring(2).trim()), true, true));
        } else if (expr.startsWith("<=")) {
            out.add(new Clause(SemVer.parse(expr.substring(2).trim()), false, true));
        } else if (expr.startsWith(">")) {
            out.add(new Clause(SemVer.parse(expr.substring(1).trim()), true, false));
        } else if (expr.startsWith("<")) {
            out.add(new Clause(SemVer.parse(expr.substring(1).trim()), false, false));
        } else if (expr.startsWith("~")) {
            SemVer v = SemVer.parse(expr.substring(1).trim());
            out.add(new Clause(v, true, true));
            out.add(new Clause(new SemVer(v.major, v.minor + 1, 0, null), false, false));
        } else if (expr.startsWith("^")) {
            SemVer v = SemVer.parse(expr.substring(1).trim());
            SemVer upper = v.major == 0
                    ? (v.minor == 0 ? new SemVer(0, 0, v.patch + 1, null)
                                    : new SemVer(0, v.minor + 1, 0, null))
                    : new SemVer(v.major + 1, 0, 0, null);
            out.add(new Clause(v, true, true));
            out.add(new Clause(upper, false, false));
        } else if (expr.startsWith("=")) {
            SemVer v = SemVer.parse(expr.substring(1).trim());
            out.add(new Clause(v, true, true));
            out.add(new Clause(v, false, true));
        } else {
            // Exact match
            SemVer v = SemVer.parse(expr);
            out.add(new Clause(v, true, true));
            out.add(new Clause(v, false, true));
        }
        return out;
    }

    /**
     * A single comparator clause: a version is bound to {@code anchor} on the
     * lower ({@code lower=true}) or upper side, inclusive or exclusive.
     */
    private record Clause(SemVer anchor, boolean lower, boolean inclusive) {
        boolean test(SemVer v) {
            int cmp = v.compareTo(anchor);
            if (lower) {
                return inclusive ? cmp >= 0 : cmp > 0;
            }
            return inclusive ? cmp <= 0 : cmp < 0;
        }
    }

    /**
     * Minimal SemVer 2.0.0 value with prerelease comparison. Build metadata is
     * ignored for ordering, matching the SemVer rule that {@code 1.0.0+a ==
     * 1.0.0+b}.
     */
    static final class SemVer implements Comparable<SemVer> {
        final int major;
        final int minor;
        final int patch;
        final String pre;

        SemVer(int major, int minor, int patch, String pre) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.pre = pre;
        }

        static SemVer parse(String text) {
            String t = text == null ? "" : text.trim();
            if (t.isEmpty()) {
                throw new IllegalArgumentException("CHKAPRISM-RANGE-001: empty version");
            }
            if (t.startsWith("=")) {
                t = t.substring(1).trim();
            }
            Matcher m = SEMVER.matcher(t);
            if (!m.matches()) {
                throw new IllegalArgumentException(
                        "CHKAPRISM-RANGE-001: invalid SemVer '" + t + "'");
            }
            return new SemVer(
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)),
                    m.group(4));
        }

        @Override
        public int compareTo(SemVer o) {
            int c = Integer.compare(major, o.major);
            if (c != 0) return c;
            c = Integer.compare(minor, o.minor);
            if (c != 0) return c;
            c = Integer.compare(patch, o.patch);
            if (c != 0) return c;
            // A version without prerelease is greater than one with.
            if (pre == null && o.pre != null) return 1;
            if (pre != null && o.pre == null) return -1;
            if (pre == null) return 0;
            return comparePre(pre, o.pre);
        }

        private static int comparePre(String a, String b) {
            String[] as = a.split("\\.");
            String[] bs = b.split("\\.");
            for (int i = 0; i < Math.min(as.length, bs.length); i++) {
                Integer ai = tryNum(as[i]);
                Integer bi = tryNum(bs[i]);
                if (ai != null && bi != null) {
                    int c = Integer.compare(ai, bi);
                    if (c != 0) return c;
                } else if (ai != null) {
                    return -1; // numeric < alphanumeric
                } else if (bi != null) {
                    return 1;
                } else {
                    int c = as[i].compareTo(bs[i]);
                    if (c != 0) return c;
                }
            }
            return Integer.compare(as.length, bs.length);
        }

        private static Integer tryNum(String s) {
            try {
                return Integer.valueOf(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch + (pre == null ? "" : "-" + pre);
        }
    }
}
