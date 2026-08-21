package com.aprism.loader.installer;

import java.util.List;
import java.util.Objects;

/**
 * Immutable launch profile describing how Aprism should be attached to a
 * Minecraft instance (v26.6-Alpha.1).
 *
 * <p>The profile captures the essential information needed to generate
 * launcher-specific configuration: the Aprism agent jar path, the target
 * Minecraft version, and any additional JVM arguments.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LaunchProfile {

    private final String aprismVersion;
    private final String mcVersion;
    private final String agentJarPath;
    private final String gameRoot;
    private final List<String> additionalJvmArgs;

    private LaunchProfile(Builder builder) {
        this.aprismVersion = Objects.requireNonNull(builder.aprismVersion, "aprismVersion");
        this.mcVersion = Objects.requireNonNull(builder.mcVersion, "mcVersion");
        this.agentJarPath = Objects.requireNonNull(builder.agentJarPath, "agentJarPath");
        this.gameRoot = builder.gameRoot;
        this.additionalJvmArgs = builder.additionalJvmArgs != null
                ? List.copyOf(builder.additionalJvmArgs)
                : List.of();
    }

    /**
     * @return the Aprism version string (e.g. "v26.6")
     */
    public String aprismVersion() {
        return aprismVersion;
    }

    /**
     * @return the target Minecraft version (e.g. "26.2", "1.21.4")
     */
    public String mcVersion() {
        return mcVersion;
    }

    /**
     * @return absolute path to the Aprism agent jar
     */
    public String agentJarPath() {
        return agentJarPath;
    }

    /**
     * @return the game root directory (e.g. ".minecraft"), or null if not set
     */
    public String gameRoot() {
        return gameRoot;
    }

    /**
     * @return additional JVM arguments to include in the launch configuration
     */
    public List<String> additionalJvmArgs() {
        return additionalJvmArgs;
    }

    /**
     * Builds the javaagent argument string for this profile.
     *
     * @return the full {@code -javaagent:...} argument with key=value parameters
     */
    public String javaagentArg() {
        StringBuilder sb = new StringBuilder("-javaagent:");
        sb.append(agentJarPath);
        sb.append("=aprismVersion=").append(aprismVersion);
        sb.append(";mcEdit=JE");
        sb.append(";mcVersion=").append(mcVersion);
        if (gameRoot != null) {
            sb.append(";gameRoot=").append(gameRoot);
        }
        return sb.toString();
    }

    /**
     * @return a new builder for constructing LaunchProfile instances
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link LaunchProfile}.
     */
    public static final class Builder {
        private String aprismVersion;
        private String mcVersion;
        private String agentJarPath;
        private String gameRoot;
        private List<String> additionalJvmArgs;

        private Builder() {}

        public Builder aprismVersion(String aprismVersion) {
            this.aprismVersion = aprismVersion;
            return this;
        }

        public Builder mcVersion(String mcVersion) {
            this.mcVersion = mcVersion;
            return this;
        }

        public Builder agentJarPath(String agentJarPath) {
            this.agentJarPath = agentJarPath;
            return this;
        }

        public Builder gameRoot(String gameRoot) {
            this.gameRoot = gameRoot;
            return this;
        }

        public Builder additionalJvmArgs(List<String> additionalJvmArgs) {
            this.additionalJvmArgs = additionalJvmArgs;
            return this;
        }

        public LaunchProfile build() {
            return new LaunchProfile(this);
        }
    }
}
