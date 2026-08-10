package com.aprism.loader.ai;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.aprism.api.ai.AiAssistant;
import com.aprism.api.ai.AiRequest;
import com.aprism.api.ai.AiResponse;

/**
 * Registry of AI assistants (v26.3-Alpha.4, goal #8).
 * <strong>Experimental / reference-only: no production guarantee.</strong>
 *
 * <p>An {@code ai-extension} (.aep) registers its {@link AiAssistant}
 * during {@code onInitialize}; mods then look up assistants by name and
 * complete requests through the capability contract. The registry is
 * capability-gated: {@link #complete(String, AiRequest)} checks
 * {@code isAvailable()} and returns a refusal when the assistant is
 * unavailable, never throwing into the game.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AiRegistry {

    private final Map<String, AiAssistant> assistants = new ConcurrentHashMap<>();

    /**
     * Registers an assistant under its {@link AiAssistant#name()}. Duplicate
     * names are refused.
     *
     * @param assistant the assistant
     * @return the registered assistant
     * @throws IllegalArgumentException when the name is already registered
     */
    public AiAssistant register(AiAssistant assistant) {
        Objects.requireNonNull(assistant, "assistant");
        Objects.requireNonNull(assistant.name(), "assistant.name");
        if (assistants.putIfAbsent(assistant.name(), assistant) != null) {
            throw new IllegalArgumentException("assistant already registered: " + assistant.name());
        }
        return assistant;
    }

    /**
     * @param name the assistant name
     * @return the assistant, or empty when unknown
     */
    public Optional<AiAssistant> get(String name) {
        return Optional.ofNullable(assistants.get(name));
    }

    /**
     * @return the names of all registered assistants
     */
    public List<String> getAssistantNames() {
        return List.copyOf(assistants.keySet());
    }

    /**
     * @return the names of assistants currently reporting available
     */
    public List<String> getAvailableAssistantNames() {
        return assistants.values().stream()
                .filter(AiAssistant::isAvailable)
                .map(AiAssistant::name)
                .toList();
    }

    /**
     * Completes a request against a named assistant (capability-gated).
     *
     * @param assistantName the assistant name
     * @param request       the completion request
     * @return the response; a refusal when the assistant is unknown,
     *         unavailable, or throws
     */
    public AiResponse complete(String assistantName, AiRequest request) {
        AiAssistant assistant = assistants.get(assistantName);
        if (assistant == null) {
            return AiResponse.refused("none", "assistant not registered: " + assistantName);
        }
        if (!assistant.isAvailable()) {
            return AiResponse.refused(assistant.model(), "assistant unavailable");
        }
        try {
            return assistant.complete(request);
        } catch (RuntimeException e) {
            return AiResponse.refused(assistant.model(), "assistant failed: " + e.getMessage());
        }
    }

    /**
     * Drops all assistants (runtime shutdown).
     */
    public void clear() {
        assistants.clear();
    }
}
