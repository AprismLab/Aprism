package com.aprism.loader.contentbind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Single discovery seam for live per-world game instances
 * (v26.7-Alpha.5). Resolves - by reflection against official 26.x names -
 * the objects that earlier binders could not reach statically:
 *
 * <ul>
 *   <li>{@code Minecraft.getInstance()} - the client singleton</li>
 *   <li>{@code LocalPlayer#getConnection()} - the client packet listener
 *       whose {@code send(Packet)} carries custom payloads</li>
 *   <li>{@code Minecraft#getSingleplayerServer()} then
 *       {@code MinecraftServer#getCommands()} then {@code #getDispatcher()}
 *       - the live integrated-server Brigadier dispatcher</li>
 * </ul>
 *
 * <p>All accessors return null instead of throwing when the game has not
 * reached that lifecycle point yet (main menu = no player; no integrated
 * server = single-player absent).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LiveInstanceDiscovery {
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private LiveInstanceDiscovery() {
    }

    /** @return the live client instance, or null outside a client JVM. */
    public static Object clientInstance() {
        try {
            Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            return mc.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** @return the client packet listener, or null before world join. */
    public static Object clientPacketListener() {
        try {
            Object player = fieldValue(clientInstance(), "player");
            if (player == null) {
                return null;
            }
            Method getConnection = findMethod(player.getClass(), "getConnection");
            if (getConnection != null) {
                getConnection.setAccessible(true);
                return getConnection.invoke(player);
            }
            return fieldValue(player, "connection");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Sends a constructed payload to the connected server through the live
     * client listener.
     *
     * @param mcPayload a payload built by {@link NetworkStackAdapter}
     * @return true when the packet was handed to the connection
     */
    public static boolean sendToServer(NetworkStackAdapter.McPayload mcPayload) {
        try {
            Class<?> packetType = Class.forName(
                    "net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket");
            Object packet = packetType.getConstructor(
                    mcPayload.payload().getClass()).newInstance(mcPayload.payload());
            Object listener = clientPacketListener();
            if (listener == null) {
                return false;
            }
            findMethod(listener.getClass(), "send", 1).invoke(listener, packet);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * @return the live integrated-server Brigadier dispatcher, or null when
     *         no single-player world is running.
     */
    public static Object integratedCommandDispatcher() {
        try {
            Object server = methodValue(clientInstance(), "getSingleplayerServer");
            if (server == null) {
                return null;
            }
            Object commands = methodValue(server, "getCommands");
            return commands == null ? null : methodValue(commands, "getDispatcher");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Object fieldValue(Object target, String name)
            throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        for (Class<?> c = target.getClass(); c != null && c != Object.class;
                c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // walk up
            }
        }
        return null;
    }

    private static Object methodValue(Object target, String name)
            throws RuntimeException {
        try {
            Method m = findMethod(target.getClass(), name);
            return m == null || target == null ? null : m.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, int paramCount) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                    return m;
                }
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    return m;
                }
            }
        }
        return null;
    }
}
