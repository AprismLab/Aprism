package com.aprism.loader.modmenu;

/**
 * Lifecycle state of a unit shown in the native Aprism mod list
 * (v26.2-Alpha.2, goal #7). The mod list (and the future in-game mod menu)
 * reports each mod or extension with its current state so users can see at a
 * glance what loaded, what failed, and what was disabled.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum ModListState {

    /** Discovered on disk but not yet loaded. */
    DISCOVERED,

    /** Loaded successfully and participating in the lifecycle. */
    LOADED,

    /** Loading failed; the unit was isolated from the boot. */
    FAILED,

    /** Explicitly disabled by the user (not loaded). */
    DISABLED
}
