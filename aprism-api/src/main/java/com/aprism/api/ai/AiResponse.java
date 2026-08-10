package com.aprism.api.ai;

import java.util.Objects;

/**
 * The result of an {@link AiAssistant} completion (v26.3-Alpha.3, goal #8).
 * <strong>Experimental / reference-only: no production guarantee.</strong>
 *
 * @param text         the generated text (never null; empty when refused)
 * @param model        the model identifier that produced the text
 * @param promptTokens tokens consumed by the prompt (0 when unknown)
 * @param completionTokens tokens generated (0 when unknown)
 * @param finishReason why generation stopped ({@code stop}, {@code length},
 *                     {@code error}; never null)
 * @author BlockConnect@StarsailsClover
 */
public record AiResponse(String text, String model, int promptTokens, int completionTokens,
                         String finishReason) {

    /**
     * Validates required fields.
     */
    public AiResponse {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(model, "model");
        finishReason = finishReason == null ? "stop" : finishReason;
    }

    /**
     * @return true when generation produced usable text
     */
    public boolean isSuccess() {
        return !text.isEmpty() && !"error".equals(finishReason);
    }

    /**
     * Builds a refusal response (assistant unavailable or request rejected).
     *
     * @param model  the model identifier
     * @param reason the refusal reason
     * @return the refusal response
     */
    public static AiResponse refused(String model, String reason) {
        return new AiResponse("", model, 0, 0, "error: " + reason);
    }
}
