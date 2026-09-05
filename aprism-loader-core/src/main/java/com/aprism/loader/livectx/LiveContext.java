package com.aprism.loader.livectx;

/**
 * The live game context model for v26.9-Alpha.3: which side of the game is
 * running and where it is in the play lifecycle. Transitions between states
 * are the reusable binder triggers (roadmap section 23, Alpha.3).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LiveContext {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Which side of the game reported the context. */
    public enum Side {
        CLIENT, SERVER
    }

    /** Play-lifecycle state of one side. */
    public enum State {
        /** Engine bootstrapping, no menus or worlds yet. */
        BOOTSTRAP,
        /** Title/menu screens, no world loaded. */
        MENU,
        /** A world (singleplayer world or server connection) is loaded. */
        IN_WORLD,
        /** World unload in progress. */
        LEAVING,
        /** Engine shutdown; terminal state. */
        SHUTDOWN
    }

    private LiveContext() {
    }
}
