package com.aprism.loader.reginterop;

/**
 * Registry freeze-timing diagnostics (v26.9 roadmap Alpha.4): classifies
 * WHERE in the vanilla registry lifecycle a binding attempt landed, from
 * the failure signatures vanilla itself produces - provider-neutral and
 * version-tolerant.
 *
 * <p>The classification is intentionally evidence-based: no mutation, no
 * probing of vanilla internals, just the outcome a real bind attempt
 * already produced.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class FreezeDiagnostics {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Where in the vanilla lifecycle a binding attempt landed. */
    public enum FreezePhase {
        /** Vanilla registries not bootstrapped yet (too early). */
        PRE_BOOTSTRAP,
        /** Registries live and still writable - the bind window. */
        BOOTSTRAP_WINDOW,
        /** Registries already frozen (too late; entry refused by vanilla). */
        POST_FREEZE,
        /** Outcome could not be attributed to a known vanilla signature. */
        UNKNOWN
    }

    /** The classified outcome of one binding attempt. */
    public record PhaseReport(FreezePhase phase, String signature,
            boolean bindWindowOpen) {

        /**
         * @return the report as compact JSON
         */
        public String toJson() {
            return "{\"phase\":\"" + phase + "\",\"signature\":\""
                    + (signature == null ? "" : signature.replace("\"", "'"))
                    + "\",\"bindWindowOpen\":" + bindWindowOpen + "}";
        }
    }

    private FreezeDiagnostics() {
    }

    /**
     * Classifies a successful attempt: the bind window was open.
     */
    public static PhaseReport success() {
        return new PhaseReport(FreezePhase.BOOTSTRAP_WINDOW, "bind ok", true);
    }

    /**
     * Classifies a failure thrown by a binding attempt against vanilla.
     * Signatures are matched on lowercase message content so different
     * mappings/versions keep classifying correctly.
     *
     * @param failure the throwable from the attempt
     * @return the phase report
     */
    public static PhaseReport classify(Throwable failure) {
        String message = failure == null ? "" : String.valueOf(
                failure.getMessage()).toLowerCase(java.util.Locale.ROOT);
        String chain = message;
        Throwable cause = failure == null ? null : failure.getCause();
        while (cause != null) {
            chain += " " + String.valueOf(cause.getMessage()).toLowerCase(
                    java.util.Locale.ROOT);
            cause = cause.getCause();
        }
        if (chain.contains("frozen") || chain.contains("registry is already")
                || chain.contains("locked")) {
            return new PhaseReport(FreezePhase.POST_FREEZE, message, false);
        }
        if (chain.contains("not bootstrapped") || chain.contains("bootstrap")) {
            return new PhaseReport(FreezePhase.PRE_BOOTSTRAP, message, false);
        }
        return new PhaseReport(FreezePhase.UNKNOWN, message, false);
    }
}
