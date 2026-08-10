package com.aprism.api.imc;

import java.util.List;

/**
 * Inter-mod communication surface providing Forge/NeoForge
 * {@code InterModComms} parity (v26.3-Alpha.7). Mods exchange one-way
 * messages keyed by a method string; messages are buffered per target mod
 * and drained when the receiver consumes them.
 *
 * <p>Semantics follow Forge conventions:
 * <ul>
 *   <li>{@link #sendTo} is accepted only after the INIT phase has begun
 *       (fail-closed earlier, mirroring Forge's setup-phase window);</li>
 *   <li>{@link #getMessages} drains the recipient's queue — consumed
 *       messages are not delivered again;</li>
 *   <li>the {@code methodKey} filter matches exact method identifiers.</li>
 * </ul>
 *
 * @author BlockConnect@StarsailsClover
 */
public interface InterModComms {

    /**
     * Sends a message from the given sender to the target mod.
     *
     * @param senderModId the sending mod id
     * @param targetModId the receiving mod id
     * @param methodKey the method identifier
     * @param payload the payload object (owned by receiver after delivery)
     * @return {@code true} if the message was accepted into the queue
     * @throws IllegalArgumentException if any addressing field is blank
     * @throws IllegalStateException if sending is attempted before the INIT
     *                               phase
     */
    boolean sendTo(String senderModId, String targetModId, String methodKey, Object payload);

    /**
     * @param targetModId the mod id to check
     * @return whether buffered messages are pending for the target mod
     */
    boolean hasMessages(String targetModId);

    /**
     * Drains and returns all pending messages addressed to the target mod,
     * in send order.
     *
     * @param targetModId the receiving mod id
     * @return the drained messages (possibly empty)
     */
    List<ImcMessage> getMessages(String targetModId);

    /**
     * Drains and returns pending messages addressed to the target mod whose
     * method key matches the filter, in send order. Non-matching messages
     * remain buffered.
     *
     * @param targetModId the receiving mod id
     * @param methodKeyFilter the exact method key to match
     * @return the drained matching messages (possibly empty)
     */
    List<ImcMessage> getMessages(String targetModId, String methodKeyFilter);

    /**
     * Removes all buffered messages. Called by the loader on shutdown so a
     * fresh boot starts with empty queues.
     */
    void clear();
}
