package com.aprism.loader.livectx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Unit tests for the live context tracker and the content bind trigger
 * (v26.9-Alpha.3): state machine, fail-open lenient mode, strict mode,
 * bounded history, shutdown listener clearing, and one-shot/re-arm trigger
 * semantics.
 *
 * @author BlockConnect@StarsailsClover
 */
class LiveContextTrackerTest {

    @Test
    void transitionsDeliverAndHistoryBounds() {
        LiveContextTracker tracker = new LiveContextTracker();
        AtomicInteger seen = new AtomicInteger();
        tracker.addListener(t -> seen.incrementAndGet());
        assertTrue(tracker.transition(LiveContext.Side.CLIENT,
                LiveContext.State.MENU, "boot"));
        assertTrue(tracker.transition(LiveContext.Side.CLIENT,
                LiveContext.State.IN_WORLD, "join"));
        assertEquals(2, seen.get());
        assertEquals(LiveContext.State.IN_WORLD,
                tracker.state(LiveContext.Side.CLIENT));
        // Repeated same-state reports are dropped, not delivered.
        assertFalse(tracker.transition(LiveContext.Side.CLIENT,
                LiveContext.State.IN_WORLD, "again"));
        assertEquals(2, seen.get());
        assertEquals(1, tracker.droppedTransitions());
        // History is bounded at 32.
        for (int i = 0; i < 40; i++) {
            tracker.transition(LiveContext.Side.CLIENT,
                    i % 2 == 0 ? LiveContext.State.LEAVING
                            : LiveContext.State.IN_WORLD,
                    "churn-" + i);
        }
        assertEquals(32, tracker.history().size());
    }

    @Test
    void strictModeRejectsIllegalGraph() {
        LiveContextTracker tracker = new LiveContextTracker();
        tracker.setStrict(true);
        // MENU -> LEAVING is not a legal edge.
        tracker.transition(LiveContext.Side.CLIENT, LiveContext.State.MENU, "boot");
        assertFalse(tracker.transition(LiveContext.Side.CLIENT,
                LiveContext.State.LEAVING, "illegal"));
        assertEquals(LiveContext.State.MENU,
                tracker.state(LiveContext.Side.CLIENT));
        assertEquals(1, tracker.droppedTransitions());
    }

    @Test
    void shutdownIsTerminalAndClearsListeners() {
        LiveContextTracker tracker = new LiveContextTracker();
        AtomicInteger seen = new AtomicInteger();
        tracker.addListener(t -> seen.incrementAndGet());
        assertTrue(tracker.transition(LiveContext.Side.CLIENT,
                LiveContext.State.SHUTDOWN, "bye"));
        assertEquals(1, seen.get());
        assertEquals(0, tracker.listenerCount());
        assertFalse(tracker.transition(LiveContext.Side.CLIENT,
                LiveContext.State.MENU, "post-shutdown"));
        assertFalse(tracker.addListener(t -> seen.incrementAndGet()));
    }

    @Test
    void throwingListenerIsContained() {
        LiveContextTracker tracker = new LiveContextTracker();
        AtomicInteger seen = new AtomicInteger();
        tracker.addListener(t -> {
            throw new IllegalStateException("boom");
        });
        tracker.addListener(t -> seen.incrementAndGet());
        assertTrue(tracker.transition(LiveContext.Side.SERVER,
                LiveContext.State.MENU, "boot"));
        assertEquals(1, seen.get());
    }

    @Test
    void contentBindTriggerFiresOncePerSideAndReArms() {
        AtomicInteger binds = new AtomicInteger();
        ContentBindTrigger trigger = new ContentBindTrigger(binds::incrementAndGet,
                true);
        LiveContextTracker tracker = new LiveContextTracker();
        tracker.addListener(trigger);
        tracker.transition(LiveContext.Side.CLIENT, LiveContext.State.MENU, "boot");
        assertEquals(0, trigger.firingCount(LiveContext.Side.CLIENT));
        tracker.transition(LiveContext.Side.CLIENT, LiveContext.State.IN_WORLD, "join");
        assertEquals(1, trigger.firingCount(LiveContext.Side.CLIENT));
        // Second IN_WORLD without leaving: no refire.
        tracker.transition(LiveContext.Side.CLIENT, LiveContext.State.LEAVING, "quit");
        tracker.transition(LiveContext.Side.CLIENT, LiveContext.State.IN_WORLD, "rejoin");
        assertEquals(2, trigger.firingCount(LiveContext.Side.CLIENT));
        // Non-re-arm instance never fires twice.
        AtomicInteger single = new AtomicInteger();
        ContentBindTrigger noReArm = new ContentBindTrigger(single::incrementAndGet,
                false);
        LiveContextTracker t2 = new LiveContextTracker();
        t2.addListener(noReArm);
        t2.transition(LiveContext.Side.SERVER, LiveContext.State.IN_WORLD, "join");
        t2.transition(LiveContext.Side.SERVER, LiveContext.State.LEAVING, "quit");
        t2.transition(LiveContext.Side.SERVER, LiveContext.State.IN_WORLD, "rejoin");
        assertEquals(1, noReArm.firingCount(LiveContext.Side.SERVER));
    }

    @Test
    void replayToReturnsSnapshot() {
        LiveContextTracker tracker = new LiveContextTracker();
        tracker.transition(LiveContext.Side.CLIENT, LiveContext.State.MENU, "boot");
        List<LiveContextTransition> sink = tracker.replayTo(new java.util.ArrayList<>());
        assertEquals(1, sink.size());
        assertEquals(LiveContext.State.MENU, sink.get(0).to());
    }
}
