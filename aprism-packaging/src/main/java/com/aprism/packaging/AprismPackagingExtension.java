package com.aprism.packaging;

import java.util.ArrayList;
import java.util.List;

/**
 * Gradle extension for configuring Aprism packaging. Exposed under the
 * {@code aprismPackaging} block in a build script.
 *
 * @author BlockConnect@StarsailsClover
 */
public class AprismPackagingExtension {

    private String manifestFile = "aprism.manifest.json";
    private List<String> includePlatforms = new ArrayList<>();
    private List<String> nativeTargets = new ArrayList<>();
    private String outputDir = "build/aprism";
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
     * @return the platform ids (e.g. {@code fabric}, {@code neoforge}) to include
     */
    public List<String> getIncludePlatforms() {
        return includePlatforms;
    }

    /**
     * @param includePlatforms the platform ids to include
     */
    public void setIncludePlatforms(List<String> includePlatforms) {
        this.includePlatforms = includePlatforms;
    }

    /**
     * @return the native targets (e.g. {@code windows-x64}, {@code linux-x64}) to bundle
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
