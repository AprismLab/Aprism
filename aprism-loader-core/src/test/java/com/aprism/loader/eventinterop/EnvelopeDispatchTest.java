package com.aprism.loader.eventinterop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Event interop tests (v26.9-Alpha.5): priority order, cancellation
 * semantics, side/lifecycle filters against the live context tracker,
 * envelope validation, and listener containment.
 *
 * @author BlockConnect@StarsailsClover
 */
class EnvelopeDispatchTest {

    private static EventEnvelope envelope(String type, boolean cancellable) {
        return EventEnvelope.builder(type)
                .source("test")
                .cancellable(cancellable)
                .payload(new Object())
                .build();
    }

    @Test
    void priorityOrderHighestFirst() {
        EnvelopeDispatch dispatch = new EnvelopeDispatch(null);
        List<String> order = new CopyOnWriteArrayList<>();
        dispatch.register("test:evt", e -> order.add("LOW"),
                com.aprism.api.EventPriority.LOWEST, null);
        dispatch.register("test:evt", e -> order.add("HIGH"),
                com.aprism.api.EventPriority.HIGHEST, null);
        dispatch.register("test:evt", e -> order.add("NORMAL"),
                com.aprism.api.EventPriority.NORMAL, null);
        assertFalse(dispatch.post(envelope("test:evt", false)));
        assertEquals(List.of("HIGH", "NORMAL", "LOW"), order);
    }

    @Test
    void cancellationStopsLowerPriority() {
        EnvelopeDispatch dispatch = new EnvelopeDispatch(null);
        List<String> order = new CopyOnWriteArrayList<>();
        dispatch.register("test:cancel", e -> {
            order.add("HIGHEST");
            e.cancel();
        }, com.aprism.api.EventPriority.HIGHEST, null);
        dispatch.register("test:cancel", e -> order.add("LOWEST"),
                com.aprism.api.EventPriority.LOWEST, null);
        assertTrue(dispatch.post(envelope("test:cancel", true)));
        assertEquals(List.of("HIGHEST"), order);
    }

    @Test
    void nonCancellableDeliversToAllEvenAfterCancelAttempt() {
        EnvelopeDispatch dispatch = new EnvelopeDispatch(null);
        List<String> order = new CopyOnWriteArrayList<>();
        dispatch.register("test:hard", e -> {
            order.add("first");
            e.cancel();
        }, com.aprism.api.EventPriority.HIGHEST, null);
        dispatch.register("test:hard", e -> order.add("second"),
                com.aprism.api.EventPriority.LOWEST, null);
        assertFalse(dispatch.post(envelope("test:hard", false)));
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void sideAndLifecycleFiltersNormalizeDelivery() {
        com.aprism.loader.livectx.LiveContextTracker tracker =
                new com.aprism.loader.livectx.LiveContextTracker();
        tracker.transition(com.aprism.loader.livectx.LiveContext.Side.CLIENT,
                com.aprism.loader.livectx.LiveContext.State.MENU, "boot");
        EnvelopeDispatch dispatch = new EnvelopeDispatch(tracker);
        List<String> order = new CopyOnWriteArrayList<>();
        dispatch.register("test:filtered", e -> order.add("clientOnly"),
                com.aprism.api.EventPriority.NORMAL,
                new EnvelopeDispatch.Filter(
                        java.util.Set.of(com.aprism.loader.livectx.LiveContext.Side.CLIENT),
                        java.util.Set.of(com.aprism.loader.livectx.LiveContext.State.IN_WORLD)));
        dispatch.register("test:filtered", e -> order.add("anySide"),
                com.aprism.api.EventPriority.NORMAL, null);
        // Envelope says CLIENT but the tracker state is MENU: the
        // IN_WORLD-only listener is skipped, the unfiltered one delivers.
        dispatch.post(envelope("test:filtered", false));
        assertEquals(List.of("anySide"), order);
        // After the world join the filtered listener delivers too.
        tracker.transition(com.aprism.loader.livectx.LiveContext.Side.CLIENT,
                com.aprism.loader.livectx.LiveContext.State.IN_WORLD, "join");
        dispatch.post(envelope("test:filtered", false));
        assertEquals(List.of("anySide", "clientOnly", "anySide"), order);
    }

    @Test
    void throwingListenerIsContained() {
        EnvelopeDispatch dispatch = new EnvelopeDispatch(null);
        List<String> order = new CopyOnWriteArrayList<>();
        dispatch.register("test:boom", e -> {
            throw new IllegalStateException("listener exploded");
        }, com.aprism.api.EventPriority.HIGHEST, null);
        dispatch.register("test:boom", e -> order.add("survived"),
                com.aprism.api.EventPriority.LOWEST, null);
        assertFalse(dispatch.post(envelope("test:boom", false)));
        assertEquals(List.of("survived"), order);
    }

    @Test
    void envelopeValidationIsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> EventEnvelope.builder("notnamespaced"));
        assertThrows(IllegalArgumentException.class,
                () -> EventEnvelope.builder(null));
        assertThrows(UnsupportedOperationException.class, () ->
                EventEnvelope.builder("test:hdr").header("k", "v").build()
                        .headers().put("inject", "x"));
    }

    @Test
    void clearRemovesListeners() {
        EnvelopeDispatch dispatch = new EnvelopeDispatch(null);
        dispatch.register("test:gone", e -> {
        }, com.aprism.api.EventPriority.NORMAL, null);
        assertEquals(1, dispatch.listenerCount("test:gone"));
        dispatch.clear("test:gone");
        assertEquals(0, dispatch.listenerCount("test:gone"));
        assertFalse(dispatch.post(envelope("test:gone", false)));
    }
}
