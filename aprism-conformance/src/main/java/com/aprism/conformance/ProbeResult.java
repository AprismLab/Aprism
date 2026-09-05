package com.aprism.conformance;

/**
 * One executable conformance probe result: the matrix cell it attests plus
 * whether the probe itself passed. A failed probe downgrades the cell in
 * the emitted report (fail-closed: a probe that does not pass can never
 * attest VERIFIED_LIVE or CONTRACT_ONLY).
 *
 * @author BlockConnect@StarsailsClover
 */
public record ProbeResult(CoverageMatrix.Cell cell, boolean passed, String detail) {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * @return the cell with its status downgraded to OPEN when the probe
     *         failed and the cell claimed a verified status
     */
    public CoverageMatrix.Cell effectiveCell() {
        if (passed) {
            return cell;
        }
        if (cell.status() == CoverageMatrix.Status.OPEN) {
            return cell;
        }
        return new CoverageMatrix.Cell(cell.area(), cell.capability(),
                cell.profile(), CoverageMatrix.Status.OPEN,
                "probe FAILED, downgraded: " + detail);
    }
}
