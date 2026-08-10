package com.aprism.api.gameevent;

import com.aprism.api.AprismPhase;

/**
 * Fired once per game tick, at the start and at the end of the tick
 * (v26.3-Alpha.1, QA0 gap #1). Mods subscribe to
 * {@code GameTickEvent.class} on the Aprism event bus and branch on
 * {@link #getStage()}. The START stage is cancellable (a cancelled start
 * asks the injector to skip the tick's mod-side processing); the END stage
 * is not.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class GameTickEvent extends AbstractGameEvent {

    /**
     * The tick stage.
     */
    public enum Stage {
        /** Fired before the game processes the tick. Cancellable. */
        START,
        /** Fired after the game processed the tick. Not cancellable. */
        END
    }

    private final Stage stage;
    private final long tickNumber;

    /**
     * @param stage      the tick stage
     * @param tickNumber the monotonically increasing tick counter (0-based)
     */
    public GameTickEvent(Stage stage, long tickNumber) {
        super(AprismPhase.COMPLETE, stage == Stage.START);
        this.stage = stage;
        this.tickNumber = tickNumber;
    }

    /**
     * @return the tick stage
     */
    public Stage getStage() {
        return stage;
    }

    /**
     * @return the monotonically increasing tick counter (0-based)
     */
    public long getTickNumber() {
        return tickNumber;
    }
}
