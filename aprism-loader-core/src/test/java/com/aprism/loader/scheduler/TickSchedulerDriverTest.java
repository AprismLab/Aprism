package com.aprism.loader.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.AprismEventBus;
import com.aprism.api.gameevent.GameTickEvent;
import com.aprism.api.scheduler.TickSide;
import com.aprism.loader.AprismEventBusImpl;

/**
 * JUnit 5 + AssertJ tests for {@link TickSchedulerDriver}
 * (v26.5-Alpha.6).
 *
 * @author BlockConnect@StarsailsClover
 */
class TickSchedulerDriverTest {

    private AprismEventBus eventBus;
    private TickSchedulerImpl scheduler;
    private TickSchedulerDriver driver;

    @BeforeEach
    void setUp() {
        eventBus = new AprismEventBusImpl();
        scheduler = new TickSchedulerImpl();
        driver = new TickSchedulerDriver(scheduler, eventBus);
    }

    @AfterEach
    void tearDown() {
        driver.detach();
        scheduler.clear();
    }

    @Nested
    class Construction {

        @Test
        void nullSchedulerThrows() {
            try {
                new TickSchedulerDriver(null, eventBus);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
            }
        }

        @Test
        void nullEventBusThrows() {
            try {
                new TickSchedulerDriver(scheduler, null);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Nested
    class Attachment {

        @Test
        void notAttachedByDefault() {
            assertThat(driver.isAttached()).isFalse();
        }

        @Test
        void attachRegistersListener() {
            driver.attach();
            assertThat(driver.isAttached()).isTrue();
        }

        @Test
        void attachIsIdempotent() {
            driver.attach();
            driver.attach();
            assertThat(driver.isAttached()).isTrue();
        }

        @Test
        void detachRemovesListener() {
            driver.attach();
            driver.detach();
            assertThat(driver.isAttached()).isFalse();
        }

        @Test
        void detachIsIdempotent() {
            driver.detach();
            driver.detach();
            assertThat(driver.isAttached()).isFalse();
        }
    }

    @Nested
    class ActiveSide {

        @Test
        void activeSideNullByDefault() {
            assertThat(driver.getActiveSide()).isNull();
        }

        @Test
        void setActiveSide() {
            driver.setActiveSide(TickSide.SERVER);
            assertThat(driver.getActiveSide()).isEqualTo(TickSide.SERVER);
        }

        @Test
        void detachClearsActiveSide() {
            driver.setActiveSide(TickSide.CLIENT);
            driver.detach();
            assertThat(driver.getActiveSide()).isNull();
        }
    }

    @Nested
    class TickDriving {

        @Test
        void tickEventDrivesSchedulerWhenAttachedAndSideSet() {
            var fired = new AtomicInteger(0);
            driver.setActiveSide(TickSide.SERVER);
            driver.attach();

            scheduler.schedule(TickSide.SERVER, 1, (Runnable) fired::incrementAndGet);

            // Fire a TICK_START event at tick 1 (nextFireTick=1, so tick 1 triggers it)
            eventBus.post(new GameTickEvent(GameTickEvent.Stage.START, 1));

            assertThat(fired.get()).isEqualTo(1);
        }

        @Test
        void tickEventIgnoredWhenSideNull() {
            var fired = new AtomicInteger(0);
            driver.attach();

            scheduler.schedule(TickSide.SERVER, 1, (Runnable) fired::incrementAndGet);

            eventBus.post(new GameTickEvent(GameTickEvent.Stage.START, 0));

            assertThat(fired.get()).isEqualTo(0);
        }

        @Test
        void tickEventIgnoredWhenNotAttached() {
            var fired = new AtomicInteger(0);
            driver.setActiveSide(TickSide.CLIENT);

            scheduler.schedule(TickSide.CLIENT, 1, (Runnable) fired::incrementAndGet);

            eventBus.post(new GameTickEvent(GameTickEvent.Stage.START, 0));

            assertThat(fired.get()).isEqualTo(0);
        }

        @Test
        void tickEndEventDoesNotDriveScheduler() {
            var fired = new AtomicInteger(0);
            driver.setActiveSide(TickSide.SERVER);
            driver.attach();

            scheduler.schedule(TickSide.SERVER, 1, (Runnable) fired::incrementAndGet);

            eventBus.post(new GameTickEvent(GameTickEvent.Stage.END, 0));

            assertThat(fired.get()).isEqualTo(0);
        }

        @Test
        void multipleTicksDriveRepeatedly() {
            var fired = new AtomicInteger(0);
            driver.setActiveSide(TickSide.CLIENT);
            driver.attach();

            scheduler.schedule(TickSide.CLIENT, 1, (Runnable) fired::incrementAndGet);

            // nextFireTick=1; ticks 1, 2, 3 each fire once, then reschedule
            eventBus.post(new GameTickEvent(GameTickEvent.Stage.START, 1));
            eventBus.post(new GameTickEvent(GameTickEvent.Stage.START, 2));
            eventBus.post(new GameTickEvent(GameTickEvent.Stage.START, 3));

            assertThat(fired.get()).isEqualTo(3);
        }

        @Test
        void clientSideDoesNotDriveServerTasks() {
            var fired = new AtomicInteger(0);
            driver.setActiveSide(TickSide.CLIENT);
            driver.attach();

            scheduler.schedule(TickSide.SERVER, 1, (Runnable) fired::incrementAndGet);

            eventBus.post(new GameTickEvent(GameTickEvent.Stage.START, 0));

            assertThat(fired.get()).isEqualTo(0);
        }

        @Test
        void throwingTaskDoesNotBreakDriver() {
            driver.setActiveSide(TickSide.SERVER);
            driver.attach();

            var fired = new AtomicInteger(0);
            scheduler.schedule(TickSide.SERVER, 1, (Runnable) () -> {
                throw new RuntimeException("simulated task failure");
            });
            scheduler.schedule(TickSide.SERVER, 1, (Runnable) fired::incrementAndGet);

            // The throwing task is caught by the scheduler's runTick;
            // the second task still fires.
            eventBus.post(new GameTickEvent(GameTickEvent.Stage.START, 1));

            assertThat(fired.get()).isEqualTo(1);
        }
    }
}
