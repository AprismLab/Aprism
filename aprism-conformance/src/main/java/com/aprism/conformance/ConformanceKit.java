package com.aprism.conformance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aprism.conformance.probe.CommandsProbe;
import com.aprism.conformance.probe.DeferredAreasProbe;
import com.aprism.conformance.probe.EventsProbe;
import com.aprism.conformance.probe.LifecycleProbe;
import com.aprism.conformance.probe.LiveContextProbe;
import com.aprism.conformance.probe.RegistryProbe;

/**
 * Executable conformance kit entry point (v26.9-Alpha.1). Runs every
 * probe, applies fail-closed status downgrades, verifies matrix
 * completeness, and writes the JSON report.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ConformanceKit {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Areas the v26.9 line promises to cover (roadmap section 23). */
    public static final List<String> REQUIRED_AREAS = List.of(
            "lifecycle", "registry", "events", "commands", "input",
            "networking", "config", "reload", "mixin");

    private ConformanceKit() {
    }

    /**
     * Runs all probes and returns the effective (downgraded) matrix.
     *
     * @return the matrix after fail-closed downgrades
     */
    public static CoverageMatrix runAll() {
        CoverageMatrix matrix = new CoverageMatrix();
        List<Probe> probes = List.of(new LifecycleProbe(), new LiveContextProbe(),
                new RegistryProbe(),
                new EventsProbe(), new CommandsProbe(), new DeferredAreasProbe());
        for (Probe probe : probes) {
            ProbeResult result = probe.run();
            for (CoverageMatrix.Cell cell : cellsFor(probe, result)) {
                matrix.add(cell);
            }
            System.out.println((result.passed() ? "[PASS] " : "[FAIL] ")
                    + result.cell().area() + "/" + result.cell().capability()
                    + ": " + result.detail());
        }
        return matrix;
    }

    private static List<CoverageMatrix.Cell> cellsFor(Probe probe,
            ProbeResult result) {
        if (probe instanceof DeferredAreasProbe) {
            List<CoverageMatrix.Cell> cells = new java.util.ArrayList<>(
                    List.of(DeferredAreasProbe.cells()));
            cells.removeIf(c -> c.area().equals("mixin"));
            cells.add(result.effectiveCell());
            return cells;
        }
        return List.of(result.effectiveCell());
    }

    /**
     * CLI entry: writes the report to the path given by --out (or the
     * default build/conformance/matrix.json) and exits non-zero when the
     * matrix is incomplete or any probe failed.
     *
     * @param args command line arguments
     * @throws Exception on IO failure
     */
    public static void main(String[] args) throws Exception {
        Path out = Path.of("build", "conformance", "matrix.json");
        for (int i = 0; i < args.length - 1; i++) {
            if ("--out".equals(args[i])) {
                out = Path.of(args[i + 1]);
            }
        }
        CoverageMatrix matrix = runAll();
        List<String> violations = matrix.verifyCompleteness(REQUIRED_AREAS);
        Files.createDirectories(out.getParent());
        Files.writeString(out, matrix.toJson());
        System.out.println("matrix written to " + out
                + " (" + matrix.cells().size() + " cells)");
        if (!violations.isEmpty()) {
            violations.forEach(v -> System.out.println("[INCOMPLETE] " + v));
            System.exit(2);
        }
    }
}
