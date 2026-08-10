package com.aprism.loader.imc;

import com.aprism.api.imc.ImcMessage;
import com.aprism.api.imc.InterModComms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe implementation of {@link InterModComms} with phase gating
 * (v26.3-Alpha.7, Forge/NeoForge InterModComms parity).
 *
 * <p>Messages are buffered per target mod in a {@link ConcurrentLinkedQueue}
 * and drained on consumption. Sending is fail-closed before the runtime has
 * entered the INIT phase: {@link #sendTo} throws {@link IllegalStateException}
 * in that window, mirroring Forge's setup-phase contract. Consumption is
 * always allowed once queued.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class InterModCommsImpl implements InterModComms {

    private final Map<String, Queue<ImcMessage>> queues = new ConcurrentHashMap<>();
    private volatile boolean initPhaseReached;

    /**
     * Opens the send window. Called by the runtime when the INIT phase
     * begins; subsequent {@link #sendTo} calls are accepted.
     */
    public void markInitPhaseReached() {
        this.initPhaseReached = true;
    }

    /**
     * @return whether the send window has been opened
     */
    public boolean isSendWindowOpen() {
        return initPhaseReached;
    }

    @Override
    public boolean sendTo(String senderModId, String targetModId, String methodKey, Object payload) {
        if (!initPhaseReached) {
            throw new IllegalStateException(
                    "InterModComms.sendTo before INIT phase: sender=" + senderModId);
        }
        ImcMessage message = new ImcMessage(targetModId, methodKey, senderModId, payload);
        queues.computeIfAbsent(targetModId, k -> new ConcurrentLinkedQueue<>()).add(message);
        return true;
    }

    @Override
    public boolean hasMessages(String targetModId) {
        Queue<ImcMessage> queue = queues.get(targetModId);
        return queue != null && !queue.isEmpty();
    }

    @Override
    public List<ImcMessage> getMessages(String targetModId) {
        Queue<ImcMessage> queue = queues.get(targetModId);
        if (queue == null) {
            return List.of();
        }
        List<ImcMessage> drained = new ArrayList<>();
        ImcMessage message;
        while ((message = queue.poll()) != null) {
            drained.add(message);
        }
        return drained;
    }

    @Override
    public List<ImcMessage> getMessages(String targetModId, String methodKeyFilter) {
        Queue<ImcMessage> queue = queues.get(targetModId);
        if (queue == null || methodKeyFilter == null) {
            return List.of();
        }
        List<ImcMessage> drained = new ArrayList<>();
        List<ImcMessage> retained = new ArrayList<>();
        ImcMessage message;
        while ((message = queue.poll()) != null) {
            if (methodKeyFilter.equals(message.methodKey())) {
                drained.add(message);
            } else {
                retained.add(message);
            }
        }
        queue.addAll(retained);
        return drained;
    }

    @Override
    public void clear() {
        queues.clear();
        initPhaseReached = false;
    }
}
