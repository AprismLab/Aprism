package com.aprism.loader.installer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link LaunchProfile}.
 */
class LaunchProfileTest {

    @Test
    void builderCreatesValidProfile() {
        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath("/path/to/Aprism-v26.6-JE-26.2.jar")
                .gameRoot("/path/to/.minecraft")
                .build();

        assertEquals("v26.6", profile.aprismVersion());
        assertEquals("26.2", profile.mcVersion());
        assertEquals("/path/to/Aprism-v26.6-JE-26.2.jar", profile.agentJarPath());
        assertEquals("/path/to/.minecraft", profile.gameRoot());
        assertTrue(profile.additionalJvmArgs().isEmpty());
    }

    @Test
    void javaagentArgIncludesAllParameters() {
        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath("/path/to/Aprism.jar")
                .gameRoot("/game")
                .build();

        String arg = profile.javaagentArg();
        assertTrue(arg.startsWith("-javaagent:/path/to/Aprism.jar="));
        assertTrue(arg.contains("aprismVersion=v26.6"));
        assertTrue(arg.contains("mcEdit=JE"));
        assertTrue(arg.contains("mcVersion=26.2"));
        assertTrue(arg.contains("gameRoot=/game"));
    }

    @Test
    void javaagentArgOmitsGameRootWhenNull() {
        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath("/path/to/Aprism.jar")
                .build();

        String arg = profile.javaagentArg();
        assertFalse(arg.contains("gameRoot"));
    }

    @Test
    void builderRejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () ->
                LaunchProfile.builder()
                        .mcVersion("26.2")
                        .agentJarPath("/path.jar")
                        .build());

        assertThrows(NullPointerException.class, () ->
                LaunchProfile.builder()
                        .aprismVersion("v26.6")
                        .agentJarPath("/path.jar")
                        .build());

        assertThrows(NullPointerException.class, () ->
                LaunchProfile.builder()
                        .aprismVersion("v26.6")
                        .mcVersion("26.2")
                        .build());
    }

    @Test
    void additionalJvmArgsAreImmutable() {
        LaunchProfile profile = LaunchProfile.builder()
                .aprismVersion("v26.6")
                .mcVersion("26.2")
                .agentJarPath("/path.jar")
                .additionalJvmArgs(java.util.List.of("-Xmx4G"))
                .build();

        assertEquals(1, profile.additionalJvmArgs().size());
        assertThrows(UnsupportedOperationException.class, () ->
                profile.additionalJvmArgs().add("-Xms2G"));
    }
}
