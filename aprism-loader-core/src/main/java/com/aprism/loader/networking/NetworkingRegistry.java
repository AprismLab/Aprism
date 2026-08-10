package com.aprism.loader.networking;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import com.aprism.api.networking.NetworkDirection;
import com.aprism.api.networking.NetworkListener;
import com.aprism.api.networking.NetworkPacket;
import com.aprism.api.networking.NetworkTransport;
import com.aprism.api.networking.PacketChannel;

/**
 * Registry of packet channels and their listeners (v26.3-Alpha.3, QA0 gap
 * #4). Mods register a {@link PacketChannel}, subscribe listeners to it, and
 * send packets through it. The registry is fail-closed:
 *
 * <ul>
 *   <li>sending on an unregistered channel is refused;</li>
 *   <li>sending while no transport is attached (or the transport is
 *       unavailable) is refused — packets are never silently dropped;</li>
 *   <li>a throwing listener is isolated and never propagates into the
 *       transport.</li>
 * </ul>
 *
 * @author BlockConnect@StarsailsClover
 */
public final class NetworkingRegistry {

    private static final Logger LOG = Logger.getLogger(NetworkingRegistry.class.getName());

    private final Map<String, PacketChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, List<NetworkListener>> listeners = new ConcurrentHashMap<>();
    private volatile NetworkTransport transport;

    /**
     * Registers a channel. Duplicate registration of the same id is refused.
     *
     * @param channel the channel to register
     * @return the registered channel
     * @throws IllegalArgumentException when the channel id is already registered
     */
    public PacketChannel registerChannel(PacketChannel channel) {
        Objects.requireNonNull(channel, "channel");
        if (channels.putIfAbsent(channel.id(), channel) != null) {
            throw new IllegalArgumentException("channel already registered: " + channel.id());
        }
        return channel;
    }

    /**
     * @param channelId the channel id
     * @return true when the channel is registered
     */
    public boolean isChannelRegistered(String channelId) {
        return channels.containsKey(channelId);
    }

    /**
     * @return the ids of all registered channels
     */
    public List<String> getChannelIds() {
        return List.copyOf(channels.keySet());
    }

    /**
     * Subscribes a listener to a channel. The channel must be registered
     * first.
     *
     * @param channelId the channel id
     * @param listener  the listener
     * @throws IllegalArgumentException when the channel is not registered
     */
    public void addListener(String channelId, NetworkListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (!channels.containsKey(channelId)) {
            throw new IllegalArgumentException("channel not registered: " + channelId);
        }
        listeners.computeIfAbsent(channelId, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Removes a listener from a channel.
     *
     * @param channelId the channel id
     * @param listener  the listener
     */
    public void removeListener(String channelId, NetworkListener listener) {
        List<NetworkListener> bucket = listeners.get(channelId);
        if (bucket != null) {
            bucket.remove(listener);
        }
    }

    /**
     * Attaches the transport that moves packets across the real game
     * connection. The registry itself implements the inbound
     * {@code deliver} path and hands it to the transport so wire bytes reach
     * channel listeners.
     *
     * @param transport the transport, or null to detach
     */
    public void attachTransport(NetworkTransport transport) {
        this.transport = transport;
    }

    /**
     * @return the attached transport, or null
     */
    public NetworkTransport getTransport() {
        return transport;
    }

    /**
     * Sends a packet on its channel (fail-closed).
     *
     * @param packet    the packet to send
     * @param direction the travel direction
     * @return true when the transport accepted the packet for delivery
     */
    public boolean send(NetworkPacket packet, NetworkDirection direction) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(direction, "direction");
        if (!channels.containsKey(packet.channelId())) {
            LOG.warning("Refusing to send on unregistered channel: " + packet.channelId());
            return false;
        }
        NetworkTransport current = transport;
        if (current == null) {
            LOG.warning("Refusing to send on " + packet.channelId() + ": no transport attached");
            return false;
        }
        if (!current.isAvailable()) {
            LOG.warning("Refusing to send on " + packet.channelId() + ": transport unavailable");
            return false;
        }
        return current.send(packet, direction);
    }

    /**
     * Delivers an inbound packet to the listeners of its channel. Called by
     * the transport when bytes arrive from the wire. A throwing listener is
     * isolated; delivery to the remaining listeners continues.
     *
     * @param packet    the received packet
     * @param direction the direction the packet travelled
     */
    public void deliver(NetworkPacket packet, NetworkDirection direction) {
        Objects.requireNonNull(packet, "packet");
        List<NetworkListener> bucket = listeners.get(packet.channelId());
        if (bucket == null) {
            return;
        }
        for (NetworkListener listener : bucket) {
            try {
                listener.onPacket(packet, direction);
            } catch (RuntimeException e) {
                LOG.warning("Listener on " + packet.channelId() + " threw: " + e.getMessage());
            }
        }
    }

    /**
     * Drops all channels, listeners, and the transport (runtime shutdown).
     */
    public void clear() {
        channels.clear();
        listeners.clear();
        transport = null;
    }
}
