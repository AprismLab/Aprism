package com.aprism.api.gameevent;

import com.aprism.api.AprismPhase;

/**
 * Fired once per rendered frame on the client distribution
 * (v26.3-Alpha.1, QA0 gap #1). Cancellable: a cancelled render event asks
 * the injector to skip the frame's mod-side overlay processing. The partial
 * tick value interpolates between game ticks for smooth rendering.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ClientRenderEvent extends AbstractGameEvent {

    private final double partialTick;
    private final long frameNumber;

    /**
     * @param partialTick the interpolation fraction between ticks (0.0-1.0)
     * @param frameNumber the monotonically increasing frame counter (0-based)
     */
    public ClientRenderEvent(double partialTick, long frameNumber) {
        super(AprismPhase.CLIENT, true);
        this.partialTick = partialTick;
        this.frameNumber = frameNumber;
    }

    /**
     * @return the interpolation fraction between ticks (0.0-1.0)
     */
    public double getPartialTick() {
        return partialTick;
    }

    /**
     * @return the monotonically increasing frame counter (0-based)
     */
    public long getFrameNumber() {
        return frameNumber;
    }
}
