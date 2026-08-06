package com.aprism.manifest;

/**
 * Thrown when a manifest file cannot be parsed, whether due to a missing file,
 * malformed JSON, or an unrecognized legacy format.
 *
 * @author BlockConnect@StarsailsClover
 */
public class ManifestParseException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public ManifestParseException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public ManifestParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
