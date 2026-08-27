package com.aprism.packaging;

import java.util.ArrayList;
import java.util.List;

/**
 * Gradle extension for configuring Aprism packaging. Exposed under the
 * {@code aprismPackaging} block in a build script.
 *
 * <p>Directory-valued fields are plain relative paths resolved against the
 * project directory.
 *
 * @author BlockConnect@StarsailsClover
 */
public class AprismPackagingExtension {

    private String manifestFile = "aprism.manifest.json";
    private String editorManifestFile;
    private String mainJar;
    private String extraResources = "src/main/resources-shared";
    private String mixinConfigs = "src/main/mixins";
    private String libDir = "src/main/lib";
    private String outputDir = "build/aprism";
    private String compatibilityGroup = "legacy";
    private List<String> nativeTargets = new ArrayList<>();
    private String minecraftEdition;
    private String minecraftVersion;

    /**
     * @return the manifest file path, relative to the project directory
     */
    public String getManifestFile() {
        return manifestFile;
    }

    /**
     * @param manifestFile the manifest file path, relative to the project directory
     */
    public void setManifestFile(String manifestFile) {
        this.manifestFile = manifestFile;
    }

    /**
     * @return optional AprismWarp editor capability manifest path
     */
    public String getEditorManifestFile() {
        return editorManifestFile;
    }

    /**
     * @param editorManifestFile optional {@code aprismwarp.editor.json} path
     */
    public void setEditorManifestFile(String editorManifestFile) {
        this.editorManifestFile = editorManifestFile;
    }

    /**
     * @return the explicit main jar path, or {@code null} to fall back to the
     *         {@code jar} task output. The embedded jar is always named
     *         {@code <modid>.jar} inside the archive regardless.
     */
    public String getMainJar() {
        return mainJar;
    }

    /**
     * @param mainJar the explicit main jar path, or {@code null} for the default
     */
    public void setMainJar(String mainJar) {
        this.mainJar = mainJar;
    }

    /**
     * @return the directory whose contents are copied to {@code resources/}
     */
    public String getExtraResources() {
        return extraResources;
    }

    /**
     * @param extraResources the directory copied to {@code resources/}
     */
    public void setExtraResources(String extraResources) {
        this.extraResources = extraResources;
    }

    /**
     * @return the directory whose contents are copied to {@code mixins/}
     */
    public String getMixinConfigs() {
        return mixinConfigs;
    }

    /**
     * @param mixinConfigs the directory copied to {@code mixins/}
     */
    public void setMixinConfigs(String mixinConfigs) {
        this.mixinConfigs = mixinConfigs;
    }

    /**
     * @return the directory whose contents are copied to {@code lib/} (embedded deps)
     */
    public String getLibDir() {
        return libDir;
    }

    /**
     * @param libDir the directory copied to {@code lib/}
     */
    public void setLibDir(String libDir) {
        this.libDir = libDir;
    }

    /**
     * @return the output directory for assembled archives
     */
    public String getOutputDir() {
        return outputDir;
    }

    /**
     * @param outputDir the output directory for assembled archives
     */
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * @return the compatibility group tag embedded in archive metadata
     */
    public String getCompatibilityGroup() {
        return compatibilityGroup;
    }

    /**
     * @param compatibilityGroup the compatibility group tag
     */
    public void setCompatibilityGroup(String compatibilityGroup) {
        this.compatibilityGroup = compatibilityGroup;
    }

    /**
     * @return the native targets (e.g. {@code windows-x64}, {@code android-arm64})
     *         to bundle under {@code native/} in a {@code .abe}
     */
    public List<String> getNativeTargets() {
        return nativeTargets;
    }

    /**
     * @param nativeTargets the native targets to bundle
     */
    public void setNativeTargets(List<String> nativeTargets) {
        this.nativeTargets = nativeTargets;
    }

    /**
     * @return the target Minecraft edition (e.g. {@code je}, {@code be})
     */
    public String getMinecraftEdition() {
        return minecraftEdition;
    }

    /**
     * @param minecraftEdition the target Minecraft edition
     */
    public void setMinecraftEdition(String minecraftEdition) {
        this.minecraftEdition = minecraftEdition;
    }

    /**
     * @return the target Minecraft version
     */
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    /**
     * @param minecraftVersion the target Minecraft version
     */
    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }
}
