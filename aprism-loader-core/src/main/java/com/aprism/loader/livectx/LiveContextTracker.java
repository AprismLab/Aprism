package com.aprism.loader.livectx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread-safe live context state machine and listener registry
 * (v26.9-Alpha.3). This class owns no threads: it is a pure library that
 * vanilla hooks (or tests) report into, which is how the design avoids
 * polling leaks - transitions are pushed, never polled.
 *
 * <p>Fail-open policy: unexpected transitions are accepted and logged
 * (never thrown) so an unknown game flow cannot crash the host; strict
 * mode exists for tests.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LiveContextTracker {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG =
            Logger.getLogger(LiveContextTracker.class.getName());

    /** Legal forward transitions per side (SHUTDOWN is terminal). */
    private static final Map<LiveContext.State, Set<LiveContext.State>> LEGAL =
            Map.of(
                    LiveContext.State.BOOTSTRAP,
                    Set.of(LiveContext.State.MENU, LiveContext.State.IN_WORLD,
                            LiveContext.State.SHUTDOWN),
                    LiveContext.State.MENU,
                    Set.of(LiveContext.State.IN_WORLD, LiveContext.State.SHUTDOWN),
                    LiveContext.State.IN_WORLD,
                    Set.of(LiveContext.State.LEAVING, LiveContext.State.SHUTDOWN),
                    LiveContext.State.LEAVING,
                    Set.of(LiveContext.State.MENU, LiveContext.State.SHUTDOWN),
                    LiveContext.State.SHUTDOWN, Set.of());

    private final Map<LiveContext.Side, LiveContext.State> states =
            new ConcurrentHashMap<>();
    private final List<LiveContextListener> listeners =
            new CopyOnWriteArrayList<>();
    private final List<LiveContextTransition> history =
            new CopyOnWriteArrayList<>();
    private final AtomicLong droppedTransitions = new AtomicLong();
    private volatile boolean strict;

    /**
     * Registers a listener.
     *
     * @param listener the listener
     * @return true when added (false after shutdown)
     */
    public boolean addListener(LiveContextListener listener) {
        if (states.get(LiveContext.Side.CLIENT) == LiveContext.State.SHUTDOWN) {
            return false;
        }
        return listeners.add(listener);
    }

    /**
     * Removes a listener (leak prevention for hosts that re-create worlds).
     */
    public boolean removeListener(LiveContextListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Reports a transition. The first report initializes from BOOTSTRAP.
     * SHUTDOWN clears listeners after delivery (no stale references).
     *
     * @param side the reporting side
     * @param to the new state
     * @param detail diagnostics detail (may be null)
     * @return true when the transition was delivered to listeners
     */
    public boolean transition(LiveContext.Side side, LiveContext.State to,
            String detail) {
        LiveContext.State from = states.put(side, to);
        if (from == null) {
            from = LiveContext.State.BOOTSTRAP;
        }
        if (strict && !LEGAL.get(from).contains(to)) {
            droppedTransitions.incrementAndGet();
            LOG.warning("[livectx] illegal transition rejected in strict mode: "
                    + side + " " + from + " -> " + to);
            states.put(side, from);
            return false;
        }
        if (from == to) {
            droppedTransitions.incrementAndGet();
            return false;
        }
        if (from == LiveContext.State.SHUTDOWN) {
            droppedTransitions.incrementAndGet();
            states.put(side, from);
            return false;
        }
        LiveContextTransition transition = new LiveContextTransition(
                side, from, to, detail == null ? "" : detail,
                System.nanoTime());
        history.add(transition);
        while (history.size() > 32) {
            history.remove(0);
        }
        for (LiveContextListener listener : listeners) {
            try {
                listener.onTransition(transition);
            } catch (Throwable contained) {
                LOG.warning("[livectx] listener threw, contained: " + contained);
            }
        }
        if (to == LiveContext.State.SHUTDOWN) {
            listeners.clear();
        }
        return true;
    }

    /**
     * @return the current state for the side (BOOTSTRAP before first report)
     */
    public LiveContext.State state(LiveContext.Side side) {
        return states.getOrDefault(side, LiveContext.State.BOOTSTRAP);
    }

    /**
     * @return an immutable copy of the bounded transition history
     */
    public List<LiveContextTransition> history() {
        return List.copyOf(history);
    }

    /**
     * @return the number of dropped (repeated/shutdown/strict-rejected)
     *         reports
     */
    public long droppedTransitions() {
        return droppedTransitions.get();
    }

    /**
     * Enables strict mode: transitions outside the legal graph are rejected
     * (test use; production runs lenient).
     */
    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    /**
     * @return a defensive snapshot of the registered listener count
     */
    public int listenerCount() {
        return listeners.size();
    }

    /**
     * Test helper: replays the history to a fresh list.
     */
    public List<LiveContextTransition> replayTo(List<LiveContextTransition> sink) {
        sink.addAll(new ArrayList<>(history));
        return sink;
    }
}
