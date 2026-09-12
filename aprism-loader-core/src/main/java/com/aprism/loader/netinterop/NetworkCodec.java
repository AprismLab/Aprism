package com.aprism.loader.netinterop;

/**
 * Typed codec SPI for one negotiated network channel (v26.9-Alpha.6).
 * Codec implementations own their wire format; the registry owns limits
 * and channel/version policy.
 *
 * @param <T> decoded value type
 * @author BlockConnect@StarsailsClover
 */
public interface NetworkCodec<T> {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** @return stable codec id used in diagnostics and handshake metadata */
    String id();

    /** @param value value to encode @return wire bytes */
    byte[] encode(T value);

    /** @param payload wire bytes @return decoded value */
    T decode(byte[] payload);
}
