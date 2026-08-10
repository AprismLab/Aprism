package com.aprism.api.lowlevel;

/**
 * Observes class files at load time, before verification, on the Aprism
 * class-transformation pipeline (v26.4-Alpha.3, deep bytecode-hook API).
 * Observers are fail-safe hooks: an observer that throws is logged and
 * skipped by the pipeline; it never aborts class loading or the game.
 *
 * <p>Observers see the class in its current pipeline state (after any
 * registered transformations and Mixin passes that ran earlier in the
 * pipeline) and are strictly read-only: they receive the shape, not
 * mutable bytes.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface ClassLoadObserver {

    /**
     * Invoked when a class passes through the transformation pipeline.
     *
     * @param shape the structural snapshot of the class at this pipeline
     *              point
     */
    void onClassObserved(ClassShape shape);
}
