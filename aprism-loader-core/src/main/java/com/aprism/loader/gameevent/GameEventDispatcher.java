package com.aprism.loader.gameevent;

import com.aprism.api.AprismEventBus;
import com.aprism.api.gameevent.ClientRenderEvent;
import com.aprism.api.gameevent.GameTickEvent;
import com.aprism.api.gameevent.WorldLoadEvent;
import com.aprism.api.gameevent.WorldUnloadEvent;

/**
 * Runtime-side dispatcher for typed game events (v26.3-Alpha.1, QA0 gap #1).
 * The native injector layer (method hooks installed through the low-level
 * API, goal #2) calls the {@code fireXxx} methods from inside the running
 * game; the dispatcher translates them into typed events posted on the
 * shared {@link AprismEventBus} that mods subscribe to.
 *
 * <p>The dispatcher owns the tick and frame counters and is fail-safe: a
 * throwing listener never propagates back into the game loop. Events posted
 * before {@link #setAttached(boolean) attachment} are dropped, so hooks that
 * fire during early boot (before mods finish loading) cannot reach half-
 * initialized listeners.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class GameEventDispatcher {

    private final AprismEventBus eventBus;
    private volatile boolean attached;
    private long tickNumber;
    private long frameNumber;

    /**
     * @param eventBus the shared Aprism event bus to post game events onto
     */
    public GameEventDispatcher(AprismEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Marks the dispatcher attached (hooks are live) or detached (events are
     * dropped). Called by the runtime when mods are ready / on shutdown.
     *
     * @param attached whether the game-event hooks are live
     */
    public void setAttached(boolean attached) {
        this.attached = attached;
    }

    /**
     * @return whether the dispatcher is attached
     */
    public boolean isAttached() {
        return attached;
    }

    /**
     * Fires a tick-start event. Called from the installed tick hook at the
     * start of each game tick.
     *
     * @return true when the event was cancelled by a listener (the injector
     *         should skip the tick's mod-side processing)
     */
    public boolean fireTickStart() {
        if (!attached) {
            return false;
        }
        GameTickEvent event = new GameTickEvent(GameTickEvent.Stage.START, tickNumber);
        postSafely(event);
        return event.isCancelled();
    }

    /**
     * Fires a tick-end event and advances the tick counter. Called from the
     * installed tick hook at the end of each game tick.
     */
    public void fireTickEnd() {
        if (!attached) {
            return;
        }
        postSafely(new GameTickEvent(GameTickEvent.Stage.END, tickNumber));
        tickNumber++;
    }

    /**
     * Fires a client render event and advances the frame counter. Called
     * from the installed render hook once per rendered frame.
     *
     * @param partialTick the interpolation fraction between ticks (0.0-1.0)
     * @return true when the event was cancelled by a listener
     */
    public boolean fireRender(double partialTick) {
        if (!attached) {
            return false;
        }
        ClientRenderEvent event = new ClientRenderEvent(partialTick, frameNumber);
        postSafely(event);
        frameNumber++;
        return event.isCancelled();
    }

    /**
     * Fires a world-load event.
     *
     * @param worldId the world identifier
     */
    public void fireWorldLoad(String worldId) {
        if (!attached) {
            return;
        }
        postSafely(new WorldLoadEvent(worldId));
    }

    /**
     * Fires a world-unload event.
     *
     * @param worldId the world identifier
     */
    public void fireWorldUnload(String worldId) {
        if (!attached) {
            return;
        }
        postSafely(new WorldUnloadEvent(worldId));
    }

    /**
     * @return the number of completed ticks observed so far
     */
    public long getTickNumber() {
        return tickNumber;
    }

    /**
     * @return the number of rendered frames observed so far
     */
    public long getFrameNumber() {
        return frameNumber;
    }

    /**
     * Resets counters and detaches (runtime shutdown).
     */
    public void reset() {
        attached = false;
        tickNumber = 0;
        frameNumber = 0;
    }

    private void postSafely(com.aprism.api.AprismEvent event) {
        try {
            eventBus.post(event);
        } catch (RuntimeException ignored) {
            // Fail-safe: a throwing listener must never crash the game loop.
        }
    }
}
