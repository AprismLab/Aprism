package com.aprism.loader;

import java.nio.file.Path;
import java.util.Optional;

import com.aprism.api.ExtensionContainer;
import com.aprism.api.ExtensionType;
import com.aprism.manifest.AprismExtensionManifest;

/**
 * Mutable {@link ExtensionContainer} backed by a loaded extension. The
 * instance field is populated when the extension's entrypoint class is
 * instantiated by the runtime.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LoadedExtensionContainer implements ExtensionContainer {

    private final AprismExtensionManifest manifest;
    private final Path sourcePath;
    private Object instance;

    /**
     * @param manifest   the parsed extension manifest
     * @param sourcePath the path to the .aep file
     */
    public LoadedExtensionContainer(AprismExtensionManifest manifest, Path sourcePath) {
        this.manifest = manifest;
        this.sourcePath = sourcePath;
    }

    /**
     * @return the parsed manifest
     */
    public AprismExtensionManifest getManifest() {
        return manifest;
    }

    /**
     * Sets the instantiated extension entrypoint object.
     *
     * @param instance the extension instance (may be {@code null})
     */
    public void setInstance(Object instance) {
        this.instance = instance;
    }

    @Override
    public String getExtensionId() {
        return manifest.extensionId();
    }

    @Override
    public ExtensionType getType() {
        return ExtensionType.parse(manifest.type());
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
