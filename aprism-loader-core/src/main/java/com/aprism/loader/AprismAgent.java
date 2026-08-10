package com.aprism.loader;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Java agent entry point for the Aprism loader. When loaded as a javaagent, it
 * registers the {@link AprismClassTransformer} with the JVM and bootstraps the
 * {@link AprismRuntime}.
 *
 * <p>Agent arguments carry the version metadata required for extension and mod
 * validation, plus the game root used for production loading. The format is
 * {@code key=value;key=value;...}, e.g.
 * {@code aprismVersion=v26.0-Alpha.1;mcEdit=JE;mcVersion=26.2;gameRoot=/path/to/.minecraft}.
 * Unspecified keys default to {@code null}, which disables the corresponding
 * validation (used in tests). When {@code gameRoot} is present, the agent
 * performs the production bootstrap (two-phase load + common lifecycle).
 * Any failure is logged and swallowed so the game JVM keeps starting; a crash
 * report is written to {@code <gameRoot>/aprism-crashes/} on a best-effort
 * basis.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismAgent {

    /** Maximum number of recent structured log records embedded in a crash report. */
    private static final int MAX_LOG_TAIL = 50;

    private AprismAgent() {
    }

    /**
     * Premain entry point invoked when the agent is loaded at JVM startup via
     * {@code -javaagent}.
     *
     * @param args agent arguments (key=value pairs separated by {@code ;})
     * @param inst the instrumentation handle
     */
    public static void premain(String args, Instrumentation inst) {
        initialize(inst, args);
    }

    /**
     * Agentmain entry point invoked when the agent is attached to a running
     * JVM via the attach API.
     *
     * @param args agent arguments (key=value pairs separated by {@code ;})
     * @param inst the instrumentation handle
     */
    public static void agentmain(String args, Instrumentation inst) {
        initialize(inst, args);
    }

    /**
     * Registers the class transformer with the JVM, initializes the runtime
     * sharing that same transformer instance, and (when a {@code gameRoot}
     * argument is present) performs the production bootstrap: two-phase load
     * followed by the common lifecycle dispatch.
     *
     * <p>Everything is wrapped in a catch-all so that a broken Aprism boot
     * never terminates the game JVM. Failures are logged and a crash report
     * is written next to the game directory.
     *
     * @param inst the instrumentation handle
     * @param args the agent arguments (may be {@code null} or empty)
     */
    private static void initialize(Instrumentation inst, String args) {
        Map<String, String> kv = parseArgs(args);
        // OPEN-3 (closed in v26.0): announce the agent's presence so that
        // companion loaders (e.g. AprismPrismate) can detect a mutually
        // exclusive Aprism agent in the same JVM and refuse to boot cleanly.
        System.setProperty("aprism.agent.active", "true");
        try {
            AprismClassTransformer transformer = new AprismClassTransformer();
            inst.addTransformer(transformer, true);
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(
                    inst,
                    transformer,
                    kv.get("aprismVersion"),
                    kv.get("mcEdit"),
                    kv.get("mcVersion"));

            // Remapped profile (pre-26.1): load Fabric Intermediary mappings so
            // that mod bytecode is remapped intermediary -> official at define
            // time. The mappings file is supplied via the `mappings` agent arg.
            String mappingsArg = kv.get("mappings");
            if (mappingsArg != null && !mappingsArg.isBlank()) {
                runtime.loadIntermediaryMappings(Path.of(mappingsArg));
            }

            // Production trigger: when gameRoot is supplied, run the two-phase
            // load and the common lifecycle synchronously inside premain. The
            // optional `side` arg (client|server) additionally dispatches the
            // CLIENT or SERVER phase after the common lifecycle.
            String gameRootArg = kv.get("gameRoot");
            if (gameRootArg != null && !gameRootArg.isBlank()) {
                runtime.bootstrapProduction(Path.of(gameRootArg), kv.get("side"));
            }
        } catch (Throwable t) {
            // Never terminate the game JVM from the agent. Record the failure
            // and continue; the game launches without (or with partial) Aprism.
            java.util.logging.Logger.getLogger("AprismAgent").severe(
                    "Aprism failed to initialize: " + t);
            writeCrashReport(kv.get("gameRoot"), t);
        }
    }

    /**
     * Best-effort crash report written to {@code <gameRoot>/aprism-crashes/}
     * (or the working directory when no game root is known) so that a failed
     * Aprism boot leaves an actionable trace. Never throws.
     *
     * @param gameRootArg the game root argument (may be {@code null})
     * @param t           the failure cause
     */
    private static void writeCrashReport(String gameRootArg, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            StringBuilder report = new StringBuilder();
            report.append("Aprism Loader crash report\n")
                  .append("==========================\n\n")
                  .append("Cause\n-----\n")
                  .append(sw);
            // v26.2-Alpha.6 (goal #7 hardening): attach the recent structured
            // log tail and the mod list snapshot so a failed boot leaves an
            // actionable trace. Both reads are defensive: during a crash the
            // runtime may be only partially initialized, and the report writer
            // must never itself throw.
            appendLogTail(report);
            appendModList(report);
            Path reportPath;
            if (gameRootArg != null && !gameRootArg.isBlank()) {
                Path dir = Path.of(gameRootArg).resolve("aprism-crashes");
                Files.createDirectories(dir);
                reportPath = dir.resolve("aprism-crash-" + System.currentTimeMillis() + ".txt");
            } else {
                reportPath = Path.of("aprism-crash-" + System.currentTimeMillis() + ".txt");
            }
            Files.writeString(reportPath, report.toString());
            java.util.logging.Logger.getLogger("AprismAgent").warning(
                    "Aprism crash report written to " + reportPath);
        } catch (IOException | RuntimeException ignored) {
            // best-effort only
        }
    }

    /**
     * Appends the most recent structured log records to the report body
     * (v26.2-Alpha.6). Swallows any failure so the report writer stays
     * best-effort.
     *
     * @param report the report body under construction
     */
    private static void appendLogTail(StringBuilder report) {
        try {
            AprismRuntime runtime = AprismRuntime.instance();
            if (runtime == null || runtime.getLogging() == null) {
                return;
            }
            var retained = runtime.getLogging().getRetained();
            var records = retained.snapshot();
            if (records.isEmpty()) {
                return;
            }
            int from = Math.max(0, records.size() - MAX_LOG_TAIL);
            report.append("\nRecent log (last ")
                  .append(records.size() - from)
                  .append(")\n------------\n");
            for (int i = from; i < records.size(); i++) {
                report.append(records.get(i).render()).append('\n');
            }
        } catch (RuntimeException ignored) {
            // best-effort only
        }
    }

    /**
     * Appends the mod list snapshot to the report body (v26.2-Alpha.6).
     * Swallows any failure so the report writer stays best-effort.
     *
     * @param report the report body under construction
     */
    private static void appendModList(StringBuilder report) {
        try {
            AprismRuntime runtime = AprismRuntime.instance();
            if (runtime == null) {
                return;
            }
            var entries = runtime.getModList().getAll();
            if (entries.isEmpty()) {
                return;
            }
            report.append("\nMod list (")
                  .append(entries.size())
                  .append(")\n----------\n");
            for (var entry : entries) {
                report.append('[').append(entry.state()).append("] ")
                      .append(entry.kind()).append(' ')
                      .append(entry.id()).append(' ')
                      .append(entry.version()).append(' ')
                      .append('(').append(entry.loaderKey()).append(")\n");
            }
        } catch (RuntimeException ignored) {
            // best-effort only
        }
    }

    /**
     * Parses the agent argument string into a key/value map. Package-private
     * for direct testing.
     *
     * @param args the raw agent arguments
     * @return the parsed key/value pairs (never {@code null})
     */
    static Map<String, String> parseArgs(String args) {
        Map<String, String> kv = new HashMap<>();
        if (args == null || args.isBlank()) {
            return kv;
        }
        for (String pair : args.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            kv.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return kv;
    }
}
