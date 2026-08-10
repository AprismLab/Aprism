package com.aprism.loader.scheduler;

import com.aprism.api.scheduler.ScheduledTask;
import com.aprism.api.scheduler.TickScheduler;
import com.aprism.api.scheduler.TickSide;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe implementation of {@link TickScheduler} (v26.3-Alpha.9,
 * Fabric ServerTickEvents/ClientTickEvents parity). Tasks are stored per
 * distribution with a next-fire tick number; {@link #runTick} fires every
 * due task fail-safely — a throwing handler is logged and never aborts the
 * remaining tasks — and one-shot tasks are removed after firing.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class TickSchedulerImpl implements TickScheduler {

    private static final Logger LOG = Logger.getLogger("aprism.scheduler");

    /**
     * A scheduled task together with the tick number on which it fires next.
     */
    private record Entry(ScheduledTask task, long nextFireTick) {
    }

    private final Map<TickSide, List<Entry>> tasks = new ConcurrentHashMap<>();

    @Override
    public ScheduledTask schedule(TickSide side, long intervalTicks, Object handler) {
        ScheduledTask task = new ScheduledTask(side, intervalTicks, true, handler);
        add(task, intervalTicks);
        return task;
    }

    @Override
    public ScheduledTask scheduleOnce(TickSide side, long delayTicks, Object handler) {
        ScheduledTask task = new ScheduledTask(side, delayTicks, false, handler);
        add(task, delayTicks);
        return task;
    }

    private void add(ScheduledTask task, long delay) {
        tasks.computeIfAbsent(task.side(), k -> new CopyOnWriteArrayList<>())
                .add(new Entry(task, delay));
    }

    @Override
    public boolean unschedule(ScheduledTask task) {
        List<Entry> bucket = tasks.get(task.side());
        if (bucket == null) {
            return false;
        }
        return bucket.removeIf(entry -> entry.task() == task);
    }

    @Override
    public void runTick(TickSide side, long tickNumber) {
        List<Entry> bucket = tasks.get(side);
        if (bucket == null) {
            return;
        }
        List<Entry> due = new ArrayList<>();
        for (Entry entry : bucket) {
            if (tickNumber >= entry.nextFireTick()) {
                due.add(entry);
            }
        }
        for (Entry entry : due) {
            fire(entry.task());
            if (entry.task().repeating()) {
                long interval = entry.task().intervalTicks();
                bucket.remove(entry);
                bucket.add(new Entry(entry.task(), tickNumber + interval));
            } else {
                bucket.remove(entry);
            }
        }
    }

    private void fire(ScheduledTask task) {
        try {
            if (task.handler() instanceof Runnable runnable) {
                runnable.run();
            }
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "scheduled task failed on " + task.side(), failure);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks(TickSide side) {
        List<Entry> bucket = tasks.get(side);
        if (bucket == null) {
            return List.of();
        }
        List<ScheduledTask> snapshot = new ArrayList<>();
        for (Entry entry : bucket) {
            snapshot.add(entry.task());
        }
        return List.copyOf(snapshot);
    }

    @Override
    public void clear() {
        tasks.clear();
    }
}
