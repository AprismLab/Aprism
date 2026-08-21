package com.aprism.loader.installer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link LauncherDetector}.
 */
class LauncherDetectorTest {

    @TempDir
    Path tempDir;

    private final LauncherDetector detector = new LauncherDetector();

    @Test
    void detectsPrismLauncher() throws IOException {
        Files.createFile(tempDir.resolve("instance.cfg"));
        Files.createFile(tempDir.resolve("mmc-pack.json"));

        assertEquals(LauncherType.PRISM, detector.detect(tempDir));
    }

    @Test
    void detectsAtLauncher() throws IOException {
        Files.createFile(tempDir.resolve("instance.json"));
        Files.createFile(tempDir.resolve("minecraft.json"));

        assertEquals(LauncherType.ATLAUNCHER, detector.detect(tempDir));
    }

    @Test
    void detectsGdLauncher() throws IOException {
        String config = "{\"modpackVersion\": \"1.0.0\", \"customJavaArgs\": []}";
        Files.writeString(tempDir.resolve("config.json"), config);

        assertEquals(LauncherType.GD_LAUNCHER, detector.detect(tempDir));
    }

    @Test
    void returnsGenericForUnknownStructure() throws IOException {
        Files.createFile(tempDir.resolve("some-random-file.txt"));

        assertEquals(LauncherType.GENERIC, detector.detect(tempDir));
    }

    @Test
    void returnsGenericForNonExistentDirectory() throws IOException {
        Path nonExistent = tempDir.resolve("does-not-exist");
        assertEquals(LauncherType.GENERIC, detector.detect(nonExistent));
    }

    @Test
    void detectsMostCommonFromParent() throws IOException {
        // Create 2 Prism instances
        Path prism1 = tempDir.resolve("prism1");
        Files.createDirectory(prism1);
        Files.createFile(prism1.resolve("instance.cfg"));
        Files.createFile(prism1.resolve("mmc-pack.json"));

        Path prism2 = tempDir.resolve("prism2");
        Files.createDirectory(prism2);
        Files.createFile(prism2.resolve("instance.cfg"));
        Files.createFile(prism2.resolve("mmc-pack.json"));

        // Create 1 ATLauncher instance
        Path atlauncher = tempDir.resolve("atlauncher");
        Files.createDirectory(atlauncher);
        Files.createFile(atlauncher.resolve("instance.json"));
        Files.createFile(atlauncher.resolve("minecraft.json"));

        assertEquals(LauncherType.PRISM, detector.detectFromParent(tempDir));
    }

    @Test
    void returnsGenericFromEmptyParent() throws IOException {
        assertEquals(LauncherType.GENERIC, detector.detectFromParent(tempDir));
    }
}
