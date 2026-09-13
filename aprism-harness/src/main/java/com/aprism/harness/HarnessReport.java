package com.aprism.harness;

import java.util.ArrayList;
import java.util.List;

/**
 * Machine-readable harness verdict (v26.9-Alpha.8): the JSON contract CI and
 * the conformance kit consume. Fail-closed - a check that cannot run is a
 * failure, never a silent pass.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class HarnessReport {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** One executed check. */
    public record Check(String name, boolean passed, String detail) {
    }

    private final List<Check> checks = new ArrayList<>();

    /**
     * Records a check.
     *
     * @param name the check name
     * @param passed whether it passed
     * @param detail human-readable detail (never null)
     */
    public void record(String name, boolean passed, String detail) {
        checks.add(new Check(name, passed, detail == null ? "" : detail));
    }

    /**
     * Records a check that failed because it threw.
     *
     * @param name the check name
     * @param failure the throwable
     */
    public void recordFailure(String name, Throwable failure) {
        record(name, false, failure.getClass().getSimpleName() + ": "
                + failure.getMessage());
    }

    /** @return true when every recorded check passed */
    public boolean passed() {
        return !checks.isEmpty() && checks.stream().allMatch(Check::passed);
    }

    /** @return the recorded checks */
    public List<Check> checks() {
        return List.copyOf(checks);
    }

    /**
     * @return the report as compact JSON
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{\"passed\":")
                .append(passed()).append(",\"checks\":[");
        for (int i = 0; i < checks.size(); i++) {
            Check check = checks.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"name\":\"").append(check.name())
                    .append("\",\"passed\":").append(check.passed())
                    .append(",\"detail\":\"").append(escape(check.detail()))
                    .append("\"}");
        }
        return sb.append("]}").toString();
    }

    /**
     * @return the report as a human-readable block
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Check check : checks) {
            sb.append(check.passed() ? "[PASS] " : "[FAIL] ")
                    .append(check.name()).append(": ").append(check.detail())
                    .append(System.lineSeparator());
        }
        sb.append(passed() ? "harness: PASS" : "harness: FAIL");
        return sb.toString();
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
