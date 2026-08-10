package com.aprism.api.imc;

/**
 * A single inter-mod communication message (Forge/NeoForge InterModComms
 * parity, v26.3-Alpha.7). A message is sent from one mod to another with a
 * string method key and an arbitrary payload; the receiving mod pulls its
 * messages (optionally filtered by method) and owns the payload.
 *
 * <p>The payload is intentionally typed as {@code Object}: Aprism makes no
 * attempt to interpret or serialize it, matching Forge semantics where the
 * sender and receiver agree on the payload contract out of band.
 *
 * @param targetModId the mod the message is addressed to
 * @param methodKey the method identifier the receiver filters on
 * @param senderModId the mod that sent the message
 * @param payload the payload object
 * @author BlockConnect@StarsailsClover
 */
public record ImcMessage(String targetModId, String methodKey, String senderModId, Object payload) {

    /**
     * Canonical compact constructor: validates addressing fields.
     */
    public ImcMessage {
        if (targetModId == null || targetModId.isBlank()) {
            throw new IllegalArgumentException("targetModId must be non-blank");
        }
        if (methodKey == null || methodKey.isBlank()) {
            throw new IllegalArgumentException("methodKey must be non-blank");
        }
        if (senderModId == null || senderModId.isBlank()) {
            throw new IllegalArgumentException("senderModId must be non-blank");
        }
    }
}
