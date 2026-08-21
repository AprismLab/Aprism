package com.aprism.loader.scheduler;

import java.util.logging.Logger;

import com.aprism.api.AprismEventBus;
import com.aprism.api.AprismEventListener;
import com.aprism.api.gameevent.GameTickEvent;
import com.aprism.api.scheduler.TickScheduler;
import com.aprism.api.scheduler.TickSide;

/**
 * Drives the {@link TickScheduler} from real game-tick events
 * (v26.5-Alpha.6).
 *
 * <p>The v26.3-Alpha.9 tick scheduler is a passive surface: it exposes
 * {@code runTick(side, tickNumber)} but nothing calls it. This driver
 * registers as a {@link GameTickEvent} listener on the shared
 * {@link AprismEventBus}; when the game-event dispatcher fires a tick
 * (from the v26.5-Alpha.3 method hooks into MC's game loop), this driver
 * calls {@code runTick} on the appropriate side.
 *
 * <p>The side mapping is configurable: the platform adapter layer tells the
 * driver which {@link TickSide} is active (CLIENT or SERVER) via
 * {@link #setActiveSide(TickSide)}. A null active side means the driver is
 * not yet attached and ticks are dropped.
 *
 * <p>All driving is fail-safe: a throwing task is caught by
 * {@link TickScheduler#runTick} (which isolates per-task failures), so a
 * faulty scheduled task never crashes the game loop.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class TickSchedulerDriver {

    private static final Logger LOG = Logger.getLogger("aprism.scheduler");

    private final TickScheduler scheduler;
    private final AprismEventBus eventBus;
    private TickSide activeSide;
    private AprismEventListener<GameTickEvent> listener;

    /**
     * @param scheduler the tick scheduler to drive
     * @param eventBus  the shared event bus to listen on
     */
    public TickSchedulerDriver(TickScheduler scheduler, AprismEventBus eventBus) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (eventBus == null) {
            throw new IllegalArgumentException("eventBus must not be null");
        }
        this.scheduler = scheduler;
        this.eventBus = eventBus;
    }

    /**
     * Sets the active distribution side. When set, every TICK_START event
     * drives {@code runTick(side, tickNumber)} on the active side. When null,
     * tick events are ignored.
     *
     * @param side the active side (CLIENT or SERVER), or null to detach
     */
    public void setActiveSide(TickSide side) {
        this.activeSide = side;
    }

    /**
     * @return the active distribution side, or null when detached
     */
    public TickSide getActiveSide() {
        return activeSide;
    }

    /**
     * Attaches the driver as a game-tick listener on the event bus. After
     * this call, every TICK_START event drives the scheduler on the active
     * side.
     */
    public void attach() {
        if (listener != null) {
            return; // already attached
        }
        listener = event -> {
            if (activeSide == null) {
                return;
            }
            if (event.getStage() == GameTickEvent.Stage.START) {
                try {
                    scheduler.runTick(activeSide, event.getTickNumber());
                } catch (RuntimeException e) {
                    LOG.warning("Tick scheduler drive failed: " + e.getMessage());
                }
            }
        };
        eventBus.register(GameTickEvent.class, listener);
        LOG.info("Tick scheduler driver attached; active side: " + activeSide);
    }

    /**
     * Detaches the driver from the event bus and clears the active side.
     * Called on runtime shutdown.
     */
    public void detach() {
        if (listener != null) {
            eventBus.unregister(GameTickEvent.class, listener);
            listener = null;
        }
        activeSide = null;
    }

    /**
     * @return whether the driver is currently attached to the event bus
     */
    public boolean isAttached() {
        return listener != null;
    }
}
