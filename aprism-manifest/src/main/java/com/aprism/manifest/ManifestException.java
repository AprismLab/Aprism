package com.aprism.manifest;

/**
 * Root of the Aprism manifest exception hierarchy.
 *
 * <p>Thrown for any failure while locating, parsing, validating or resolving
 * a mod manifest. The two concrete subtypes are {@link ManifestParseException}
 * (structural JSON problems) and {@link ManifestValidationException} (schema
 * rule violations).
 *
 * @author BlockConnect@StarsailsClover
 */
public class ManifestException extends Exception {

    /** Constructs a new exception with a detail message. */
    public ManifestException(String message) {
        super(message);
    }

    /** Constructs a new exception with a detail message and cause. */
    public ManifestException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Thrown when a manifest cannot be parsed as JSON or is structurally
     * malformed (missing required fields, wrong types).
     */
    public static class ManifestParseException extends ManifestException {
        public ManifestParseException(String message) {
            super(message);
        }

        public ManifestParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when a parsed manifest violates a CHKAPRISM validation rule.
     */
    public static class ManifestValidationException extends ManifestException {
        public ManifestValidationException(String message) {
            super(message);
        }

        public ManifestValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
