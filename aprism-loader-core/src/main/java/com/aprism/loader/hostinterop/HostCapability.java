package com.aprism.loader.hostinterop;

/**
 * Capabilities a host can offer to the loader (v26.9 roadmap Alpha.7).
 * Hosts are discovered, never assumed: an adapter advertises the set it
 * really implements, and a registration aimed at an absent capability
 * fails closed instead of silently doing nothing.
 *
 * @author BlockConnect@StarsailsClover
 */
public enum HostCapability {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Can register loader-level commands into a live dispatcher. */
    COMMANDS,

    /** Can register key bindings into a live input system. */
    KEY_INPUT,

    /** Can present a settings screen/surface for mod settings. */
    SETTINGS_GUI,

    /** Can be queried for the current screen/widget tree. */
    SCREEN_QUERY,

    /** Can control world lifecycle (join/leave) programmatically. */
    WORLD_CONTROL
}
