package com.aprism.loader.installer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link LaunchProfileGenerator}.
 */
class LaunchProfileGeneratorTest {

    @TempDir
    Path tempDir;

    private LaunchProfile createTestProfile() {
        return LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath("/path/to/Aprism-v26.6-JE-26.2.jar")
                .gameRoot("/path/to/.minecraft")
                .build();
    }

    @Test
    void generatesPrismConfig() {
        LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.PRISM);
        LaunchProfile profile = createTestProfile();

        String content = generator.generateContent(profile);

        assertTrue(content.contains("Prism Launcher instance configuration"));
        assertTrue(content.contains("MinecraftVersion=26.2"));
        assertTrue(content.contains("PreLaunchCommand="));
        assertTrue(content.contains("-javaagent:"));
        assertTrue(content.contains("aprismVersion=v26.6"));
    }

    @Test
    void generatesAtLauncherConfig() {
        LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.ATLAUNCHER);
        LaunchProfile profile = createTestProfile();

        String content = generator.generateContent(profile);

        assertTrue(content.contains("\"minecraftVersion\": \"26.2\""));
        assertTrue(content.contains("\"aprismVersion\": \"v26.6\""));
        assertTrue(content.contains("\"type\": \"javaagent\""));
    }

    @Test
    void generatesGdLauncherConfig() {
        LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.GD_LAUNCHER);
        LaunchProfile profile = createTestProfile();

        String content = generator.generateContent(profile);

        assertTrue(content.contains("\"minecraftVersion\": \"26.2\""));
        assertTrue(content.contains("\"customJavaArgs\""));
        assertTrue(content.contains("-javaagent:"));
    }

    @Test
    void genericThrowsOnGenerateContent() {
        LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.GENERIC);
        LaunchProfile profile = createTestProfile();

        assertThrows(IllegalStateException.class, () -> generator.generateContent(profile));
    }

    @Test
    void genericThrowsOnGenerate() {
        LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.GENERIC);
        LaunchProfile profile = createTestProfile();

        assertThrows(IllegalArgumentException.class, () -> generator.generate(profile, tempDir));
    }

    @Test
    void generatesBatchScript() throws IOException {
        LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.GENERIC);
        LaunchProfile profile = createTestProfile();

        Path script = generator.generateScript(profile, tempDir, true);

        assertTrue(Files.exists(script));
        assertEquals("launch-aprism.bat", script.getFileName().toString());

        String content = Files.readString(script);
        assertTrue(content.contains("@echo off"));
        assertTrue(content.contains("Aprism Launcher Script"));
        assertTrue(content.contains("-javaagent:"));
        assertTrue(content.contains("pause"));
    }

    @Test
    void generatesShellScript() throws IOException {
        LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.GENERIC);
        LaunchProfile profile = createTestProfile();

        Path script = generator.generateScript(profile, tempDir, false);

        assertTrue(Files.exists(script));
        assertEquals("launch-aprism.sh", script.getFileName().toString());

        String content = Files.readString(script);
        assertTrue(content.contains("#!/bin/bash"));
        assertTrue(content.contains("Aprism Launcher Script"));
        assertTrue(content.contains("-javaagent:"));

        // Check executable permission
        assertTrue(script.toFile().canExecute());
    }

    @Test
    void writesPrismConfigToFile() throws IOException {
        LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.PRISM);
        LaunchProfile profile = createTestProfile();

        Path configFile = generator.generate(profile, tempDir);

        assertTrue(Files.exists(configFile));
        assertEquals("instance.cfg", configFile.getFileName().toString());

        String content = Files.readString(configFile);
        assertTrue(content.contains("Prism Launcher"));
    }

    @Test
    void includesAdditionalJvmArgs() {
        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath("/path/to/Aprism.jar")
                .additionalJvmArgs(List.of("-Xmx4G", "-Xms2G"))
                .build();

        LaunchProfileGenerator generator = new LaunchProfileGenerator(LauncherType.PRISM);
        String content = generator.generateContent(profile);

        assertTrue(content.contains("-Xmx4G"));
        assertTrue(content.contains("-Xms2G"));
    }
}
