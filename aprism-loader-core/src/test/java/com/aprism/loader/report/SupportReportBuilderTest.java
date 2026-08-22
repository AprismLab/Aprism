package com.aprism.loader.report;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SupportReportBuilder}.
 */
class SupportReportBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildProducesHeaderAndEnvironment() {
        String report = SupportReportBuilder.build("v26.6-Alpha.4", "JE", "26.2");

        assertTrue(report.contains("Aprism Loader support report"));
        assertTrue(report.contains("v26.6-Alpha.4"));
        assertTrue(report.contains("JE 26.2"));
        assertTrue(report.contains("Environment"));
        assertTrue(report.contains("java.vm.name".replace("java.vm.name",
                System.getProperty("java.vm.name"))));
    }

    @Test
    void buildWithNullIdentityFieldsIsStable() {
        String report = SupportReportBuilder.build(null, null, null);
        assertNotNull(report);
        assertTrue(report.contains("Aprism Loader support report"));
        // Null fields render as empty strings, never the word null.
        assertFalse(report.contains("null"));
    }

    @Test
    void noLoadReportSectionWhenRuntimeUnloaded() {
        // In a plain test JVM the runtime singleton is not initialized.
        String report = SupportReportBuilder.build("v26.6", "JE", "26.2");
        assertTrue(report.contains("No load report available"));
    }

    @Test
    void writeProducesFileInGameRoot() {
        Path written = SupportReportBuilder.write(tempDir, "v26.6", "JE", "26.2");
        assertNotNull(written);
        assertEquals("aprism-report.txt", written.getFileName().toString());
        assertTrue(Files.exists(written));
    }

    @Test
    void writeWithNullGameRootFallsBackToCwd() {
        // Must not throw; may write into the process working directory.
        assertDoesNotThrow(() -> {
            Path written = SupportReportBuilder.write(null, "v26.6", "JE", "26.2");
            if (written != null) {
                Files.deleteIfExists(written);
            }
        });
    }

    @Test
    void hintForDependencyFailures() {
        assertTrue(SupportReportBuilder.hintFor("missing dependency: fabric-api")
                .contains("dependency"));
        assertTrue(SupportReportBuilder.hintFor("circular dependency detected")
                .toLowerCase().contains("depend"));
        assertNotNull(SupportReportBuilder.hintFor("version range not satisfied"));
        assertNotNull(SupportReportBuilder.hintFor("manifest missing"));
        assertNotNull(SupportReportBuilder.hintFor("ClassNotFoundException: com.example.Foo"));
        assertNotNull(SupportReportBuilder.hintFor("duplicate id registered"));
    }

    @Test
    void hintForUnknownFailureIsNull() {
        assertNull(SupportReportBuilder.hintFor(null));
        assertNull(SupportReportBuilder.hintFor("something entirely unusual happened"));
    }

    @Test
    void mutualExclusionWarningWhenBothActive() {
        System.setProperty("aprism.agent.active", "true");
        System.setProperty("prismate.active", "true");
        try {
            String report = SupportReportBuilder.build("v26.6", "JE", "26.2");
            assertTrue(report.contains("MUTUAL EXCLUSION WARNING"));
            assertTrue(report.contains("unsupported"));
        } finally {
            System.clearProperty("aprism.agent.active");
            System.clearProperty("prismate.active");
        }
    }

    @Test
    void noMutualExclusionWarningWhenOnlyAgentActive() {
        System.setProperty("aprism.agent.active", "true");
        try {
            String report = SupportReportBuilder.build("v26.6", "JE", "26.2");
            assertFalse(report.contains("MUTUAL EXCLUSION WARNING"));
        } finally {
            System.clearProperty("aprism.agent.active");
        }
    }
}
