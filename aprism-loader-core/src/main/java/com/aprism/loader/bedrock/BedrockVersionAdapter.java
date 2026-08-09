package com.aprism.loader.bedrock;

import java.util.Locale;
import java.util.Objects;

/**
 * Maps a running Minecraft Bedrock Edition version to an entry in the
 * fail-closed {@link BedrockVersionDatabase} (FACT.md 9.9 / 9.16: the version
 * adapter is mandatory infrastructure; per 9.16, BE support starts at 26.x).
 *
 * <p>The adapter performs three steps, all fail-closed:
 * <ol>
 *   <li><b>Normalize</b> the raw version string: trim, lowercase, strip a
 *       leading {@code v}. Suffixes (e.g. {@code -preview}) are preserved so
 *       they must match a signature DB entry exactly.</li>
 *   <li><b>Scope check</b>: only major versions &gt;= {@link #MIN_MAJOR_VERSION}
 *       (26) are supported. Pre-26.x Bedrock versions are refused outright,
 *       even if a signature DB entry for them somehow existed (FACT.md 9.16
 *       hard boundary).</li>
 *   <li><b>Resolve</b>: look the normalized version up in the database. A miss
 *       is refused (the version may simply have no signatures yet).</li>
 * </ol>
 *
 * @author BlockConnect@StarsailsClover
 */
public final class BedrockVersionAdapter {

    /** Minimum supported Bedrock major version (FACT.md 9.16: BE only from 26.x). */
    public static final int MIN_MAJOR_VERSION = 26;

    /** Why a version could not be resolved to a database entry. */
    public enum RefusalReason {
        /** The raw version string was null/blank or not parseable. */
        UNPARSEABLE,
        /** The version is pre-26.x, outside the BE support scope. */
        OUT_OF_SCOPE,
        /** The version is in scope but has no signature database entry. */
        NOT_IN_DATABASE
    }

    /**
     * The outcome of adapting a raw version string.
     *
     * @param resolved          whether the version resolved to a database entry
     * @param normalizedVersion the normalized version key (null when unparseable)
     * @param entry             the matched database entry (null unless resolved)
     * @param refusal           the refusal reason (null when resolved)
     */
    public record AdapterResult(boolean resolved, String normalizedVersion,
                                BedrockVersionDatabase.VersionEntry entry, RefusalReason refusal) {
        /** @return true if the version resolved to a database entry */
        public boolean isResolved() {
            return resolved;
        }
    }

    /**
     * Adapts a raw Bedrock version string against the given database.
     *
     * @param rawVersion the version as reported by the platform
     * @param database   the fail-closed signature version database
     * @return the adapter result (resolved, or refused with a reason)
     */
    public AdapterResult adapt(String rawVersion, BedrockVersionDatabase database) {
        Objects.requireNonNull(database, "database must not be null");

        String normalized = normalize(rawVersion);
        if (normalized == null) {
            return new AdapterResult(false, null, null, RefusalReason.UNPARSEABLE);
        }

        int major = majorOf(normalized);
        if (major < MIN_MAJOR_VERSION) {
            return new AdapterResult(false, normalized, null, RefusalReason.OUT_OF_SCOPE);
        }

        var entry = database.lookup(normalized);
        if (entry.isEmpty()) {
            return new AdapterResult(false, normalized, null, RefusalReason.NOT_IN_DATABASE);
        }
        return new AdapterResult(true, normalized, entry.get(), null);
    }

    /**
     * Normalizes a raw version string: trims, lowercases, and strips a leading
     * {@code v}. Returns {@code null} for null/blank input. Suffixes after the
     * numeric part are preserved verbatim (they must match the DB exactly).
     *
     * @param raw the raw version string
     * @return the normalized key, or null if unparseable
     */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("v")) {
            s = s.substring(1);
        }
        return s.isEmpty() ? null : s;
    }

    /**
     * Extracts the major version number from a normalized version key.
     * Returns {@code -1} if the leading numeric segment is absent or invalid,
     * which always fails the scope check.
     *
     * @param normalized the normalized version key
     * @return the major version, or -1
     */
    static int majorOf(String normalized) {
        // Split on '.' or '-': "26.2.0" -> 26, "26.2.0-preview" -> 26.
        int end = normalized.length();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '.' || c == '-') {
                end = i;
                break;
            }
        }
        String head = normalized.substring(0, end);
        try {
            return Integer.parseInt(head);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
