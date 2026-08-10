package com.aprism.loader.gameevent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.AprismEventBus;
import com.aprism.api.gameevent.ClientRenderEvent;
import com.aprism.api.gameevent.GameTickEvent;
import com.aprism.api.gameevent.WorldLoadEvent;
import com.aprism.api.gameevent.WorldUnloadEvent;
import com.aprism.loader.AprismEventBusImpl;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the game-event dispatch foundation (v26.3-Alpha.1, QA0 gap #1):
 * {@link GameEventDispatcher} behaviour (attachment gating, counters,
 * cancellation, fail-safety) and the runtime wiring
 * ({@code AprismRuntime.getGameEventDispatcher()}).
 *
 * @author BlockConnect@StarsailsClover
 */
class GameEventDispatcherTest {

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    private GameEventDispatcher dispatcher(AprismEventBus bus) {
        GameEventDispatcher dispatcher = new GameEventDispatcher(bus);
        dispatcher.setAttached(true);
        return dispatcher;
    }

    @Nested
    class AttachmentGating {

        @Test
        void eventsDroppedWhenDetached() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> seen = new ArrayList<>();
            bus.register(GameTickEvent.class, e -> seen.add("tick"));

            GameEventDispatcher dispatcher = new GameEventDispatcher(bus);
            // Not attached: events must not reach listeners.
            dispatcher.fireTickStart();
            dispatcher.fireTickEnd();

            assertThat(seen).isEmpty();
            assertThat(dispatcher.getTickNumber()).isZero();
        }

        @Test
        void eventsDeliveredWhenAttached() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> seen = new ArrayList<>();
            bus.register(GameTickEvent.class, e -> seen.add(e.getStage().name()));

            GameEventDispatcher dispatcher = dispatcher(bus);
            dispatcher.fireTickStart();
            dispatcher.fireTickEnd();

            assertThat(seen).containsExactly("START", "END");
        }

        @Test
        void detachingStopsDelivery() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> seen = new ArrayList<>();
            bus.register(GameTickEvent.class, e -> seen.add("tick"));

            GameEventDispatcher dispatcher = dispatcher(bus);
            dispatcher.fireTickStart();
            dispatcher.setAttached(false);
            dispatcher.fireTickStart();

            assertThat(seen).hasSize(1);
        }
    }

    @Nested
    class Counters {

        @Test
        void tickCounterAdvancesOnTickEnd() {
            AprismEventBus bus = new AprismEventBusImpl();
            GameEventDispatcher dispatcher = dispatcher(bus);

            dispatcher.fireTickStart();
            dispatcher.fireTickEnd();
            dispatcher.fireTickStart();
            dispatcher.fireTickEnd();

            assertThat(dispatcher.getTickNumber()).isEqualTo(2);
        }

        @Test
        void frameCounterAdvancesOnRender() {
            AprismEventBus bus = new AprismEventBusImpl();
            GameEventDispatcher dispatcher = dispatcher(bus);

            dispatcher.fireRender(0.0);
            dispatcher.fireRender(0.5);
            dispatcher.fireRender(1.0);

            assertThat(dispatcher.getFrameNumber()).isEqualTo(3);
        }

        @Test
        void tickEventsCarryCurrentTickNumber() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<Long> numbers = new ArrayList<>();
            bus.register(GameTickEvent.class, e -> numbers.add(e.getTickNumber()));

            GameEventDispatcher dispatcher = dispatcher(bus);
            dispatcher.fireTickStart(); // tick 0 start
            dispatcher.fireTickEnd();   // tick 0 end -> counter becomes 1
            dispatcher.fireTickStart(); // tick 1 start

            assertThat(numbers).containsExactly(0L, 0L, 1L);
        }
    }

    @Nested
    class Cancellation {

        @Test
        void tickStartIsCancellable() {
            AprismEventBus bus = new AprismEventBusImpl();
            bus.register(GameTickEvent.class, e -> e.setCancelled(true));

            GameEventDispatcher dispatcher = dispatcher(bus);
            boolean cancelled = dispatcher.fireTickStart();

            assertThat(cancelled).isTrue();
        }

        @Test
        void tickEndIsNotCancellable() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<Boolean> cancellableFlags = new ArrayList<>();
            bus.register(GameTickEvent.class,
                    e -> cancellableFlags.add(e.isCancellable()));

            GameEventDispatcher dispatcher = dispatcher(bus);
            dispatcher.fireTickStart();
            dispatcher.fireTickEnd();

            assertThat(cancellableFlags).containsExactly(true, false);
        }

        @Test
        void renderIsCancellableAndCarriesPartialTick() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<Double> partials = new ArrayList<>();
            bus.register(ClientRenderEvent.class, e -> {
                partials.add(e.getPartialTick());
                e.setCancelled(true);
            });

            GameEventDispatcher dispatcher = dispatcher(bus);
            boolean cancelled = dispatcher.fireRender(0.75);

            assertThat(cancelled).isTrue();
            assertThat(partials).containsExactly(0.75);
        }
    }

    @Nested
    class WorldEvents {

        @Test
        void worldLoadAndUnloadDelivered() {
            AprismEventBus bus = new AprismEventBusImpl();
            List<String> seen = new ArrayList<>();
            bus.register(WorldLoadEvent.class, e -> seen.add("load:" + e.getWorldId()));
            bus.register(WorldUnloadEvent.class, e -> seen.add("unload:" + e.getWorldId()));

            GameEventDispatcher dispatcher = dispatcher(bus);
            dispatcher.fireWorldLoad("world-1");
            dispatcher.fireWorldUnload("world-1");

            assertThat(seen).containsExactly("load:world-1", "unload:world-1");
        }
    }

    @Nested
    class FailSafety {

        @Test
        void throwingListenerDoesNotPropagate() {
            AprismEventBus bus = new AprismEventBusImpl();
            bus.register(GameTickEvent.class, e -> {
                throw new RuntimeException("synthetic listener failure");
            });

            GameEventDispatcher dispatcher = dispatcher(bus);
            // Must not throw.
            boolean cancelled = dispatcher.fireTickStart();
            dispatcher.fireTickEnd();

            assertThat(cancelled).isFalse();
            assertThat(dispatcher.getTickNumber()).isEqualTo(1);
        }

        @Test
        void resetDetachesAndClearsCounters() {
            AprismEventBus bus = new AprismEventBusImpl();
            GameEventDispatcher dispatcher = dispatcher(bus);
            dispatcher.fireTickEnd();
            dispatcher.fireRender(0.0);

            dispatcher.reset();

            assertThat(dispatcher.isAttached()).isFalse();
            assertThat(dispatcher.getTickNumber()).isZero();
            assertThat(dispatcher.getFrameNumber()).isZero();
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesDispatcherAfterInitialize() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");

            GameEventDispatcher dispatcher = runtime.getGameEventDispatcher();
            assertThat(dispatcher).isNotNull();
            assertThat(dispatcher.isAttached()).isFalse();
        }

        @Test
        void dispatcherResetOnShutdown() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.initialize(null, "26.3.0", "JE", "26.2");
            GameEventDispatcher dispatcher = runtime.getGameEventDispatcher();
            dispatcher.setAttached(true);
            dispatcher.fireTickEnd();

            runtime.shutdown();

            assertThat(runtime.getGameEventDispatcher()).isNull();
        }
    }
}
