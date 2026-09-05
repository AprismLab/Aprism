package com.aprism.conformance.probe;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.aprism.api.EventPriority;
import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;
import com.aprism.loader.eventinterop.EnvelopeDispatch;
import com.aprism.loader.eventinterop.EventEnvelope;
import com.aprism.loader.livectx.LiveContext;
import com.aprism.loader.livectx.LiveContextTracker;

/**
 * Event interop probe (v26.9-Alpha.5): normalized envelope dispatch with
 * priority, cancellation, and side/lifecycle normalization against the
 * live context tracker.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class EventInteropProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    @Override
    public ProbeResult run() {
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("events",
                "interop envelope + priority/cancellation + filters", "unit",
                CoverageMatrix.Status.CONTRACT_ONLY,
                "executed by ConformanceKit on every run");
        try {
            LiveContextTracker tracker = new LiveContextTracker();
            tracker.transition(LiveContext.Side.CLIENT, LiveContext.State.MENU,
                    "boot");
            EnvelopeDispatch dispatch = new EnvelopeDispatch(tracker);
            List<String> order = new CopyOnWriteArrayList<>();

            dispatch.register("conformance:probe", e -> {
                order.add("high-cancel");
                e.cancel();
            }, EventPriority.HIGHEST, null);
            dispatch.register("conformance:probe", e -> order.add("low"),
                    EventPriority.LOWEST,
                    new EnvelopeDispatch.Filter(
                            java.util.Set.of(LiveContext.Side.CLIENT),
                            java.util.Set.of(LiveContext.State.IN_WORLD)));

            EventEnvelope cancellable = EventEnvelope.builder("conformance:probe")
                    .source("conformance").cancellable(true)
                    .header("probe", "1").payload(new Object()).build();
            boolean cancelled = dispatch.post(cancellable);
            boolean cancelledFlag = cancelled;
            order.clear();

            tracker.transition(LiveContext.Side.CLIENT, LiveContext.State.IN_WORLD,
                    "join");
            // Non-cancellable envelope: cancellation is ignored, so the
            // IN_WORLD-filtered LOWEST listener now delivers too.
            EventEnvelope open = EventEnvelope.builder("conformance:probe")
                    .source("conformance").cancellable(false)
                    .payload(new Object()).build();
            boolean notCancelled = dispatch.post(open);

            boolean pass = cancelledFlag
                    && order.equals(List.of("high-cancel", "low"));
            return new ProbeResult(cell, pass, "delivery=" + order);
        } catch (Throwable t) {
            return new ProbeResult(cell, false, t.toString());
        }
    }
}
