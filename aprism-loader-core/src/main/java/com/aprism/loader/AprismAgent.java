package com.aprism.loader;

import java.lang.instrument.Instrumentation;

/**
 * Java agent entry point for the Aprism loader. When loaded as a javaagent, it
 * registers the {@link AprismClassTransformer} with the JVM and bootstraps the
 * {@link AprismRuntime}.
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
     * @param args agent arguments (currently ignored)
     * @param inst the instrumentation handle
     */
    public static void premain(String args, Instrumentation inst) {
        initialize(inst);
    }

    /**
     * Agentmain entry point invoked when the agent is attached to a running
     * JVM via the attach API.
     *
     * @param args agent arguments (currently ignored)
     * @param inst the instrumentation handle
     */
    public static void agentmain(String args, Instrumentation inst) {
        initialize(inst);
    }

    /**
     * Registers the class transformer and initializes the runtime.
     *
     * @param inst the instrumentation handle
     */
    private static void initialize(Instrumentation inst) {
        AprismClassTransformer transformer = new AprismClassTransformer();
        inst.addTransformer(transformer, true);
        AprismRuntime.instance().initialize(inst);
    }
}
