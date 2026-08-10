package com.aprism.api.networking;

/**
 * The transport seam for Aprism networking (v26.3-Alpha.3, QA0 gap #4). A
 * transport knows how to move raw packet bytes across the actual game
 * connection (vanilla custom payload, a mod protocol, or a test loopback).
 * The loader core ships no real transport: when none is attached, the
 * networking registry is fail-closed (sends are refused, never silently
 * dropped into a void).
 *
 * @author BlockConnect@StarsailsClover
 */
public interface NetworkTransport {

    /**
     * @return true when the transport can currently move packets (e.g. the
     *         game connection is established)
     */
    boolean isAvailable();

    /**
     * Sends a packet in the given direction.
     *
     * @param packet    the packet to send
     * @param direction the travel direction
     * @return true when the transport accepted the packet for delivery
     */
    boolean send(NetworkPacket packet, NetworkDirection direction);

    /**
     * Delivers an inbound packet to the registry's listeners. Implemented
     * by the registry; transports call this when bytes arrive from the
     * wire.
     *
     * @param packet    the received packet
     * @param direction the direction the packet travelled
     */
    void deliver(NetworkPacket packet, NetworkDirection direction);
}
