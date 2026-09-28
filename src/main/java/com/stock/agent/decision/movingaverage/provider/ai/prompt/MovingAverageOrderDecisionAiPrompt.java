package com.stock.agent.decision.movingaverage.provider.ai.prompt;

import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequest;

import java.util.Objects;

public record MovingAverageOrderDecisionAiPrompt(
        String systemInstruction,
        MovingAverageOrderDecisionAiRequest request
) {
    public MovingAverageOrderDecisionAiPrompt {
        if (systemInstruction == null || systemInstruction.isBlank()) {
            throw new IllegalArgumentException(
                    "systemInstruction must not be blank."
            );
        }
        Objects.requireNonNull(request, "request must not be null.");
    }
}
