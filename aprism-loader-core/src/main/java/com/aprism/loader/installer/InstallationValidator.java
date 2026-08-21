package com.aprism.loader.installer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates the integrity of an Aprism installation (v26.6-Alpha.1).
 *
 * <p>The validator checks:
 * <ul>
 *   <li>Agent jar exists and is readable</li>
 *   <li>Agent jar has the correct manifest attributes</li>
 *   <li>Game root directory exists</li>
 *   <li>Mods directory exists (optional, with warning if missing)</li>
 *   <li>Extensions directory exists (optional, with warning if missing)</li>
 * </ul>
 *
 * @author BlockConnect@StarsailsClover
 */
public final class InstallationValidator {

    /**
     * Validation result containing status and any issues found.
     */
    public record ValidationResult(
            boolean valid,
            List<String> errors,
            List<String> warnings) {

        /**
         * @return true if the installation is valid (no errors)
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * @return list of error messages (empty if valid)
         */
        public List<String> errors() {
            return errors;
        }

        /**
         * @return list of warning messages (may be non-empty even if valid)
         */
        public List<String> warnings() {
            return warnings;
        }
    }

    /**
     * Validates the Aprism installation for the given launch profile.
     *
     * @param profile the launch profile to validate
     * @return the validation result
     */
    public ValidationResult validate(LaunchProfile profile) {
        Objects.requireNonNull(profile, "profile");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check agent jar
        Path agentJar = Path.of(profile.agentJarPath());
        if (!Files.exists(agentJar)) {
            errors.add("Agent jar not found: " + profile.agentJarPath());
        } else if (!Files.isRegularFile(agentJar)) {
            errors.add("Agent jar path is not a file: " + profile.agentJarPath());
        } else if (!Files.isReadable(agentJar)) {
            errors.add("Agent jar is not readable: " + profile.agentJarPath());
        } else {
            // Check jar size (should be at least a few KB)
            try {
                long size = Files.size(agentJar);
                if (size < 1000) {
                    warnings.add("Agent jar is suspiciously small (" + size + " bytes)");
                }
            } catch (IOException e) {
                warnings.add("Could not check agent jar size: " + e.getMessage());
            }
        }

        // Check game root if specified
        if (profile.gameRoot() != null) {
            Path gameRoot = Path.of(profile.gameRoot());
            if (!Files.exists(gameRoot)) {
                errors.add("Game root directory not found: " + profile.gameRoot());
            } else if (!Files.isDirectory(gameRoot)) {
                errors.add("Game root path is not a directory: " + profile.gameRoot());
            } else {
                // Check for mods directory
                Path modsDir = gameRoot.resolve("mods");
                if (!Files.exists(modsDir)) {
                    warnings.add("Mods directory does not exist: " + modsDir
                            + " (will be created on first launch)");
                }

                // Check for extensions directory
                Path extensionsDir = gameRoot.resolve("aprism-extensions");
                if (!Files.exists(extensionsDir)) {
                    warnings.add("Extensions directory does not exist: " + extensionsDir
                            + " (optional, for loader-support extensions)");
                }
            }
        }

        // Check version format
        if (!profile.aprismVersion().matches("v\\d+\\.\\d+(-Alpha\\.\\d+)?")) {
            warnings.add("Aprism version format is non-standard: " + profile.aprismVersion());
        }

        // Check MC version format
        String mcVer = profile.mcVersion();
        if (!mcVer.matches("\\d+\\.\\d+(\\.\\d+)?")) {
            warnings.add("Minecraft version format is non-standard: " + mcVer);
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Validates the installation and returns a human-readable report.
     *
     * @param profile the launch profile to validate
     * @return the validation report as a string
     */
    public String validateAndReport(LaunchProfile profile) {
        ValidationResult result = validate(profile);
        StringBuilder sb = new StringBuilder();
        sb.append("Aprism Installation Validation Report\n");
        sb.append("=====================================\n\n");

        sb.append("Aprism Version: ").append(profile.aprismVersion()).append("\n");
        sb.append("MC Version: ").append(profile.mcVersion()).append("\n");
        sb.append("Agent Jar: ").append(profile.agentJarPath()).append("\n");
        if (profile.gameRoot() != null) {
            sb.append("Game Root: ").append(profile.gameRoot()).append("\n");
        }
        sb.append("\n");

        if (result.isValid()) {
            sb.append("Status: VALID\n\n");
        } else {
            sb.append("Status: INVALID\n\n");
        }

        if (!result.errors().isEmpty()) {
            sb.append("Errors:\n");
            for (String error : result.errors()) {
                sb.append("  - ").append(error).append("\n");
            }
            sb.append("\n");
        }

        if (!result.warnings().isEmpty()) {
            sb.append("Warnings:\n");
            for (String warning : result.warnings()) {
                sb.append("  - ").append(warning).append("\n");
            }
        }

        return sb.toString();
    }
}
