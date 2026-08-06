package com.aprism.manifest;

/**
 * Thrown when dependency resolution fails: a required dependency is missing, a
 * version conflict exists, or a dependency cycle is detected.
 *
 * @author BlockConnect@StarsailsClover
 */
public class DependencyResolutionException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public DependencyResolutionException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public DependencyResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
