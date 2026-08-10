package com.aprism.api.ai;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A completion request sent to an {@link AiAssistant} (v26.3-Alpha.4, goal
 * #8). <strong>Experimental / reference-only: no production guarantee.</strong>
 *
 * @param prompt    the user prompt (never null or blank)
 * @param context   optional context lines appended before the prompt
 * @param maxTokens the maximum tokens to generate (0 = adapter default)
 * @param temperature sampling temperature (0.0 deterministic .. 1.0
 *                    creative; negative = adapter default)
 * @author BlockConnect@StarsailsClover
 */
public record AiRequest(String prompt, List<String> context, int maxTokens, double temperature) {

    /**
     * Validates the prompt.
     */
    public AiRequest {
        Objects.requireNonNull(prompt, "prompt");
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        context = context == null ? List.of() : List.copyOf(context);
    }

    /**
     * Builds a minimal request (no context, adapter defaults).
     *
     * @param prompt the prompt
     * @return the request
     */
    public static AiRequest of(String prompt) {
        return new AiRequest(prompt, List.of(), 0, -1.0);
    }
}
