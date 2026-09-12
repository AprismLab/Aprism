package com.aprism.conformance.probe;

import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;

    /**
     * Declares the areas whose interoperability infrastructure is scheduled
     * for later milestones (roadmap section 23): config and resource-reload
     * normalization land with the later infrastructure work and are tracked
     * in v26.10. Input, networking, registry, events, commands and the
     * settings-GUI surface now have executable probes of their own, so they
     * are deliberately NOT listed here - the matrix must not double-book an
     * area that another probe already attests. The mixin/AW area is probed
     * for infrastructure presence because it predates this line.
     *
     * @author BlockConnect@StarsailsClover
     */
    public final class DeferredAreasProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    @Override
    public ProbeResult run() {
        try {
            Class.forName("com.aprism.loader.AprismMixinBootstrap");
            Class.forName("com.aprism.loader.AprismClassTransformer");
            boolean pass = true;
            return new ProbeResult(cell(), pass,
                    "mixin/AW infrastructure classes present; "
                            + "config/reload declared OPEN for v26.10");
        } catch (Throwable t) {
            return new ProbeResult(cell(), false, t.toString());
        }
    }

    /**
     * Builds an OPEN cell whose evidence names the milestone that owns it.
     *
     * @param area the matrix area
     * @param capability the capability name
     * @param milestone the owning milestone (already version-prefixed)
     * @return the OPEN cell
     */
    private static CoverageMatrix.Cell cell(String area, String capability,
            String milestone) {
        return new CoverageMatrix.Cell(area, capability, "planned",
                CoverageMatrix.Status.OPEN, "milestone: " + milestone);
    }

    public static CoverageMatrix.Cell[] cells() {
        return new CoverageMatrix.Cell[] {
                cell("config", "config interop infrastructure", "v26.10"),
                cell("reload", "resource reload normalization", "v26.10"),
                new CoverageMatrix.Cell("mixin", "AW + transformer chain",
                        "unit+live-ref", CoverageMatrix.Status.CONTRACT_ONLY,
                        "live: mixinproof example + 26.2 mixin smoke (FACT); infra re-run by kit")
        };
    }

    private CoverageMatrix.Cell cell() {
        // Aggregate probe result cell; individual cells are added by the kit.
        return new CoverageMatrix.Cell("mixin", "AW + transformer chain",
                "unit+live-ref", CoverageMatrix.Status.CONTRACT_ONLY,
                "live: mixinproof example + 26.2 mixin smoke (FACT); infra re-run by kit");
    }
}
