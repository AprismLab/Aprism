package com.aprism.conformance.probe;

import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;

/**
 * Declares the areas whose interoperability infrastructure is scheduled
 * for later v26.9 milestones (roadmap section 23, Alpha.4-Alpha.7).
 * Fail-closed honesty: these cells must exist and be OPEN, never silently
 * absent. The mixin/AW area is additionally probed for infrastructure
 * presence (transformer chain classes) because it predates this line.
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
                            + "input/networking/config/reload declared OPEN");
        } catch (Throwable t) {
            return new ProbeResult(cell(), false, t.toString());
        }
    }

    private static CoverageMatrix.Cell cell(String area, String capability,
            String milestone) {
        return new CoverageMatrix.Cell(area, capability, "planned",
                CoverageMatrix.Status.OPEN, "milestone: v26.9-" + milestone);
    }

    public static CoverageMatrix.Cell[] cells() {
        return new CoverageMatrix.Cell[] {
                cell("input", "key mapping surface", "Alpha.7"),
                cell("networking", "handshake + payload transport", "Alpha.6"),
                cell("config", "config interop infrastructure", "Alpha.4"),
                cell("reload", "resource reload normalization", "Alpha.5"),
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
