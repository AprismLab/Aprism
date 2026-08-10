package com.aprism.api.ai;

import java.util.List;

/**
 * The adapter seam for local model runtimes (v26.3-Alpha.4, goal #8).
 * <strong>Experimental / reference-only: no production guarantee.</strong>
 *
 * <p>A local adapter bridges an on-device or LAN model server (for example
 * an Ollama-compatible HTTP endpoint) into the {@link AiAssistant} contract.
 * The loader core defines only this seam; concrete adapters ship inside the
 * providing ai-extension so the core never depends on a specific model
 * runtime.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface LocalModelAdapter {

    /**
     * @return the adapter identifier (e.g. {@code ollama}, {@code llama-cpp})
     */
    String adapterId();

    /**
     * @return the model names the local runtime currently exposes
     */
    List<String> listModels();

    /**
     * Runs a completion against the local runtime.
     *
     * @param modelName the model to run (from {@link #listModels()})
     * @param request   the completion request
     * @return the response
     */
    AiResponse generate(String modelName, AiRequest request);

    /**
     * @return true when the local runtime is reachable
     */
    boolean isReachable();
}
