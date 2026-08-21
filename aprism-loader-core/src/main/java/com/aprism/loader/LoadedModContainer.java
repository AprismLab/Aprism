package com.aprism.loader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.aprism.api.ModContainer;
import com.aprism.manifest.AprismManifest;

/**
 * Mutable {@link ModContainer} backed by a discovered mod. The instance field
 * is populated by {@link EntryPointInvoker} when the mod's entrypoint class is
 * instantiated, and remains {@code null} until then.
 *
 * <p>Reference identity invariant (FACT.md 9.2): for a given mod id, every
 * lookup through the runtime returns the SAME {@code LoadedModContainer}
 * instance. Callers may rely on reference equality across the lifecycle.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LoadedModContainer implements ModContainer {

    private final AprismManifest manifest;
    private final Path sourcePath;
    private final String loaderKey;
    private Object instance;
    private final List<Path> extractedJarPaths = new ArrayList<>();

    /**
     * @param manifest   the parsed mod manifest
     * @param sourcePath the path to the mod archive on disk
     * @param loaderKey  the loader key that discovered this mod (e.g.
     *                   {@code "aprism"}, {@code "Fa"}, ...)
     */
    public LoadedModContainer(AprismManifest manifest, Path sourcePath, String loaderKey) {
        this.manifest = manifest;
        this.sourcePath = sourcePath;
        this.loaderKey = loaderKey;
    }

    /**
     * Records a jar path extracted from a .aje archive into the temp directory.
     * Used by the annotation scanner when the manifest has no entrypoints.
     *
     * @param jarPath the extracted jar path
     * @since v26.5-Alpha.1
     */
    public void addExtractedJarPath(Path jarPath) {
        extractedJarPaths.add(jarPath);
    }

    /**
     * Returns the jar paths extracted from a .aje archive into the temp
     * directory. For plain .jar mods this list is empty (the source path
     * itself is the classpath entry).
     *
     * @return the list of extracted jar paths (immutable copy)
     * @since v26.5-Alpha.1
     */
    public List<Path> getExtractedJarPaths() {
        return List.copyOf(extractedJarPaths);
    }

    /**
     * @return the parsed manifest
     */
    public AprismManifest getManifest() {
        return manifest;
    }

    /**
     * @return the loader key that discovered this mod
     */
    public String getLoaderKey() {
        return loaderKey;
    }

    /**
     * Sets the instantiated mod entrypoint object. Called by the entrypoint
     * invoker after constructing the entrypoint class.
     *
     * @param instance the mod instance (may be {@code null})
     */
    public void setInstance(Object instance) {
        this.instance = instance;
    }

    @Override
    public String getId() {
        return manifest.id();
    }

    @Override
    public String getVersion() {
        return manifest.version();
    }

    @Override
    public String getDisplayName() {
        return manifest.displayName();
    }

    @Override
    public String getDescription() {
        return manifest.description();
    }

    @Override
    public Path getSourcePath() {
        return sourcePath;
    }

    @Override
    public Object getInstance() {
        return instance;
    }

    @Override
    public <T> Optional<T> getInstance(Class<T> type) {
        if (instance == null) {
            return Optional.empty();
        }
        return type.isInstance(instance) ? Optional.of(type.cast(instance)) : Optional.empty();
    }
}
