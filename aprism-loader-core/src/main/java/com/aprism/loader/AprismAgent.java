package com.aprism.loader;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

/**
 * Java agent entry point for the Aprism loader. When loaded as a javaagent, it
 * registers the {@link AprismClassTransformer} with the JVM and bootstraps the
 * {@link AprismRuntime}.
 *
 * <p>Agent arguments carry the version metadata required for extension and mod
 * validation. The format is {@code key=value;key=value;...}, e.g.
 * {@code aprismVersion=26.0-Alpha.1;mcEdit=JE;mcVersion=1.21.4}. Unspecified
 * keys default to {@code null}, which disables the corresponding validation
 * (used in tests).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismAgent {

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
     * Registers the class transformer and initializes the runtime with the
     * version metadata parsed from the agent arguments.
     *
     * @param inst the instrumentation handle
     * @param args the agent arguments (may be {@code null} or empty)
     */
    private static void initialize(Instrumentation inst, String args) {
        Map<String, String> kv = parseArgs(args);
        AprismClassTransformer transformer = new AprismClassTransformer();
        inst.addTransformer(transformer, true);
        AprismRuntime.instance().initialize(
                inst,
                kv.get("aprismVersion"),
                kv.get("mcEdit"),
                kv.get("mcVersion"));
    }

    /**
     * Parses the agent argument string into a key/value map.
     *
     * @param args the raw argument string
     * @return the parsed key/value pairs (never {@code null})
     */
    private static Map<String, String> parseArgs(String args) {
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
