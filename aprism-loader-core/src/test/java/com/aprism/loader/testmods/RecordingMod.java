package com.aprism.loader.testmods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aprism.api.AprismContext;
import com.aprism.api.IAprismMod;

/**
 * Test fixture mod that records every lifecycle callback it receives. Used by
 * {@link com.aprism.loader.AprismRuntimeTest} to verify that the runtime
 * dispatches the correct phase methods in the correct order.
 *
 * <p>Instances are mutable and accumulate phase calls across the lifecycle.
 * Tests read the {@link #getPhases()} list to assert ordering.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class RecordingMod implements IAprismMod {

    private static final List<String> GLOBAL_PHASES = Collections.synchronizedList(new ArrayList<>());

    private final List<String> phases = new ArrayList<>();
    private AprismContext context;

    /**
     * Resets the global phase log. Call this at the start of each test that
     * inspects the global log.
     */
    public static void resetGlobal() {
        GLOBAL_PHASES.clear();
    }

    /**
     * @return the global phase log accumulated across all RecordingMod instances
     */
    public static List<String> getGlobalPhases() {
        return List.copyOf(GLOBAL_PHASES);
    }

    @Override
    public void onPreInitialize(AprismContext context) {
        this.context = context;
        phases.add("PREINIT");
        GLOBAL_PHASES.add("PREINIT:" + context.getMod().getId());
    }

    @Override
    public void onInitialize(AprismContext context) {
        this.context = context;
        phases.add("INIT");
        GLOBAL_PHASES.add("INIT:" + context.getMod().getId());
    }

    @Override
    public void onSetup(AprismContext context) {
        this.context = context;
        phases.add("SETUP");
        GLOBAL_PHASES.add("SETUP:" + context.getMod().getId());
    }

    @Override
    public void onComplete(AprismContext context) {
        this.context = context;
        phases.add("COMPLETE");
        GLOBAL_PHASES.add("COMPLETE:" + context.getMod().getId());
    }

    /**
     * @return the phases this instance has received, in call order
     */
    public List<String> getPhases() {
        return List.copyOf(phases);
    }

    /**
     * @return the most recently received context, or {@code null} if no phase
     *         has been invoked yet
     */
    public AprismContext getContext() {
        return context;
    }
}
