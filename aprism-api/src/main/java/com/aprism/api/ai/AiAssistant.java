package com.aprism.api.ai;

/**
 * The AI assistant capability contract (v26.3-Alpha.4, goal #8).
 * <strong>Experimental / reference-only: no production guarantee.</strong>
 *
 * <p>An {@code ai-extension} (.aep) provides an implementation of this
 * interface and registers it through the AI registry. Mods interact with AI
 * capabilities only through this contract, never through a concrete
 * provider, so the underlying model (cloud API, local runtime such as an
 * Ollama-compatible server, or a stub) can be swapped without touching mod
 * code.
 *
 * <p>Capability gating: callers must check {@link #isAvailable()} before
 * {@link #complete(AiRequest)}; completing against an unavailable assistant
 * must return a refusal response, never throw into the game.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface AiAssistant {

    /**
     * @return the stable assistant identifier (e.g. the providing extension
     *         id); unique within the AI registry
     */
    String name();

    /**
     * @return the model identifier backing this assistant (for display and
     *         telemetry)
     */
    String model();

    /**
     * @return true when the assistant can currently serve requests (model
     *         reachable, credentials present, not rate-limited)
     */
    boolean isAvailable();

    /**
     * Completes a request. Must never throw into the game: failures are
     * returned as refusal responses.
     *
     * @param request the completion request
     * @return the response (refusal when unavailable or rejected)
     */
    AiResponse complete(AiRequest request);
}
