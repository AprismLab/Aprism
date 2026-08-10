package com.aprism.api.scheduler;

/**
 * Which distribution a scheduled tick task runs on (Fabric
 * {@code ServerTickEvents}/{@code ClientTickEvents} parity, v26.3-Alpha.9).
 *
 * @author BlockConnect@StarsailsClover
 */
public enum TickSide {

    /** Scheduled tasks fired by the client tick loop. */
    CLIENT,

    /** Scheduled tasks fired by the server tick loop. */
    SERVER
}
