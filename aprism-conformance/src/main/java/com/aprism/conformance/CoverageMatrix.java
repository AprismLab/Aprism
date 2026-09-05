package com.aprism.conformance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable capability coverage matrix (v26.9 roadmap Alpha.1).
 *
 * <p>Every cell records an area, a capability, a profile, a status, and an
 * evidence pointer. The matrix is fail-closed: a capability that is not
 * explicitly covered is reported as missing by {@link #verifyCompleteness}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class CoverageMatrix {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Evidence-backed status of one capability cell. */
    public enum Status {
        /** Live-game proof exists and is referenced by the evidence field. */
        VERIFIED_LIVE,
        /** The kit itself re-verifies the contract on every run (unit). */
        CONTRACT_ONLY,
        /** Planned capability with a named milestone; not built yet. */
        OPEN
    }

    /** One capability cell of the matrix. */
    public record Cell(String area, String capability, String profile,
            Status status, String evidence) {
    }

    private final Map<String, Cell> cells = new LinkedHashMap<>();

    /**
     * Adds a cell; duplicate area+capability+profile keys fail fast so the
     * matrix cannot silently double-book a capability.
     */
    public void add(Cell cell) {
        Cell previous = cells.put(key(cell), cell);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "duplicate matrix cell: " + key(cell));
        }
    }

    private static String key(Cell cell) {
        return cell.area() + "/" + cell.capability() + "/" + cell.profile();
    }

    /**
     * Fail-closed completeness check: every area in {@code requiredAreas}
     * must contain at least one cell, and OPEN cells must name a milestone
     * in their evidence field.
     *
     * @param requiredAreas the areas the line promises to cover
     * @return a list of violations; empty when the matrix is complete
     */
    public List<String> verifyCompleteness(List<String> requiredAreas) {
        List<String> violations = new ArrayList<>();
        for (String area : requiredAreas) {
            boolean present = cells.values().stream()
                    .anyMatch(c -> c.area().equals(area));
            if (!present) {
                violations.add("missing area: " + area);
            }
        }
        for (Cell cell : cells.values()) {
            if (cell.status() == Status.OPEN
                    && (cell.evidence() == null || !cell.evidence().startsWith("milestone:"))) {
                violations.add("OPEN cell without milestone pointer: "
                        + key(cell));
            }
        }
        return violations;
    }

    /**
     * @return the cells in insertion order
     */
    public List<Cell> cells() {
        return new ArrayList<>(cells.values());
    }

    /**
     * Emits the matrix as compact JSON (dependency-free).
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"matrix\":[");
        boolean first = true;
        for (Cell cell : cells.values()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"area\":\"").append(escape(cell.area()))
                    .append("\",\"capability\":\"").append(escape(cell.capability()))
                    .append("\",\"profile\":\"").append(escape(cell.profile()))
                    .append("\",\"status\":\"").append(cell.status())
                    .append("\",\"evidence\":\"").append(escape(cell.evidence()))
                    .append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
