package com.aprism.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

/**
 * JUnit 5 tests for {@link AprismPhase}.
 *
 * @author BlockConnect@StarsailsClover
 */
class AprismPhaseTest {

    @Test
    void allPhasesHaveDescriptions() {
        for (AprismPhase phase : EnumSet.allOf(AprismPhase.class)) {
            assertNotNull(phase.getDescription(), "Phase " + phase + " is missing a description");
        }
    }

    @Test
    void expectedPhasesPresent() {
        assertEquals(6, EnumSet.allOf(AprismPhase.class).size());
        assertEquals(AprismPhase.PREINIT, AprismPhase.valueOf("PREINIT"));
        assertEquals(AprismPhase.INIT, AprismPhase.valueOf("INIT"));
        assertEquals(AprismPhase.SETUP, AprismPhase.valueOf("SETUP"));
        assertEquals(AprismPhase.COMPLETE, AprismPhase.valueOf("COMPLETE"));
        assertEquals(AprismPhase.CLIENT, AprismPhase.valueOf("CLIENT"));
        assertEquals(AprismPhase.SERVER, AprismPhase.valueOf("SERVER"));
    }
}
