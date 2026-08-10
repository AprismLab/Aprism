package com.aprism.api.gameevent;

import com.aprism.api.AprismPhase;

/**
 * Fired when a world (single-player save or server level) is unloaded
 * (v26.3-Alpha.1, QA0 gap #1). Mods should flush per-world state here.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class WorldUnloadEvent extends AbstractGameEvent {

    private final String worldId;

    /**
     * @param worldId the world identifier (level/save name)
     */
    public WorldUnloadEvent(String worldId) {
        super(AprismPhase.COMPLETE, false);
        this.worldId = worldId;
    }

    /**
     * @return the world identifier
     */
    public String getWorldId() {
        return worldId;
    }
}
