package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.AprismEventBus;
import com.aprism.api.EventPriority;
import com.aprism.api.gameevent.GameTickEvent;

/**
 * Tests for dispatch-priority support on the Aprism event bus
 * (v26.3-Alpha.6, Forge/NeoForge EventPriority parity).
 *
 * @author BlockConnect@StarsailsClover
 */
class AprismEventBusPriorityTest {

    private GameTickEvent tick() {
        return new GameTickEvent(GameTickEvent.Stage.END, 0);
    }

    @Nested
    class Ordering {

        @Test
        void higherPriorityDispatchesFirst() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> order = new ArrayList<>();

            bus.register(GameTickEvent.class, e -> order.add("lowest"), EventPriority.LOWEST);
            bus.register(GameTickEvent.class, e -> order.add("highest"), EventPriority.HIGHEST);
            bus.register(GameTickEvent.class, e -> order.add("normal"), EventPriority.NORMAL);

            bus.post(tick());

            assertThat(order).containsExactly("highest", "normal", "lowest");
        }

        @Test
        void fullPriorityLadderDispatchesHighToLow() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> order = new ArrayList<>();

            for (EventPriority priority : new EventPriority[] {
                    EventPriority.LOWEST, EventPriority.NORMAL, EventPriority.HIGHEST,
                    EventPriority.LOW, EventPriority.HIGH }) {
                bus.register(GameTickEvent.class, e -> order.add(priority.name()), priority);
            }

            bus.post(tick());

            assertThat(order).containsExactly("HIGHEST", "HIGH", "NORMAL", "LOW", "LOWEST");
        }

        @Test
        void samePriorityKeepsRegistrationOrder() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> order = new ArrayList<>();

            bus.register(GameTickEvent.class, e -> order.add("first"), EventPriority.NORMAL);
            bus.register(GameTickEvent.class, e -> order.add("second"), EventPriority.NORMAL);
            bus.register(GameTickEvent.class, e -> order.add("third"), EventPriority.NORMAL);

            bus.post(tick());

            assertThat(order).containsExactly("first", "second", "third");
        }

        @Test
        void registrationWithoutPriorityDefaultsToNormal() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> order = new ArrayList<>();

            bus.register(GameTickEvent.class, e -> order.add("high"), EventPriority.HIGH);
            bus.register(GameTickEvent.class, e -> order.add("default"));
            bus.register(GameTickEvent.class, e -> order.add("low"), EventPriority.LOW);

            bus.post(tick());

            assertThat(order).containsExactly("high", "default", "low");
        }

        @Test
        void nullPriorityFallsBackToNormal() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> order = new ArrayList<>();

            bus.register(GameTickEvent.class, e -> order.add("high"), EventPriority.HIGH);
            bus.register(GameTickEvent.class, e -> order.add("null-priority"), null);
            bus.register(GameTickEvent.class, e -> order.add("low"), EventPriority.LOW);

            bus.post(tick());

            assertThat(order).containsExactly("high", "null-priority", "low");
        }

        @Test
        void lateHighPriorityRegistrationStillDispatchesFirst() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> order = new ArrayList<>();

            bus.register(GameTickEvent.class, e -> order.add("early"), EventPriority.LOW);
            bus.register(GameTickEvent.class, e -> order.add("late"), EventPriority.HIGHEST);

            bus.post(tick());

            assertThat(order).containsExactly("late", "early");
        }
    }

    @Nested
    class CancellationAndUnregister {

        @Test
        void highPriorityCancelStopsLowerPriorities() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> order = new ArrayList<>();
            GameTickEvent cancellable = new GameTickEvent(GameTickEvent.Stage.START, 0);

            bus.register(GameTickEvent.class, e -> order.add("lowest"), EventPriority.LOWEST);
            bus.register(GameTickEvent.class,
                    e -> { order.add("highest-cancels"); e.setCancelled(true); },
                    EventPriority.HIGHEST);
            bus.register(GameTickEvent.class, e -> order.add("normal"), EventPriority.NORMAL);

            bus.post(cancellable);

            assertThat(cancellable.isCancelled()).isTrue();
            assertThat(order).containsExactly("highest-cancels");
        }

        @Test
        void unregisterRemovesListenerAtAnyPriority() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> order = new ArrayList<>();

            bus.register(GameTickEvent.class, e -> order.add("high"), EventPriority.HIGH);
            com.aprism.api.AprismEventListener<GameTickEvent> low = e -> order.add("low");
            bus.register(GameTickEvent.class, low, EventPriority.LOWEST);

            bus.unregister(GameTickEvent.class, low);
            bus.post(tick());

            assertThat(order).containsExactly("high");
        }

        @Test
        void priorityComparatorHelperBehaves() {
            assertThat(EventPriority.HIGHEST.dispatchesBefore(EventPriority.LOW)).isTrue();
            assertThat(EventPriority.LOW.dispatchesBefore(EventPriority.HIGHEST)).isFalse();
            assertThat(EventPriority.NORMAL.dispatchesBefore(EventPriority.NORMAL)).isFalse();
        }
    }
}
