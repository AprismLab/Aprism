package com.aprism.conformance;

/**
 * An executable conformance probe. Each probe exercises one capability
 * contract against the real runtime classes (never mocks of Aprism itself)
 * and returns the matrix cell it attests.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * Runs the probe. Implementations must never throw for an expected
     * failure: capture the failure into the ProbeResult instead so one
     * broken capability cannot break the whole report.
     *
     * @return the probe result
     */
    ProbeResult run();
}
