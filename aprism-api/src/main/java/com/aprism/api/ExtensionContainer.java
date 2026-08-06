package com.aprism.api;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Represents a loaded Aprism Extension in the runtime.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ExtensionContainer {

    /**
     * @return the unique extension identifier
     */
    String getExtensionId();

    /**
     * @return the extension type
     */
    ExtensionType getType();

    /**
     * @return the path to the extension's source .aep file
     */
    Path getSourcePath();

    /**
     * @return the instantiated extension entrypoint, or null if not yet constructed
     */
    Object getInstance();

    /**
     * Returns the extension instance typed to the requested class.
     *
     * @param type the expected type
     * @param <T>  the expected type
     * @return an Optional containing the typed instance, or empty if absent or incompatible
     */
    <T> Optional<T> getInstance(Class<T> type);
}
