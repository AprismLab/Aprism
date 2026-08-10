package com.aprism.api.scheduler;

/**
 * A scheduled tick task (Fabric {@code ServerTickEvents} parity,
 * v26.3-Alpha.9). A task carries a handler object owned by the scheduling
 * mod, an interval in ticks, and a repeat flag; the scheduler fires the
 * handler on every matching tick and removes one-shot tasks after their
 * first fire.
 *
 * <p>The handler is intentionally untyped ({@code Object}): the game side
 * knows how to invoke it, keeping the loader-level scheduler independent
 * of any tick-loop API.
 *
 * @param side the distribution the task runs on
 * @param intervalTicks the interval in ticks (1 = every tick)
 * @param repeating whether the task repeats after firing
 * @param handler the task handler object
 * @author BlockConnect@StarsailsClover
 */
public record ScheduledTask(TickSide side, long intervalTicks, boolean repeating, Object handler) {

    /**
     * Canonical compact constructor: validates interval and handler.
     */
    public ScheduledTask {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("intervalTicks must be >= 1");
        }
        if (handler == null) {
            throw new IllegalArgumentException("task handler must be non-null");
        }
        if (side == null) {
            throw new IllegalArgumentException("tick side must be non-null");
        }
    }
}
