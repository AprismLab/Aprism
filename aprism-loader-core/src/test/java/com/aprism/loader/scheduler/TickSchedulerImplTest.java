package com.aprism.loader.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.scheduler.ScheduledTask;
import com.aprism.api.scheduler.TickSide;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the tick-task scheduler (v26.3-Alpha.9, Fabric
 * ServerTickEvents/ClientTickEvents parity): interval firing, one-shot
 * removal, fail-safe handler isolation, and runtime wiring.
 *
 * @author BlockConnect@StarsailsClover
 */
class TickSchedulerImplTest {

    private final TickSchedulerImpl scheduler = new TickSchedulerImpl();

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class Scheduling {

        @Test
        void scheduleCreatesRepeatingTask() {
            ScheduledTask task = scheduler.schedule(TickSide.SERVER, 2, (Runnable) () -> { });

            assertThat(task.repeating()).isTrue();
            assertThat(task.intervalTicks()).isEqualTo(2);
            assertThat(task.side()).isEqualTo(TickSide.SERVER);
            assertThat(scheduler.scheduledTasks(TickSide.SERVER)).containsExactly(task);
        }

        @Test
        void scheduleOnceCreatesOneShotTask() {
            ScheduledTask task = scheduler.scheduleOnce(TickSide.CLIENT, 5, (Runnable) () -> { });

            assertThat(task.repeating()).isFalse();
            assertThat(task.intervalTicks()).isEqualTo(5);
            assertThat(scheduler.scheduledTasks(TickSide.CLIENT)).containsExactly(task);
        }

        @Test
        void zeroIntervalIsRejected() {
            assertThatThrownBy(() -> scheduler.schedule(TickSide.SERVER, 0, (Runnable) () -> { }))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullHandlerIsRejected() {
            assertThatThrownBy(() -> scheduler.schedule(TickSide.SERVER, 1, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void unscheduleRemovesTask() {
            ScheduledTask task = scheduler.schedule(TickSide.SERVER, 1, (Runnable) () -> { });

            assertThat(scheduler.unschedule(task)).isTrue();
            assertThat(scheduler.scheduledTasks(TickSide.SERVER)).isEmpty();
        }

        @Test
        void unscheduleUnknownTaskReturnsFalse() {
            ScheduledTask task = scheduler.schedule(TickSide.SERVER, 1, (Runnable) () -> { });
            scheduler.unschedule(task);

            assertThat(scheduler.unschedule(task)).isFalse();
        }
    }

    @Nested
    class TickFiring {

        @Test
        void oneShotFiresOnceAtDueTick() {
            List<String> fired = new ArrayList<>();
            scheduler.scheduleOnce(TickSide.SERVER, 3, (Runnable) () -> fired.add("fired"));

            scheduler.runTick(TickSide.SERVER, 0);
            scheduler.runTick(TickSide.SERVER, 1);
            scheduler.runTick(TickSide.SERVER, 2);
            assertThat(fired).isEmpty();

            scheduler.runTick(TickSide.SERVER, 3);
            assertThat(fired).containsExactly("fired");

            scheduler.runTick(TickSide.SERVER, 4);
            assertThat(fired).containsExactly("fired");
            assertThat(scheduler.scheduledTasks(TickSide.SERVER)).isEmpty();
        }

        @Test
        void repeatingFiresEveryInterval() {
            AtomicInteger count = new AtomicInteger();
            scheduler.schedule(TickSide.SERVER, 2, (Runnable) count::incrementAndGet);

            for (long tick = 0; tick <= 6; tick++) {
                scheduler.runTick(TickSide.SERVER, tick);
            }

            assertThat(count.get()).isGreaterThanOrEqualTo(2);
            assertThat(scheduler.scheduledTasks(TickSide.SERVER)).hasSize(1);
        }

        @Test
        void sidesAreIndependent() {
            AtomicInteger serverFired = new AtomicInteger();
            AtomicInteger clientFired = new AtomicInteger();
            scheduler.schedule(TickSide.SERVER, 1, (Runnable) serverFired::incrementAndGet);
            scheduler.schedule(TickSide.CLIENT, 1, (Runnable) clientFired::incrementAndGet);

            scheduler.runTick(TickSide.SERVER, 1);

            assertThat(serverFired.get()).isGreaterThanOrEqualTo(1);
            assertThat(clientFired.get()).isZero();
        }

        @Test
        void throwingHandlerDoesNotAbortOtherTasks() {
            List<String> fired = new ArrayList<>();
            scheduler.schedule(TickSide.SERVER, 1, (Runnable) () -> fired.add("before"));
            scheduler.schedule(TickSide.SERVER, 1, (Runnable) () -> { throw new RuntimeException("boom"); });
            scheduler.schedule(TickSide.SERVER, 1, (Runnable) () -> fired.add("after"));

            scheduler.runTick(TickSide.SERVER, 1);

            assertThat(fired).containsExactly("before", "after");
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesTickScheduler() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getTickScheduler()).isNotNull();
        }

        @Test
        void runtimeShutdownClearsScheduler() {
            AprismRuntime runtime = AprismRuntime.instance();
            runtime.getTickScheduler().schedule(TickSide.SERVER, 1, (Runnable) () -> { });

            runtime.shutdown();

            assertThat(runtime.getTickScheduler().scheduledTasks(TickSide.SERVER)).isEmpty();
        }
    }
}
