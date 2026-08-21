package com.aprism.loader.installer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates launcher-specific configuration files for Aprism installation
 * (v26.6-Alpha.1).
 *
 * <p>Each supported launcher has its own configuration format. This generator
 * produces the appropriate JSON or script content for the target launcher.
 *
 * <p>Example usage:
 * <pre>{@code
 * LaunchProfile profile = LaunchProfile.builder()
 *     .aprismVersion("v26.6")
 *     .mcVersion("26.2")
 *     .agentJarPath("/path/to/Aprism-v26.6-JE-26.2.jar")
 *     .gameRoot("/path/to/.minecraft")
 *     .build();
 *
 * LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.PRISM);
 * Path configFile = generator.generate(profile, instanceDir);
 * }</pre>
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LaunchProfileGenerator {

    private final LauncherType launcherType;

    /**
     * Creates a generator for the specified launcher type.
     *
     * @param launcherType the target launcher type
     */
    public LaunchProfileGenerator(LauncherType launcherType) {
        this.launcherType = Objects.requireNonNull(launcherType, "launcherType");
    }

    /**
     * Generates the launcher configuration file and writes it to the
     * specified instance directory.
     *
     * @param profile the launch profile containing Aprism configuration
     * @param instanceDir the launcher instance directory
     * @return the path to the generated configuration file
     * @throws IOException if writing fails
     * @throws IllegalArgumentException if the launcher type does not support
     *         file-based configuration (e.g. GENERIC)
     */
    public Path generate(LaunchProfile profile, Path instanceDir) throws IOException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(instanceDir, "instanceDir");

        if (launcherType == LauncherType.GENERIC) {
            throw new IllegalArgumentException(
                    "GENERIC launcher type does not support file-based configuration; "
                    + "use generateScript() instead");
        }

        String configFileName = launcherType.instanceConfigFile();
        if (configFileName == null) {
            throw new IllegalArgumentException(
                    "Launcher type " + launcherType + " does not have an instance config file");
        }

        Path configFile = instanceDir.resolve(configFileName);
        String content = generateContent(profile);
        Files.writeString(configFile, content);
        return configFile;
    }

    /**
     * Generates a standalone launch script (batch or shell) for the GENERIC
     * launcher type.
     *
     * @param profile the launch profile
     * @param outputDir the directory to write the script
     * @param windows true for Windows batch (.bat), false for shell (.sh)
     * @return the path to the generated script
     * @throws IOException if writing fails
     */
    public Path generateScript(LaunchProfile profile, Path outputDir, boolean windows)
            throws IOException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(outputDir, "outputDir");

        String extension = windows ? ".bat" : ".sh";
        String scriptName = "launch-aprism" + extension;
        Path scriptFile = outputDir.resolve(scriptName);

        String content = windows
                ? generateBatchScript(profile)
                : generateShellScript(profile);
        Files.writeString(scriptFile, content);

        if (!windows) {
            // Set executable permission on Unix-like systems
            scriptFile.toFile().setExecutable(true, false);
        }

        return scriptFile;
    }

    /**
     * Generates the configuration content as a string without writing to disk.
     *
     * @param profile the launch profile
     * @return the configuration content in the launcher's format
     */
    public String generateContent(LaunchProfile profile) {
        return switch (launcherType) {
            case PRISM -> generatePrismConfig(profile);
            case ATLAUNCHER -> generateAtLauncherConfig(profile);
            case GD_LAUNCHER -> generateGdLauncherConfig(profile);
            case GENERIC -> throw new IllegalStateException(
                    "GENERIC launcher type does not support content generation");
        };
    }

    private String generatePrismConfig(LaunchProfile profile) {
        // Prism Launcher uses instance.cfg (INI-style) and mmc-pack.json
        // For javaagent injection, we modify the pre-launch command or
        // use the libraries array in the instance's patches.
        // This is a simplified version; real Prism config is more complex.
        StringBuilder sb = new StringBuilder();
        sb.append("# Prism Launcher instance configuration\n");
        sb.append("# Generated by Aprism Installer v").append(profile.aprismVersion()).append("\n");
        sb.append("InstanceType=OneSix\n");
        sb.append("MinecraftVersion=").append(profile.mcVersion()).append("\n");
        sb.append("PreLaunchCommand=");
        sb.append("java -javaagent:").append(profile.agentJarPath());
        sb.append("=aprismVersion=").append(profile.aprismVersion());
        sb.append(";mcEdit=JE");
        sb.append(";mcVersion=").append(profile.mcVersion());
        if (profile.gameRoot() != null) {
            sb.append(";gameRoot=").append(profile.gameRoot());
        }
        sb.append("\n");
        for (String arg : profile.additionalJvmArgs()) {
            sb.append("JvmArgs=").append(arg).append("\n");
        }
        return sb.toString();
    }

    private String generateAtLauncherConfig(LaunchProfile profile) {
        // ATLauncher uses JSON format
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", "Aprism Instance");
        config.put("minecraftVersion", profile.mcVersion());
        config.put("aprismVersion", profile.aprismVersion());

        List<Map<String, Object>> libraries = new ArrayList<>();
        Map<String, Object> aprismLib = new LinkedHashMap<>();
        aprismLib.put("name", "Aprism");
        aprismLib.put("version", profile.aprismVersion());
        aprismLib.put("file", Path.of(profile.agentJarPath()).getFileName().toString());
        aprismLib.put("type", "javaagent");
        libraries.add(aprismLib);
        config.put("libraries", libraries);

        List<String> jvmArgs = new ArrayList<>(profile.additionalJvmArgs());
        if (!jvmArgs.isEmpty()) {
            config.put("javaArgs", String.join(" ", jvmArgs));
        }

        return toJsonString(config, 0);
    }

    private String generateGdLauncherConfig(LaunchProfile profile) {
        // GDLauncher uses a simpler JSON format
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", "Aprism Instance");
        config.put("minecraftVersion", profile.mcVersion());
        config.put("modpackVersion", profile.aprismVersion());

        List<String> jvmArgs = new ArrayList<>(profile.additionalJvmArgs());
        jvmArgs.add(0, profile.javaagentArg());
        config.put("customJavaArgs", jvmArgs);

        return toJsonString(config, 0);
    }

    private String generateBatchScript(LaunchProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append("rem Aprism Launcher Script\r\n");
        sb.append("rem Generated by Aprism Installer v").append(profile.aprismVersion()).append("\r\n");
        sb.append("rem \r\n");
        sb.append("rem Minecraft Version: ").append(profile.mcVersion()).append("\r\n");
        sb.append("rem Aprism Version: ").append(profile.aprismVersion()).append("\r\n");
        sb.append("\r\n");
        sb.append("set JAVA_ARGS=").append(profile.javaagentArg()).append("\r\n");
        for (String arg : profile.additionalJvmArgs()) {
            sb.append("set JAVA_ARGS=%JAVA_ARGS% ").append(arg).append("\r\n");
        }
        sb.append("\r\n");
        sb.append("echo Starting Minecraft with Aprism...\r\n");
        sb.append("echo Java Arguments: %JAVA_ARGS%\r\n");
        sb.append("\r\n");
        sb.append("rem Replace the following with your actual Minecraft launch command\r\n");
        sb.append("rem java %JAVA_ARGS% -jar minecraft.jar\r\n");
        sb.append("echo Please configure the actual Minecraft launch command in this script.\r\n");
        sb.append("pause\r\n");
        return sb.toString();
    }

    private String generateShellScript(LaunchProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/bash\n");
        sb.append("# Aprism Launcher Script\n");
        sb.append("# Generated by Aprism Installer v").append(profile.aprismVersion()).append("\n");
        sb.append("#\n");
        sb.append("# Minecraft Version: ").append(profile.mcVersion()).append("\n");
        sb.append("# Aprism Version: ").append(profile.aprismVersion()).append("\n");
        sb.append("\n");
        sb.append("JAVA_ARGS=\"").append(profile.javaagentArg()).append("\"");
        for (String arg : profile.additionalJvmArgs()) {
            sb.append(" \\\n  \"").append(arg).append("\"");
        }
        sb.append("\n");
        sb.append("\n");
        sb.append("echo \"Starting Minecraft with Aprism...\"\n");
        sb.append("echo \"Java Arguments: $JAVA_ARGS\"\n");
        sb.append("\n");
        sb.append("# Replace the following with your actual Minecraft launch command\n");
        sb.append("# java $JAVA_ARGS -jar minecraft.jar\n");
        sb.append("echo \"Please configure the actual Minecraft launch command in this script.\"\n");
        return sb.toString();
    }

    /**
     * Simple JSON serializer for configuration maps. Produces readable,
     * indented JSON without external dependencies.
     */
    private String toJsonString(Map<String, Object> map, int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = "  ".repeat(indent);
        String innerIndent = "  ".repeat(indent + 1);
        sb.append(indentStr).append("{\n");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sb.append(innerIndent).append("\"").append(entry.getKey()).append("\": ");
            Object value = entry.getValue();
            if (value instanceof String s) {
                sb.append("\"").append(escapeJson(s)).append("\"");
            } else if (value instanceof Number n) {
                sb.append(n);
            } else if (value instanceof Boolean b) {
                sb.append(b);
            } else if (value instanceof List<?> list) {
                sb.append("[\n");
                for (int j = 0; j < list.size(); j++) {
                    Object item = list.get(j);
                    if (item instanceof Map<?, ?> itemMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedMap = (Map<String, Object>) itemMap;
                        sb.append(toJsonString(typedMap, indent + 2));
                    } else if (item instanceof String s) {
                        sb.append(innerIndent).append("  \"").append(escapeJson(s)).append("\"");
                    } else {
                        sb.append(innerIndent).append("  ").append(item);
                    }
                    if (j < list.size() - 1) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append(innerIndent).append("]");
            } else if (value instanceof Map<?, ?> nestedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedMap = (Map<String, Object>) nestedMap;
                sb.append("\n").append(toJsonString(typedMap, indent + 1));
            } else {
                sb.append("null");
            }
            if (i < map.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            i++;
        }
        sb.append(indentStr).append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
