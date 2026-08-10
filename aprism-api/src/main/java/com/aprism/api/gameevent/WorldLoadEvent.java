package com.aprism.api.gameevent;

import com.aprism.api.AprismPhase;

/**
 * Fired when a world (single-player save or server level) is loaded
 * (v26.3-Alpha.1, QA0 gap #1). Carries the world identifier so mods can
 * scope state per world.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class WorldLoadEvent extends AbstractGameEvent {

    private final String worldId;

    /**
     * @param worldId the world identifier (level/save name)
     */
    public WorldLoadEvent(String worldId) {
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
