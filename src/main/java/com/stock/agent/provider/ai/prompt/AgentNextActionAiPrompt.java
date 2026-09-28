package com.stock.agent.provider.ai.prompt;

import com.stock.agent.provider.ai.request.AgentNextActionAiRequest;

import java.util.Objects;

public record AgentNextActionAiPrompt(
        String systemInstruction,
        AgentNextActionAiRequest request
) {
    public AgentNextActionAiPrompt {
        if (systemInstruction == null || systemInstruction.isBlank()) {
            throw new IllegalArgumentException(
                    "systemInstruction must not be blank."
            );
        }
        Objects.requireNonNull(request, "request must not be null.");
    }
}
