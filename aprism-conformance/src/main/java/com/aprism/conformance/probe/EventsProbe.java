package com.aprism.conformance.probe;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.aprism.api.AprismEvent;
import com.aprism.api.AprismEventListener;
import com.aprism.api.AprismPhase;
import com.aprism.api.EventPriority;
import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;
import com.aprism.loader.AprismEventBusImpl;

/**
 * Events contract probe (v26.9-Alpha.1): typed publish/subscribe with
 * priority ordering (HIGHEST before NORMAL before LOWEST).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class EventsProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Probe-local event type (GameEvent is the non-sealed extension). */
    public static final class Ping implements AprismEvent.GameEvent {

        @Override
        public AprismPhase getPhase() {
            return AprismPhase.INIT;
        }

        @Override
        public boolean isCancellable() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            // non-cancellable
        }
    }

    @Override
    public ProbeResult run() {
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("events",
                "typed bus + priority dispatch", "unit",
                CoverageMatrix.Status.CONTRACT_ONLY,
                "executed by ConformanceKit on every run");
        try {
            AprismEventBusImpl bus = new AprismEventBusImpl();
            List<String> order = new CopyOnWriteArrayList<>();
            bus.register(Ping.class, (AprismEventListener<Ping>) e -> order.add("LOW"),
                    EventPriority.LOWEST);
            bus.register(Ping.class, (AprismEventListener<Ping>) e -> order.add("HIGH"),
                    EventPriority.HIGHEST);
            bus.register(Ping.class, (AprismEventListener<Ping>) e -> order.add("NORMAL"),
                    EventPriority.NORMAL);
            bus.post(new Ping());
            boolean ordered = order.equals(List.of("HIGH", "NORMAL", "LOW"));
            return new ProbeResult(cell, ordered, "dispatch order=" + order);
        } catch (Throwable t) {
            return new ProbeResult(cell, false, t.toString());
        }
    }
}
