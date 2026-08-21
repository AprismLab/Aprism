package com.aprism.loader.networking;

import java.util.logging.Logger;

import com.aprism.api.networking.NetworkDirection;
import com.aprism.api.networking.NetworkPacket;
import com.aprism.api.networking.PacketChannel;

/**
 * Bridges the MC network stack into the {@link NetworkingRegistry}
 * (v26.5-Alpha.8).
 *
 * <p>The v26.3-Alpha.3 networking registry has a {@code NetworkTransport}
 * seam (for outbound sends) and a {@code deliver} method (for inbound
 * packets), but nothing connects them to the real game network stack. This
 * installer provides the platform adapter layer with a single entry point to:
 * <ul>
 *   <li>Attach a {@code NetworkTransport} (outbound send bridge) to the
 *       registry.</li>
 *   <li>Deliver inbound packets from the MC network stack into the registry's
 *       listener dispatch via {@link #deliverInbound}.</li>
 * </ul>
 *
 * <p>The platform adapter layer (which knows the running MC version's network
 * API) calls {@code setTransport()} to attach the outbound bridge, and calls
 * {@code deliverInbound()} whenever a registered channel's packet arrives from
 * the network. The loader core never references MC network classes directly.
 *
 * <p>All operations are fail-safe: a throwing listener is caught by
 * {@link NetworkingRegistry#deliver} (per-listener isolation); a throwing
 * transport is caught by the registry's send method (fail-closed refusal).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class NetworkTransportInstaller {

    private static final Logger LOG = Logger.getLogger("aprism.networking");

    private final NetworkingRegistry registry;

    /**
     * @param registry the networking registry to bridge
     */
    public NetworkTransportInstaller(NetworkingRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.registry = registry;
    }

    /**
     * Attaches a platform-supplied network transport for outbound sends.
     * After this call, {@code registry.send()} delegates to the transport.
     * Pass null to detach.
     *
     * @param transport the outbound transport, or null to detach
     */
    public void setTransport(com.aprism.api.networking.NetworkTransport transport) {
        registry.attachTransport(transport);
        if (transport != null) {
            LOG.info("Network transport attached: " + transport.getClass().getName());
        } else {
            LOG.info("Network transport detached");
        }
    }

    /**
     * @return whether a transport is currently attached
     */
    public boolean isTransportAttached() {
        return registry.getTransport() != null;
    }

    /**
     * Delivers an inbound packet from the MC network stack to all registered
     * listeners on the packet's channel. Called by the platform adapter layer
     * when a registered channel's packet arrives from the network.
     *
     * <p>If the channel is not registered, the packet is silently dropped
     * (no error, matching the registry's design). If no transport is attached,
     * delivery still works (inbound is independent of outbound transport).
     *
     * @param channel the packet channel id (namespace:name)
     * @param payload the raw packet payload bytes
     * @param direction the network direction (CLIENT_TO_SERVER or SERVER_TO_CLIENT)
     */
    public void deliverInbound(String channel, byte[] payload, NetworkDirection direction) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        PacketChannel ch;
        try {
            ch = PacketChannel.of(channel);
        } catch (IllegalArgumentException e) {
            return; // malformed channel id; silently drop
        }
        NetworkPacket packet = new NetworkPacket(ch, payload == null ? new byte[0] : payload);
        registry.deliver(packet, direction);
    }
}
