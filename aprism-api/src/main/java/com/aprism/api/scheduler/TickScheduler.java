package com.aprism.api.scheduler;

import java.util.List;

/**
 * Tick-task scheduler providing Fabric {@code ServerTickEvents}/
 * {@code ClientTickEvents} parity (v26.3-Alpha.9). Mods schedule one-shot
 * or repeating tasks per distribution; the game tick loop drives
 * {@link #runTick(TickSide, long)}, which fires every due task fail-safely
 * (a throwing task is logged and never aborts the remaining tasks or the
 * game).
 *
 * @author BlockConnect@StarsailsClover
 */
public interface TickScheduler {

    /**
     * Schedules a repeating task.
     *
     * @param side the distribution
     * @param intervalTicks the interval in ticks (must be at least 1)
     * @param handler the task handler object
     * @return the scheduled task descriptor
     */
    ScheduledTask schedule(TickSide side, long intervalTicks, Object handler);

    /**
     * Schedules a one-shot task that fires after the given delay.
     *
     * @param side the distribution
     * @param delayTicks the delay in ticks (must be at least 1)
     * @param handler the task handler object
     * @return the scheduled task descriptor
     */
    ScheduledTask scheduleOnce(TickSide side, long delayTicks, Object handler);

    /**
     * Removes a previously scheduled task.
     *
     * @param task the task descriptor returned by schedule/scheduleOnce
     * @return whether the task was found and removed
     */
    boolean unschedule(ScheduledTask task);

    /**
     * Drives the scheduler for one tick on the given side. Fires every due
     * task fail-safely; one-shot tasks are removed after firing.
     *
     * @param side the distribution
     * @param tickNumber the current tick number (0-based)
     */
    void runTick(TickSide side, long tickNumber);

    /**
     * @param side the distribution
     * @return the tasks currently scheduled on the side, in schedule order
     */
    List<ScheduledTask> scheduledTasks(TickSide side);

    /**
     * Removes all scheduled tasks on both sides. Called by the loader on
     * shutdown.
     */
    void clear();
}
