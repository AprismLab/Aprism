package com.aprism.loader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.aprism.api.ModContainer;
import com.aprism.manifest.AprismManifest;

/**
 * {@link ModContainer} for Bedrock Edition mods discovered in
 * {@code aprism_mods/}. Unlike JE mods, BE mods do not have Java entrypoints;
 * they consist of native binaries, Script API sources, and BP/RP content.
 *
 * <p>The native library map ({@link #nativeLibraries}) maps each target
 * platform to the list of native library entry paths inside the {@code .abe}
 * archive (e.g. {@code native/windows/mylib.dll}). The native Aprism injector
 * consumes this map to load the correct binaries for the running platform.
 *
 * <p>Per FACT.md 9.16, BE mods are Aprism native (`.abe`) and do NOT use the
 * Java classloader. This container holds metadata only; the actual native
 * loading is performed by the platform-specific injector.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LoadedBedrockModContainer implements ModContainer {

    private final AprismManifest manifest;
    private final Path sourcePath;
    private final Map<BedrockModDiscoverer.BedrockPlatform, List<String>> nativeLibraries;
    private final boolean hasBehaviorPack;
    private final boolean hasResourcePack;
    private final boolean hasScripts;

    /**
     * @param manifest        the parsed mod manifest
     * @param sourcePath      the path to the .abe archive on disk
     * @param nativeLibraries map of platform to native library entry paths
     * @param hasBehaviorPack whether the archive contains a {@code behavior_pack/} directory
     * @param hasResourcePack whether the archive contains a {@code resource_pack/} directory
     * @param hasScripts      whether the archive contains a {@code scripts/} directory
     */
    public LoadedBedrockModContainer(
            AprismManifest manifest,
            Path sourcePath,
            Map<BedrockModDiscoverer.BedrockPlatform, List<String>> nativeLibraries,
            boolean hasBehaviorPack,
            boolean hasResourcePack,
            boolean hasScripts) {
        this.manifest = manifest;
        this.sourcePath = sourcePath;
        this.nativeLibraries = nativeLibraries;
        this.hasBehaviorPack = hasBehaviorPack;
        this.hasResourcePack = hasResourcePack;
        this.hasScripts = hasScripts;
    }

    /**
     * @return the map of platform to native library entry paths inside the
     *         {@code .abe} archive (may be empty if the mod has no native code)
     */
    public Map<BedrockModDiscoverer.BedrockPlatform, List<String>> nativeLibraries() {
        return nativeLibraries;
    }

    /**
     * @return whether the archive contains a {@code behavior_pack/} directory
     */
    public boolean hasBehaviorPack() {
        return hasBehaviorPack;
    }

    /**
     * @return whether the archive contains a {@code resource_pack/} directory
     */
    public boolean hasResourcePack() {
        return hasResourcePack;
    }

    /**
     * @return whether the archive contains a {@code scripts/} directory
     */
    public boolean hasScripts() {
        return hasScripts;
    }

    /**
     * Returns the native library entry paths for the given platform, or an
     * empty list if the mod has no native code for that platform.
     *
     * @param platform the target platform
     * @return the list of library entry paths inside the .abe archive
     */
    public List<String> getNativeLibraries(BedrockModDiscoverer.BedrockPlatform platform) {
        return nativeLibraries.getOrDefault(platform, List.of());
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
        return null;
    }

    @Override
    public <T> java.util.Optional<T> getInstance(Class<T> type) {
        return java.util.Optional.empty();
    }

    /**
     * @return the parsed manifest
     */
    public AprismManifest getManifest() {
        return manifest;
    }
}
