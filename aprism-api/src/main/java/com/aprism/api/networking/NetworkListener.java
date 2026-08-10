package com.aprism.api.networking;

/**
 * A listener for packets received on a registered channel
 * (v26.3-Alpha.3, QA0 gap #4). Listeners are registered against a channel
 * id in the networking registry and invoked when the transport delivers a
 * packet for that channel.
 *
 * @author BlockConnect@StarsailsClover
 */
@FunctionalInterface
public interface NetworkListener {

    /**
     * Called when a packet arrives on the registered channel.
     *
     * @param packet    the received packet
     * @param direction the direction the packet travelled
     */
    void onPacket(NetworkPacket packet, NetworkDirection direction);
}
