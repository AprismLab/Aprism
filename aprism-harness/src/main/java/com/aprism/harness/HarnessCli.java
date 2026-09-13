package com.aprism.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MDL/Despotes conformance harness CLI (v26.9 roadmap Alpha.8).
 *
 * <pre>
 * aprism-harness agent   --jar &lt;agent.jar&gt; [--version &lt;v&gt;] [--host native|fabric|forge]
 * aprism-harness aje     --file &lt;mod.aje&gt; --baseline &lt;v26.8&gt;
 * aprism-harness leftovers --libs &lt;build/libs&gt;
 * </pre>
 *
 * Every check is fail-closed and emits a JSON verdict on stdout. Exit codes:
 * 0 all checks passed, 2 a check failed, 1 usage error.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class HarnessCli {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private HarnessCli() {
    }

    /**
     * CLI entry point.
     *
     * @param args the subcommand and its options
     * @throws Exception on IO failure
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: aprism-harness "
                    + "<agent|aje|leftovers|all> [options]");
            System.exit(1);
            return;
        }
        HarnessReport report = new HarnessReport();
        Path jsonOut = null;
        List<String> rest = new ArrayList<>();
        String command = args[0];
        for (int i = 1; i < args.length; i++) {
            if ("--json".equals(args[i]) && i + 1 < args.length) {
                jsonOut = Path.of(args[++i]);
            } else {
                rest.add(args[i]);
            }
        }
        switch (command) {
            case "agent" -> runAgentChecks(report, rest);
            case "aje" -> runAjeChecks(report, rest);
            case "leftovers" -> runLeftoverCheck(report, rest);
            case "all" -> {
                runLeftoverCheck(report, rest);
                runAgentChecks(report, rest);
                runAjeChecks(report, rest);
            }
            default -> {
                System.err.println("unknown subcommand: " + command);
                System.exit(1);
            }
        }

        System.out.println(report.toText());
        System.out.println(report.toJson());
        if (jsonOut != null) {
            Path parent = jsonOut.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(jsonOut, report.toJson());
        }
        System.exit(report.passed() ? 0 : 2);
    }

    private static void runAgentChecks(HarnessReport report, List<String> args) {
        String jar = option(args, "--jar");
        String version = option(args, "--version");
        String hostArg = option(args, "--host");
        if (jar == null) {
            report.record("agent.usage", false, "--jar is required");
            return;
        }
        try {
            AgentArtifactGuard.Artifact artifact =
                    AgentArtifactGuard.verify(Path.of(jar), version);
            report.record("agent.readable", true, artifact.path().getFileName()
                    + " (" + artifact.sizeBytes() + " bytes, sha256 "
                    + artifact.sha256().substring(0, 16) + "...)");
            report.record("agent.version", true, "version " + artifact.version());
            if (hostArg != null) {
                HostVariantSelector.Host host =
                        HostVariantSelector.parseHost(hostArg);
                List<String> problems = HostVariantSelector.verifyPairing(host,
                        artifact.path().getFileName().toString(),
                        artifact.entryNames());
                report.record("agent.hostVariant", problems.isEmpty(),
                        problems.isEmpty()
                                ? "variant matches host " + host
                                : String.join("; ", problems));
            }
        } catch (IOException | IllegalArgumentException failure) {
            report.recordFailure("agent.readable", failure);
        }
    }

    private static void runAjeChecks(HarnessReport report, List<String> args) {
        String file = option(args, "--file");
        String baseline = option(args, "--baseline");
        if (file == null) {
            report.record("aje.usage", false, "--file is required");
            return;
        }
        try {
            AjeManifestContract.ManifestFacts facts =
                    AjeManifestContract.read(Path.of(file));
            report.record("aje.manifest", true, facts.modId() + " "
                    + facts.version() + " entrypoint=" + facts.entrypoint());
            List<String> structure = AjeManifestContract.verifyStructure(
                    Path.of(file), facts);
            report.record("aje.structure", structure.isEmpty(),
                    structure.isEmpty() ? "archive contract holds"
                            : String.join("; ", structure));
            if (baseline != null) {
                List<String> floor = AjeManifestContract.verifyFloor(facts, baseline);
                report.record("aje.floor", floor.isEmpty(),
                        floor.isEmpty() ? "floor " + facts.aprismFloor()
                                + " >= baseline " + baseline
                                : String.join("; ", floor));
            }
        } catch (IOException failure) {
            report.recordFailure("aje.manifest", failure);
        }
    }

    private static void runLeftoverCheck(HarnessReport report, List<String> args) {
        String libs = option(args, "--libs");
        if (libs == null) {
            report.record("leftovers.usage", false, "--libs is required");
            return;
        }
        try {
            List<Path> leftovers = AgentArtifactGuard.findLeftovers(Path.of(libs));
            report.record("leftovers.clean", leftovers.isEmpty(),
                    leftovers.isEmpty() ? "no .tmp leftovers in " + libs
                            : "interrupted build outputs found: " + leftovers);
        } catch (IOException failure) {
            report.recordFailure("leftovers.clean", failure);
        }
    }

    private static String option(List<String> args, String name) {
        for (int i = 0; i < args.size() - 1; i++) {
            if (name.equals(args.get(i))) {
                return args.get(i + 1);
            }
        }
        return null;
    }
}
