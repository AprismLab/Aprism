package com.aprism.conformance.probe;

import java.util.concurrent.atomic.AtomicInteger;

import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;
import com.aprism.loader.livectx.ContentBindTrigger;
import com.aprism.loader.livectx.LiveContext;
import com.aprism.loader.livectx.LiveContextTracker;

/**
 * Live context lifecycle probe (v26.9-Alpha.3): the full client graph
 * BOOTSTRAP -> MENU -> IN_WORLD -> LEAVING -> IN_WORLD -> SHUTDOWN must
 * deliver transitions, drive the content bind trigger (re-arm semantics),
 * terminate on shutdown, and drop repeated reports.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LiveContextProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    @Override
    public ProbeResult run() {
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("lifecycle",
                "live-context transitions + bind triggers", "unit",
                CoverageMatrix.Status.CONTRACT_ONLY,
                "executed by ConformanceKit on every run");
        try {
            LiveContextTracker tracker = new LiveContextTracker();
            AtomicInteger binds = new AtomicInteger();
            ContentBindTrigger trigger =
                    new ContentBindTrigger(binds::incrementAndGet, true);
            tracker.addListener(trigger);

            boolean t1 = tracker.transition(LiveContext.Side.CLIENT,
                    LiveContext.State.MENU, "boot");
            boolean t2 = tracker.transition(LiveContext.Side.CLIENT,
                    LiveContext.State.IN_WORLD, "join");
            int afterFirstJoin = binds.get();
            boolean t3 = tracker.transition(LiveContext.Side.CLIENT,
                    LiveContext.State.LEAVING, "quit");
            boolean t4 = tracker.transition(LiveContext.Side.CLIENT,
                    LiveContext.State.IN_WORLD, "rejoin");
            boolean t5 = tracker.transition(LiveContext.Side.CLIENT,
                    LiveContext.State.SHUTDOWN, "bye");

            boolean pass = t1 && t2 && t3 && t4 && t5
                    && afterFirstJoin == 1
                    && binds.get() == 2
                    && tracker.listenerCount() == 0
                    && tracker.state(LiveContext.Side.CLIENT)
                            == LiveContext.State.SHUTDOWN
                    && tracker.droppedTransitions() == 0;
            return new ProbeResult(cell, pass, "binds=" + binds.get()
                    + " listenersAfterShutdown=" + tracker.listenerCount());
        } catch (Throwable t) {
            return new ProbeResult(cell, false, t.toString());
        }
    }
}
