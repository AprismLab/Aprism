package com.aprism.loader.installer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link InstallationValidator}.
 */
class InstallationValidatorTest {

    @TempDir
    Path tempDir;

    private final InstallationValidator validator = new InstallationValidator();

    @Test
    void validInstallationPasses() throws IOException {
        Path agentJar = tempDir.resolve("Aprism.jar");
        Files.writeString(agentJar, "fake jar content that is long enough");

        Path gameRoot = tempDir.resolve("minecraft");
        Files.createDirectory(gameRoot);
        Files.createDirectory(gameRoot.resolve("mods"));

        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath(agentJar.toString())
                .gameRoot(gameRoot.toString())
                .build();

        InstallationValidator.ValidationResult result = validator.validate(profile);
        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void missingAgentJarIsError() {
        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath("/nonexistent/Aprism.jar")
                .build();

        InstallationValidator.ValidationResult result = validator.validate(profile);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not found")));
    }

    @Test
    void missingGameRootIsError() throws IOException {
        Path agentJar = tempDir.resolve("Aprism.jar");
        Files.writeString(agentJar, "fake jar content");

        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath(agentJar.toString())
                .gameRoot("/nonexistent/game/root")
                .build();

        InstallationValidator.ValidationResult result = validator.validate(profile);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Game root")));
    }

    @Test
    void missingModsDirIsWarning() throws IOException {
        Path agentJar = tempDir.resolve("Aprism.jar");
        Files.writeString(agentJar, "fake jar content that is long enough");

        Path gameRoot = tempDir.resolve("minecraft");
        Files.createDirectory(gameRoot);
        // No mods directory

        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath(agentJar.toString())
                .gameRoot(gameRoot.toString())
                .build();

        InstallationValidator.ValidationResult result = validator.validate(profile);
        assertTrue(result.isValid()); // Still valid, just a warning
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Mods directory")));
    }

    @Test
    void smallAgentJarIsWarning() throws IOException {
        Path agentJar = tempDir.resolve("Aprism.jar");
        Files.writeString(agentJar, "tiny"); // Very small file

        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath(agentJar.toString())
                .build();

        InstallationValidator.ValidationResult result = validator.validate(profile);
        assertTrue(result.isValid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("small")));
    }

    @Test
    void nonStandardVersionFormatIsWarning() throws IOException {
        Path agentJar = tempDir.resolve("Aprism.jar");
        Files.writeString(agentJar, "fake jar content that is long enough");

        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("weird-version")
                .mcVersion("26.2")
                .agentJarPath(agentJar.toString())
                .build();

        InstallationValidator.ValidationResult result = validator.validate(profile);
        assertTrue(result.isValid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("non-standard")));
    }

    @Test
    void validateAndReportProducesReadableOutput() throws IOException {
        Path agentJar = tempDir.resolve("Aprism.jar");
        Files.writeString(agentJar, "fake jar content that is long enough");

        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath(agentJar.toString())
                .build();

        String report = validator.validateAndReport(profile);
        assertTrue(report.contains("Aprism Installation Validation Report"));
        assertTrue(report.contains("VALID"));
        assertTrue(report.contains("v26.6"));
    }
}
