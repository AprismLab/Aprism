package com.aprism.loader.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import com.aprism.loader.AprismRuntime;
import com.aprism.loader.LoadReport;
import com.aprism.loader.logging.AprismLogRecord;

/**
 * Assembles the {@code aprism-report} support bundle (v26.6-Alpha.4).
 *
 * <p>The report is the single artifact a user attaches to a bug report. It
 * combines everything support needs in one readable text file:
 * <ul>
 *   <li>Environment identity (Aprism version, MC edition/version, JVM, OS)</li>
 *   <li>The startup load summary (per-unit outcomes and timing)</li>
 *   <li>Per-failure details with actionable hints</li>
 *   <li>The mutual-exclusion warning when both the Aprism agent and
 *       AprismPrismate are active in one instance</li>
 *   <li>The recent structured-log tail</li>
 *   <li>The mod list snapshot</li>
 * </ul>
 *
 * <p>All assembly and IO is fail-safe: any failure is logged and swallowed so
 * report generation never breaks the game.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class SupportReportBuilder {

    private static final Logger LOG = Logger.getLogger("aprism.report");

    /** Maximum structured-log records embedded in the report. */
    private static final int MAX_LOG_TAIL = 100;

    private SupportReportBuilder() {
    }

    /**
     * Builds the report body from the current runtime state.
     *
     * @param aprismVersion the running Aprism version (may be null)
     * @param mcEdit        the target edition (may be null)
     * @param mcVersion     the target Minecraft version (may be null)
     * @return the full report text (never null)
     */
    public static String build(String aprismVersion, String mcEdit, String mcVersion) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, aprismVersion, mcEdit, mcVersion);
        appendEnvironment(sb);
        appendLoadOutcome(sb);
        appendMutualExclusion(sb);
        appendLogTail(sb);
        appendModList(sb);
        return sb.toString();
    }

    /**
     * Builds and writes the report to {@code <gameRoot>/aprism-report.txt}.
     * Fail-safe: returns null instead of throwing when writing fails or the
     * game root is absent.
     *
     * @param gameRoot      the game instance root (may be null)
     * @param aprismVersion the running Aprism version (may be null)
     * @param mcEdit        the target edition (may be null)
     * @param mcVersion     the target Minecraft version (may be null)
     * @return the written path, or null when unavailable
     */
    public static Path write(Path gameRoot, String aprismVersion, String mcEdit, String mcVersion) {
        try {
            Path target = (gameRoot != null ? gameRoot : Path.of("."))
                    .resolve("aprism-report.txt");
            Files.writeString(target, build(aprismVersion, mcEdit, mcVersion));
            return target;
        } catch (IOException | RuntimeException e) {
            LOG.warning("SupportReportBuilder: failed to write report: " + e.getMessage());
            return null;
        }
    }

    private static void appendHeader(StringBuilder sb, String aprismVersion,
            String mcEdit, String mcVersion) {
        sb.append("Aprism Loader support report\n")
          .append("===========================\n")
          .append("Generated: ").append(Instant.now()).append('\n')
          .append("Aprism:    ").append(nvl(aprismVersion)).append('\n')
          .append("Target:    ").append(nvl(mcEdit)).append(' ')
          .append(nvl(mcVersion)).append("\n\n");
    }

    private static void appendEnvironment(StringBuilder sb) {
        Runtime rt = Runtime.getRuntime();
        sb.append("Environment\n")
          .append("-----------\n")
          .append("JVM:       ").append(System.getProperty("java.vm.name")).append(' ')
          .append(System.getProperty("java.version")).append('\n')
          .append("Vendor:    ").append(System.getProperty("java.vendor")).append('\n')
          .append("OS:        ").append(System.getProperty("os.name")).append(' ')
          .append(System.getProperty("os.version")).append(" (")
          .append(System.getProperty("os.arch")).append(")\n")
          .append("Cores:     ").append(rt.availableProcessors()).append('\n')
          .append("Max heap:  ").append(rt.maxMemory() / (1024 * 1024)).append(" MB\n\n");
    }

    private static void appendLoadOutcome(StringBuilder sb) {
        try {
            AprismRuntime runtime = AprismRuntime.instance();
            LoadReport report = runtime == null ? null : runtime.getLoadReport();
            if (report == null) {
                sb.append("Load outcome\n")
                  .append("------------\n")
                  .append("No load report available (runtime not loaded or already shut down).\n\n");
                return;
            }
            sb.append("Load outcome\n")
              .append("------------\n")
              .append(report.toSummary(aprismVersionOf(runtime)))
              .append('\n');
            appendFailureHints(sb, report.failures());
        } catch (RuntimeException ignored) {
            // best-effort only
        }
    }

    private static void appendFailureHints(StringBuilder sb, List<LoadReport.Entry> failures) {
        if (failures.isEmpty()) {
            return;
        }
        sb.append("Failure details\n")
          .append("---------------\n");
        for (LoadReport.Entry failure : failures) {
            sb.append('-').append(' ')
              .append(failure.kind()).append(" '").append(failure.id())
              .append("' (").append(nvl(failure.version())).append("): ")
              .append(nvl(failure.failure())).append('\n');
            String hint = hintFor(failure.failure());
            if (hint != null) {
                sb.append("  Hint: ").append(hint).append('\n');
            }
        }
        sb.append('\n');
    }

    /**
     * Maps common failure signatures to actionable first-step hints. Returns
     * null when no known pattern matches.
     */
    static String hintFor(String failure) {
        if (failure == null) {
            return null;
        }
        String lower = failure.toLowerCase();
        if (lower.contains("dependenc") || lower.contains("missing dep")) {
            return "A required mod is missing or its version range is unsatisfied. "
                    + "Install the dependency or align versions.";
        }
        if (lower.contains("circular") || lower.contains("cycle")) {
            return "Two or more mods depend on each other. Remove one dependency edge "
                    + "(reported to the mod authors).";
        }
        if (lower.contains("version") && lower.contains("range")) {
            return "The extension/mod does not support the running Aprism or Minecraft "
                    + "version. Update the unit or Aprism.";
        }
        if (lower.contains("manifest")) {
            return "The archive manifest is missing or malformed. Re-export the pack "
                    + "(see the developer guide, Doc 08).";
        }
        if (lower.contains("classnotfound") || lower.contains("noclassdef")) {
            return "A class could not be loaded. The pack may target a different "
                    + "Minecraft version; verify the pack's mcVersion range.";
        }
        if (lower.contains("duplicate")) {
            return "The id is already registered. Remove one of the duplicate packs.";
        }
        return null;
    }

    private static void appendMutualExclusion(StringBuilder sb) {
        // OPEN-7 convention: companion loaders announce themselves via system
        // properties. The agent sets aprism.agent.active; Prismate-compatible
        // bridges are expected to set prismate.active. When both are present
        // the instance state is unsupported and must be surfaced loudly.
        boolean agentActive = Boolean.parseBoolean(System.getProperty("aprism.agent.active", "false"));
        boolean prismateActive = Boolean.parseBoolean(
                System.getProperty("prismate.active",
                        System.getProperty("aprism.prismate.active", "false")));
        if (agentActive && prismateActive) {
            sb.append("MUTUAL EXCLUSION WARNING\n")
              .append("------------------------\n")
              .append("Both the Aprism javaagent and an AprismPrismate bridge are active\n")
              .append("in this instance. This combination is unsupported: Prismate aborts\n")
              .append("when it detects the agent; remove one of the two installations.\n\n");
        }
    }

    private static void appendLogTail(StringBuilder sb) {
        try {
            AprismRuntime runtime = AprismRuntime.instance();
            if (runtime == null || runtime.getLogging() == null) {
                return;
            }
            List<AprismLogRecord> records = runtime.getLogging().getRetained().snapshot();
            if (records.isEmpty()) {
                return;
            }
            int from = Math.max(0, records.size() - MAX_LOG_TAIL);
            sb.append("Recent log (last ").append(records.size() - from).append(")\n")
              .append("------------\n");
            for (int i = from; i < records.size(); i++) {
                sb.append(records.get(i).render()).append('\n');
            }
            sb.append('\n');
        } catch (RuntimeException ignored) {
            // best-effort only
        }
    }

    private static void appendModList(StringBuilder sb) {
        try {
            AprismRuntime runtime = AprismRuntime.instance();
            if (runtime == null) {
                return;
            }
            var entries = runtime.getModList().getAll();
            if (entries.isEmpty()) {
                return;
            }
            sb.append("Mod list (").append(entries.size()).append(")\n")
              .append("----------\n");
            for (var entry : entries) {
                sb.append('[').append(entry.state()).append("] ")
                  .append(entry.kind()).append(' ')
                  .append(entry.id()).append(' ')
                  .append(entry.version()).append(' ')
                  .append('(').append(entry.loaderKey()).append(")\n");
            }
        } catch (RuntimeException ignored) {
            // best-effort only
        }
    }

    private static String aprismVersionOf(AprismRuntime runtime) {
        try {
            return runtime.getAprismVersion();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
