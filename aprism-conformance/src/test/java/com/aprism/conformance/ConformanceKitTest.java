package com.aprism.conformance;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Kit-level tests: the matrix must be complete (fail-closed against the
 * required areas), every OPEN cell must carry a milestone pointer, probes
 * must attest their cells, and the JSON report must be emitted.
 *
 * @author BlockConnect@StarsailsClover
 */
class ConformanceKitTest {

    @TempDir
    Path tempDir;

    @Test
    void kitProducesCompleteMatrixAndReport() throws Exception {
        CoverageMatrix matrix = ConformanceKit.runAll();
        assertTrue(matrix.verifyCompleteness(ConformanceKit.REQUIRED_AREAS)
                .isEmpty(), "matrix completeness violations");

        Path out = tempDir.resolve("matrix.json");
        Files.writeString(out, matrix.toJson());
        String json = Files.readString(out);
        assertTrue(json.startsWith("{\"matrix\":["));
        assertTrue(json.contains("\"status\":\"VERIFIED_LIVE\""));
        assertTrue(json.contains("\"status\":\"CONTRACT_ONLY\""));
        assertTrue(json.contains("\"status\":\"OPEN\""));
    }

    @Test
    void failedProbeDowngradesVerifiedCell() {
        CoverageMatrix.Cell claimed = new CoverageMatrix.Cell("x", "y", "z",
                CoverageMatrix.Status.VERIFIED_LIVE, "evidence");
        ProbeResult failed = new ProbeResult(claimed, false, "boom");
        assertEquals(CoverageMatrix.Status.OPEN,
                failed.effectiveCell().status());
        assertTrue(failed.effectiveCell().evidence().contains("probe FAILED"));

        ProbeResult passed = new ProbeResult(claimed, true, "ok");
        assertEquals(CoverageMatrix.Status.VERIFIED_LIVE,
                passed.effectiveCell().status());
    }

    @Test
    void duplicateCellsAreRejected() {
        CoverageMatrix matrix = new CoverageMatrix();
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("a", "b", "c",
                CoverageMatrix.Status.OPEN, "milestone: v26.9-Alpha.4");
        matrix.add(cell);
        assertThrows(IllegalArgumentException.class, () -> matrix.add(cell));
    }
}
