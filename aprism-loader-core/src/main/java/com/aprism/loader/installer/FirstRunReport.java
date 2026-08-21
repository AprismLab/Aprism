package com.aprism.loader.installer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Generates a first-run report for Aprism installation (v26.6-Alpha.1).
 *
 * <p>The report provides users with:
 * <ul>
 *   <li>Installation summary</li>
 *   <li>Detected launcher type</li>
 *   <li>Validation results</li>
 *   <li>Next steps and troubleshooting tips</li>
 * </ul>
 *
 * @author BlockConnect@StarsailsClover
 */
public final class FirstRunReport {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Generates a first-run report for the given installation.
     *
     * @param profile the launch profile
     * @param launcherType the detected launcher type
     * @param validationResult the validation result
     * @return the formatted report
     */
    public String generate(LaunchProfile profile, LauncherType launcherType,
            InstallationValidator.ValidationResult validationResult) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(launcherType, "launcherType");
        Objects.requireNonNull(validationResult, "validationResult");

        StringBuilder sb = new StringBuilder();
        sb.append("╔═══════════════════════════════════════════════════════════════╗\n");
        sb.append("║              Aprism Loader - First Run Report                 ║\n");
        sb.append("╚═══════════════════════════════════════════════════════════════╝\n\n");

        sb.append("Generated: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n\n");

        // Installation summary
        sb.append("Installation Summary\n");
        sb.append("--------------------\n");
        sb.append("Aprism Version:    ").append(profile.aprismVersion()).append("\n");
        sb.append("Minecraft Version: ").append(profile.mcVersion()).append("\n");
        sb.append("Launcher Type:     ").append(launcherType.displayName()).append("\n");
        sb.append("Agent Jar:         ").append(profile.agentJarPath()).append("\n");
        if (profile.gameRoot() != null) {
            sb.append("Game Root:         ").append(profile.gameRoot()).append("\n");
        }
        sb.append("\n");

        // Validation status
        sb.append("Validation Status\n");
        sb.append("-----------------\n");
        if (validationResult.isValid()) {
            sb.append("Status: ✓ VALID\n\n");
        } else {
            sb.append("Status: ✗ INVALID\n\n");
        }

        // Errors
        if (!validationResult.errors().isEmpty()) {
            sb.append("Errors:\n");
            for (String error : validationResult.errors()) {
                sb.append("  ✗ ").append(error).append("\n");
            }
            sb.append("\n");
        }

        // Warnings
        if (!validationResult.warnings().isEmpty()) {
            sb.append("Warnings:\n");
            for (String warning : validationResult.warnings()) {
                sb.append("  ! ").append(warning).append("\n");
            }
            sb.append("\n");
        }

        // Next steps
        sb.append("Next Steps\n");
        sb.append("----------\n");
        if (validationResult.isValid()) {
            sb.append("1. Launch Minecraft using your launcher.\n");
            sb.append("2. Check the game log for 'Aprism loaded' message.\n");
            sb.append("3. Place .aje mods in <game-root>/mods/ directory.\n");
            sb.append("4. Place .aep extensions in <game-root>/aprism-extensions/ (optional).\n");
        } else {
            sb.append("1. Fix the errors listed above.\n");
            sb.append("2. Re-run the installer or manually verify the installation.\n");
            sb.append("3. Check the Aprism documentation for troubleshooting.\n");
        }
        sb.append("\n");

        // Launcher-specific notes
        sb.append("Launcher Notes\n");
        sb.append("--------------\n");
        switch (launcherType) {
            case PRISM -> {
                sb.append("Prism Launcher detected. The installer has configured the instance\n");
                sb.append("to use Aprism as a javaagent. You can verify this in the instance\n");
                sb.append("settings under 'Pre-launch commands' or 'Java arguments'.\n");
            }
            case ATLAUNCHER -> {
                sb.append("ATLauncher detected. The installer has added Aprism to the instance's\n");
                sb.append("libraries with type 'javaagent'. Check the instance's 'Loader' tab.\n");
            }
            case GD_LAUNCHER -> {
                sb.append("GDLauncher detected. The installer has added the javaagent argument\n");
                sb.append("to the instance's custom Java arguments.\n");
            }
            case GENERIC -> {
                sb.append("No specific launcher detected. The installer has generated a launch\n");
                sb.append("script. Edit the script to include your actual Minecraft launch command.\n");
            }
        }
        sb.append("\n");

        // Support information
        sb.append("Support\n");
        sb.append("-------\n");
        sb.append("Documentation: https://github.com/AprismLab/Aprism/wiki\n");
        sb.append("Issues:        https://github.com/AprismLab/Aprism/issues\n");
        sb.append("Discussions:   https://github.com/AprismLab/Aprism/discussions\n");
        sb.append("\n");

        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("                    End of First Run Report                    \n");
        sb.append("═══════════════════════════════════════════════════════════════\n");

        return sb.toString();
    }
}
