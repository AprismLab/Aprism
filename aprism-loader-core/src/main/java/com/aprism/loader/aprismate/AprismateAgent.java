package com.aprism.loader.aprismate;

import com.aprism.api.aprismate.AprismateAgentDescriptor;
import com.aprism.api.aprismate.AprismateCapability;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reference implementation of the AprismateAgent on the loader side
 * (v26.4-Alpha.6). This is NOT the JDK-embedded agent that ships inside
 * the AprismJDK image; it is the loader-level counterpart that:
 *
 * <ol>
 *   <li>detects whether the current JVM is an AprismateAgent-capable
 *       runtime (AprismJDK) or stock OpenJDK;</li>
 *   <li>assembles the {@link AprismateAgentDescriptor} capability set
 *       (the "capability descriptor" of AprismJDK design §2);</li>
 *   <li>exposes the descriptor so the Aprism deep API can upgrade its
 *       behaviour on capable runtimes and degrade gracefully on stock
 *       JVMs.</li>
 * </ol>
 *
 * <p>Detection contract: AprismJDK sets the system property
 * {@code aprismate.jdk.version} inside its image. Its presence marks an
 * AprismateAgent-capable runtime; absence marks stock OpenJDK. This keeps
 * detection fail-safe: on any ambiguity the loader assumes stock and
 * degrades, never assumes capabilities it cannot prove.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismateAgent {

    private static final Logger LOG = Logger.getLogger("aprism.aprismate");

    /** System property set by the AprismJDK image when present. */
    public static final String APRISMATE_VERSION_PROPERTY = "aprismate.jdk.version";

    /** Capability names used across the descriptor. */
    public static final String CAP_CLASS_REDEFINITION = "class-redefinition";
    public static final String CAP_METHOD_HOOKS = "method-hooks";
    public static final String CAP_JVM_INTROSPECTION = "jvm-introspection";
    public static final String CAP_NATIVE_BRIDGE = "native-bridge";

    private final AprismateAgentDescriptor descriptor;

    /**
     * Detects the runtime and assembles the descriptor.
     *
     * @param instrumentation the agent's instrumentation handle (may be
     *                        null when no agent handle is available)
     */
    public AprismateAgent(Instrumentation instrumentation) {
        boolean onAprismJdk = System.getProperty(APRISMATE_VERSION_PROPERTY) != null;
        String runtimeName = onAprismJdk ? "AprismJDK" : "stock";
        List<AprismateCapability> capabilities = assembleCapabilities(instrumentation);
        this.descriptor = new AprismateAgentDescriptor(onAprismJdk, runtimeName, capabilities);
        LOG.info("AprismateAgent descriptor assembled: runtime=" + runtimeName
                + ", capabilities=" + descriptor.availableCapabilityNames());
    }

    /**
     * @return the assembled capability descriptor
     */
    public AprismateAgentDescriptor descriptor() {
        return descriptor;
    }

    /**
     * @return whether the current JVM is an AprismateAgent-capable runtime
     */
    public boolean isAprismJdk() {
        return descriptor.present();
    }

    /**
     * Assembles the capability set. Each capability is proven, not assumed:
     * class redefinition and method hooks require a live instrumentation
     * handle that reports the corresponding support; introspection and the
     * native bridge are loader-provided and available on any JVM, but are
     * reported with detail reflecting whether the runtime is AprismJDK
     * (where deeper seams may back them).
     *
     * @param instrumentation the agent's instrumentation handle (may be
     *                        null)
     * @return the capability list
     */
    private static List<AprismateCapability> assembleCapabilities(
            Instrumentation instrumentation) {
        List<AprismateCapability> capabilities = new ArrayList<>();

        boolean redefine = instrumentation != null && instrumentation.isRedefineClassesSupported();
        capabilities.add(new AprismateCapability(CAP_CLASS_REDEFINITION, redefine,
                redefine ? "Instrumentation.redefineClasses supported" : "no redefine support"));

        boolean retransform = instrumentation != null
                && instrumentation.isRetransformClassesSupported();
        capabilities.add(new AprismateCapability(CAP_METHOD_HOOKS, retransform,
                retransform ? "Instrumentation.retransformClasses supported"
                        : "no retransform support"));

        capabilities.add(new AprismateCapability(CAP_JVM_INTROSPECTION, true,
                "MXBean-backed JvmInsight"));

        capabilities.add(new AprismateCapability(CAP_NATIVE_BRIDGE, true,
                "loader-level NativeBridgeRegistry; FFM backend requires AprismJDK"));

        return capabilities;
    }
}
