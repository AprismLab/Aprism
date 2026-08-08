package com.aprism.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Startup load report for a single Aprism boot (Alpha 4 production
 * hardening). Collects timing and the outcome of every unit loaded during
 * the two-phase boot, distinguishing successful loads from isolated failures
 * so the launcher (and the user) can see exactly what worked and what did
 * not.
 *
 * <p>A mod or extension failure is isolated: it is recorded here and in the
 * log, but does not abort the rest of the boot. Dependency-resolution
 * failures remain fatal (they abort the load) because they indicate an
 * inconsistent mod set rather than a single broken mod.
 *
 * <p>The report is rendered by {@link #toSummary(String)} into a plain-text
 * block suitable for the game log and for the crash-report directory.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LoadReport {

    /** A single loaded unit (extension or mod) with its outcome. */
    public record Entry(String kind, String id, String version, Status status, long durationMs, String failure) {
        /** Load outcome. */
        public enum Status { OK, FAILED }
    }

    private final long startNanos = System.nanoTime();
    private long phase1StartNanos;
    private long phase1EndNanos;
    private long phase2StartNanos;
    private long phase2EndNanos;
    private final List<Entry> entries = new ArrayList<>();

    /** Marks the start of phase 1 (extension scan). */
    public void beginPhase1() {
        phase1StartNanos = System.nanoTime();
    }

    /** Marks the end of phase 1. */
    public void endPhase1() {
        phase1EndNanos = System.nanoTime();
    }

    /** Marks the start of phase 2 (mod scan). */
    public void beginPhase2() {
        phase2StartNanos = System.nanoTime();
    }

    /** Marks the end of phase 2. */
    public void endPhase2() {
        phase2EndNanos = System.nanoTime();
    }

    /**
     * Records a successfully loaded unit.
     *
     * @param kind       {@code "extension"} or {@code "mod"}
     * @param id         the unit id
     * @param version    the unit version (may be {@code null})
     * @param durationMs the time taken to load this unit, in milliseconds
     */
    public void recordOk(String kind, String id, String version, long durationMs) {
        entries.add(new Entry(kind, id, version, Entry.Status.OK, durationMs, null));
    }

    /**
     * Records an isolated load failure.
     *
     * @param kind       {@code "extension"} or {@code "mod"}
     * @param id         the unit id
     * @param version    the unit version (may be {@code null})
     * @param durationMs the time spent before the failure, in milliseconds
     * @param failure    a short human-readable failure description
     */
    public void recordFailure(String kind, String id, String version, long durationMs, String failure) {
        entries.add(new Entry(kind, id, version, Entry.Status.FAILED, durationMs, failure));
    }

    /** @return all recorded entries, in load order */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** @return the number of successfully loaded units */
    public long okCount() {
        return entries.stream().filter(e -> e.status() == Entry.Status.OK).count();
    }

    /** @return the number of failed units */
    public long failureCount() {
        return entries.stream().filter(e -> e.status() == Entry.Status.FAILED).count();
    }

    /** @return the failed entries only */
    public List<Entry> failures() {
        return entries.stream().filter(e -> e.status() == Entry.Status.FAILED).toList();
    }

    /** @return total boot time so far, in milliseconds */
    public long totalMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private long ms(long from, long to) {
        if (from == 0 || to == 0) {
            return 0;
        }
        return (to - from) / 1_000_000;
    }

    /**
     * Renders the report as a plain-text summary block.
     *
     * @param aprismVersion the running Aprism version, for the header
     * @return the summary text (multiple lines, no trailing newline guarantee)
     */
    public String toSummary(String aprismVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append("==== Aprism Load Report (").append(aprismVersion).append(") ====\n");
        sb.append("Phase 1 (extensions): ").append(ms(phase1StartNanos, phase1EndNanos)).append(" ms\n");
        sb.append("Phase 2 (mods):       ").append(ms(phase2StartNanos, phase2EndNanos)).append(" ms\n");
        sb.append("Total boot:           ").append(totalMs()).append(" ms\n");
        sb.append("Loaded ").append(okCount()).append(", failed ").append(failureCount()).append('\n');
        for (Entry e : entries) {
            sb.append("  [").append(e.status() == Entry.Status.OK ? "OK  " : "FAIL")
                    .append("] ").append(e.kind()).append(' ').append(e.id());
            if (e.version() != null && !e.version().isBlank()) {
                sb.append(' ').append(e.version());
            }
            sb.append(" (").append(e.durationMs()).append(" ms)");
            if (e.failure() != null) {
                sb.append(" -> ").append(e.failure());
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
