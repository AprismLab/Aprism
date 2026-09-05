package com.aprism.loader.livectx;

/**
 * One observed context transition. Immutable and timestamped so binder
 * triggers and diagnostics can reason about ordering without shared
 * mutable state.
 *
 * @param side the reporting side
 * @param from the previous state (null for the initial report)
 * @param to the new state
 * @param detail free-form diagnostics detail (may be empty)
 * @param timestampNanos {@link System#nanoTime} at the observation
 * @author BlockConnect@StarsailsClover
 */
public record LiveContextTransition(LiveContext.Side side,
        LiveContext.State from, LiveContext.State to, String detail,
        long timestampNanos) {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover
}
