package com.aprism.api;

/**
 * Enumeration of the lifecycle phases recognized by the Aprism loader. Phases
 * are dispatched in a strict order, with the side-specific {@link #CLIENT} and
 * {@link #SERVER} phases dispatched only on the corresponding distribution.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum AprismPhase {

    /** Pre-initialization: manifests parsed, classes being discovered. */
    PREINIT("Pre-initialization phase"),

    /** Initialization: mods register content and subscribe to events. */
    INIT("Initialization phase"),

    /** Setup: post-registration wiring and cross-mod integration. */
    SETUP("Setup phase"),

    /** Completion: all mods initialized, ready for the game tick loop. */
    COMPLETE("Completion phase"),

    /** Client-only phase, dispatched on the client distribution. */
    CLIENT("Client phase"),

    /** Server-only phase, dispatched on the dedicated server distribution. */
    SERVER("Server phase");

    private final String description;

    AprismPhase(String description) {
        this.description = description;
    }

    /**
     * @return a human-readable description of this phase
     */
    public String getDescription() {
        return description;
    }
}
