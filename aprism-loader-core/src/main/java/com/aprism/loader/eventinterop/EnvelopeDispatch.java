package com.aprism.loader.eventinterop;

import com.aprism.api.EventPriority;
import com.aprism.loader.livectx.LiveContext;
import com.aprism.loader.livectx.LiveContextTracker;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Envelope dispatch service (v26.9 roadmap Alpha.5): typed, priority-
 * ordered, cancellation-aware delivery with side/lifecycle normalization.
 *
 * <p>Delivery rules: listeners run HIGHEST -> LOWEST; a cancelled
 * cancellable envelope stops delivery immediately (Forge-like semantics);
 * a non-cancellable envelope always reaches every listener. Listeners may
 * declare side/lifecycle constraints - delivery is skipped when the
 * current live context does not match, which is the normalization layer
 * that keeps host-specific event timing out of consumer code.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class EnvelopeDispatch {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG =
            Logger.getLogger(EnvelopeDispatch.class.getName());

    /** Delivery constraints a listener can declare (all must match). */
    public record Filter(Set<LiveContext.Side> sides,
            Set<LiveContext.State> lifecycles) {

        /**
         * @return true when the current context satisfies the filter
         */
        public boolean matches(LiveContext.Side side, LiveContext.State state) {
            return (sides == null || sides.isEmpty() || sides.contains(side))
                    && (lifecycles == null || lifecycles.isEmpty()
                            || lifecycles.contains(state));
        }
    }

    private record Listener(EnvelopeListener listener, EventPriority priority,
            Filter filter, long order) {
    }

    private final Map<String, List<Listener>> listeners =
            new ConcurrentHashMap<>();
    private final LiveContextTracker tracker;
    private volatile long sequence;

    /**
     * @param tracker optional live context tracker for filter resolution;
     *        null disables filtering (every envelope delivers)
     */
    public EnvelopeDispatch(LiveContextTracker tracker) {
        this.tracker = tracker;
    }

    /**
     * Registers a listener for the named envelope type.
     *
     * @param type the envelope type (namespaced, as published)
     * @param listener the listener
     * @param priority the dispatch priority
     * @param filter the side/lifecycle constraints (null = always)
     */
    public void register(String type, EnvelopeListener listener,
            EventPriority priority, Filter filter) {
        if (type == null || type.isBlank() || listener == null) {
            throw new IllegalArgumentException("type and listener required");
        }
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
                .add(new Listener(listener, priority == null
                        ? EventPriority.NORMAL : priority, filter,
                        sequence++));
    }

    /**
     * Publishes an envelope to the listeners of its type. Throwing
     * listeners are contained (delivery continues).
     *
     * @param envelope the envelope
     * @return true when the envelope was cancelled by a listener
     */
    public boolean post(EventEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope required");
        }
        List<Listener> typed = listeners.get(envelope.type());
        if (typed == null || typed.isEmpty()) {
            return false;
        }
        List<Listener> ordered = new java.util.ArrayList<>(typed);
        ordered.sort((a, b) -> {
            int byPriority = Integer.compare(priorityRank(a.priority()),
                    priorityRank(b.priority()));
            return byPriority != 0 ? byPriority : Long.compare(a.order(), b.order());
        });
        for (Listener listener : ordered) {
            if (envelope.isCancelled() && envelope.isCancellable()) {
                break;
            }
            if (listener.filter() != null && tracker != null && !listener
                    .filter().matches(envelope.side(), tracker.state(
                            envelope.side()))) {
                continue;
            }
            try {
                listener.listener().onEnvelope(envelope);
            } catch (Throwable contained) {
                LOG.warning("[eventinterop] listener threw, contained: "
                        + contained);
            }
        }
        return envelope.isCancelled();
    }

    private static int priorityRank(EventPriority priority) {
        // HIGHEST runs first.
        return switch (priority) {
            case HIGHEST -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
            case LOWEST -> 4;
        };
    }

    /**
     * @return the number of listeners registered for the type
     */
    public int listenerCount(String type) {
        List<Listener> typed = listeners.get(type);
        return typed == null ? 0 : typed.size();
    }

    /**
     * Removes all listeners for the type (leak prevention).
     */
    public void clear(String type) {
        listeners.remove(type);
    }
}
