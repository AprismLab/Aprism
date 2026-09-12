package com.aprism.packaging;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Manifest baseline floor tests (v26.9-Alpha.8).
 *
 * <p>Regression anchor: the Despotes Dev report where a mod compiled against
 * Aprism v26.8 shipped {@code depends.aprism = ">=26.0-Alpha.1"}. An old
 * loader would then attempt to load a v26.8-compiled artifact even though the
 * agent's ASM/MixinExtras supply differs between generations. The packaging
 * plugin must now refuse that manifest.
 *
 * @author BlockConnect@StarsailsClover
 */
class ManifestBaselineCheckTest {

    @Test
    void oldFloorBelowBaselineIsRefused() {
        // The exact defect reported downstream.
        ManifestBaselineCheck.Result result =
                ManifestBaselineCheck.check(">=26.0-Alpha.1", "v26.8");
        assertFalse(result.ok(), "an old floor must be refused");
        assertEquals(1, result.problems().size());
        String problem = result.problems().get(0);
        assertTrue(problem.contains("26.0-Alpha.1"), problem);
        assertTrue(problem.contains("BELOW"), problem);
        assertTrue(problem.contains("26.8"), problem);
    }

    @Test
    void matchingFloorIsAccepted() {
        assertTrue(ManifestBaselineCheck.check(">=26.8", "v26.8").ok());
        assertTrue(ManifestBaselineCheck.check(">=v26.8", "v26.8").ok());
        assertTrue(ManifestBaselineCheck.check(">=26.9", "v26.8").ok(),
                "a newer floor than the baseline is allowed");
        assertTrue(ManifestBaselineCheck.check("[26.8,27)", "v26.8").ok(),
                "bracket range lower bound is honoured");
        assertTrue(ManifestBaselineCheck.check("26.8", "v26.8").ok(),
                "a bare version is treated as its own floor");
    }

    @Test
    void prereleaseOrderingIsSemantic() {
        // A release outranks its own alphas, so 26.8-Alpha.9 is below 26.8.
        assertFalse(ManifestBaselineCheck.check(">=26.8-Alpha.9", "v26.8").ok());
        assertTrue(ManifestBaselineCheck.check(">=26.8-Alpha.9", "v26.8-Alpha.8").ok());
        assertTrue(ManifestBaselineCheck.check(">=26.8-Alpha.8", "v26.8-Alpha.8").ok());
        // Higher alpha of the same version is above the baseline alpha.
        assertTrue(ManifestBaselineCheck.check(">=26.8-Alpha.9", "v26.8-Alpha.9").ok());
        assertTrue(ManifestBaselineCheck.check(">=26.8-Alpha.10", "v26.8-Alpha.9").ok());
    }

    @Test
    void missingFloorOrBaselineIsRefused() {
        assertFalse(ManifestBaselineCheck.check(null, "v26.8").ok(),
                "a manifest must declare a floor");
        assertFalse(ManifestBaselineCheck.check("   ", "v26.8").ok());
        assertFalse(ManifestBaselineCheck.check(">=26.8", null).ok(),
                "a missing baseline is a configuration error");
        assertFalse(ManifestBaselineCheck.check(">=26.8", "not-a-version").ok());
        assertFalse(ManifestBaselineCheck.check(">=nonsense", "v26.8").ok());
    }

    @Test
    void floorExtractionHandlesRangeForms() {
        assertEquals("26.8", ManifestBaselineCheck.floorOf(">=26.8"));
        assertEquals("26.8", ManifestBaselineCheck.floorOf(">26.8"));
        assertEquals("26.8", ManifestBaselineCheck.floorOf("[26.8,27)"));
        assertEquals("26.8", ManifestBaselineCheck.floorOf("[26.8,)"));
        assertEquals("26.8", ManifestBaselineCheck.floorOf("26.8"));
        assertNull(ManifestBaselineCheck.floorOf("   "));
    }
}
