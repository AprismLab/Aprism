package com.aprism.api;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Represents a loaded mod in the Aprism runtime.
 * <p>
 * Reference identity invariant: for a given mod ID, every lookup performed
 * through the runtime, registry, or context returns the <em>same</em>
 * {@code ModContainer} instance. Callers may rely on reference equality
 * ({@code ==}) to identify a mod container across the entire lifecycle.
 * </p>
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ModContainer {

    /**
     * @return the unique, lowercase mod identifier
     */
    String getId();

    /**
     * @return the mod version string
     */
    String getVersion();

    /**
     * @return the human-readable display name
     */
    String getDisplayName();

    /**
     * @return the mod description
     */
    String getDescription();

    /**
     * @return the path to the mod's source jar or directory
     */
    Path getSourcePath();

    /**
     * @return the instantiated mod entrypoint object, or {@code null} if not yet constructed
     */
    Object getInstance();

    /**
     * Returns the mod instance typed to the requested class.
     *
     * @param type the expected type
     * @param <T> the expected type
     * @return an {@link Optional} containing the typed instance, or empty if absent or incompatible
     */
    <T> Optional<T> getInstance(Class<T> type);
}
