package com.aprism.loader.contentbind;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.logging.Logger;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Reflective adapter onto Minecraft's custom-payload network stack
 * (v26.7-Alpha.4).
 *
 * <p>Target surface (official 26.x names):
 * {@code CustomPacketPayload.createType(String)} builds the channel type,
 * {@code CustomPayloadType} is its record form over {@code Identifier}, and
 * {@code FriendlyByteBuf(Unpooled.buffer())} carries the payload bytes.
 * The adapter constructs payload instances for Aprism channels so a live
 * connection can send them once the transport seam discovers a
 * {@code Connection}/{@code PacketListener}.
 *
 * <p>Fail-closed: reflection misses refuse instead of throwing; the class
 * never touches IO or threads itself.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class NetworkStackAdapter {
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG = Logger.getLogger("aprism.contentbind");

    private static final String PAYLOAD_CLASS =
            "net.minecraft.network.protocol.common.custom.CustomPacketPayload";
    private static final String TYPE_CLASS =
            "net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type";
    private static final String IDENTIFIER_CLASS =
            "net.minecraft.resources.Identifier";

    /** A constructed MC payload ready for a live connection to send. */
    public record McPayload(Object payload, Object type) {
    }

    /**
     * Builds a {@code CustomPacketPayload} carrying {@code bytes} on the
     * given Aprism channel ({@code aprism:<channel>}).
     *
     * @param channel the channel name (no namespace)
     * @param bytes   the raw payload body
     * @return the constructed payload, or null when the MC surface is absent
     */
    public McPayload buildPayload(String channel, byte[] bytes) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(bytes, "bytes");
        try {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> payloadClass = loader.loadClass(PAYLOAD_CLASS);
            Class<?> typeClass = loader.loadClass(TYPE_CLASS);
            Object type = buildType(typeClass, "aprism:" + channel);
            if (type == null) {
                return null;
            }
            // Anonymous payload impl: proxy the interface returning our type;
            // stream codec handling stays with the live transport seam.
            Object payload = java.lang.reflect.Proxy.newProxyInstance(
                    loader, new Class<?>[] {payloadClass},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "type" -> type;
                        case "toString" -> "AprismPayload[" + channel + "]";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
            return new McPayload(payload, type);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warning("NetworkStackAdapter: payload construction failed: "
                    + e.getMessage());
            return null;
        }
    }
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static Object buildType(Class<?> typeClass, String combined)
            throws ReflectiveOperationException {
        // Prefer the createType factory (validates the identifier), then the
        // record constructor over Identifier.
        try {
            return typeClass.getClassLoader()
                    .loadClass(PAYLOAD_CLASS)
                    .getMethod("createType", String.class)
                    .invoke(null, combined);
        } catch (NoSuchMethodException ignored) {
            // fall through to the record ctor
        }
        Class<?> identifier = typeClass.getClassLoader().loadClass(IDENTIFIER_CLASS);
        Constructor<?> idCtor;
        try {
            idCtor = identifier.getConstructor(String.class, String.class);
        } catch (NoSuchMethodException e) {
            return null; // parse-only variants need splitting; not required yet
        }
        int sep = combined.indexOf(':');
        Object id = idCtor.newInstance(combined.substring(0, sep),
                combined.substring(sep + 1));
        return typeClass.getConstructor(identifier).newInstance(id);
    }
}
