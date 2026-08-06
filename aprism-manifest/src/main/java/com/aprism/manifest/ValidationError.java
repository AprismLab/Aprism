package com.aprism.manifest;

/**
 * A single problem found while validating an {@link AprismManifest}.
 *
 * <p>Codes follow the {@code CHKAPRISM-*} scheme documented in Document 2
 * section 9 (e.g. {@code CHKAPRISM-MANIFEST-003}, {@code CHKAPRISM-DEP-005}).
 *
 * @author BlockConnect@StarsailsClover
 */
public record ValidationError(
        String code,
        Severity severity,
        String field,
        String message
) {

    /** How serious a validation problem is. */
    public enum Severity {
        /** Hard failure; the mod cannot be loaded. */
        ERROR,
        /** Soft failure; the mod loads but tooling should warn. */
        WARNING
    }

    /**
     * Convenience factory for error-severity problems.
     *
     * @param code    the CHKAPRISM code
     * @param field   the offending field path, or {@code ""} for whole-manifest
     * @param message human-readable detail
     * @return a new error validation
     */
    public static ValidationError error(String code, String field, String message) {
        return new ValidationError(code, Severity.ERROR, field, message);
    }

    /**
     * Convenience factory for warning-severity problems.
     *
     * @param code    the CHKAPRISM code
     * @param field   the offending field path, or {@code ""} for whole-manifest
     * @param message human-readable detail
     * @return a new warning validation
     */
    public static ValidationError warning(String code, String field, String message) {
        return new ValidationError(code, Severity.WARNING, field, message);
    }
}
