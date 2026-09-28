package com.stock.agent.decision.movingaverage.provider.ai.openai.response;

import com.stock.agent.decision.order.proposal.OrderDecisionIntent;

import java.util.Objects;

public record OpenAiMovingAverageOrderDecisionResponse(
        OrderDecisionIntent intent,
        Long quantity,
        String reason
) {
    public OpenAiMovingAverageOrderDecisionResponse {
        Objects.requireNonNull(
                intent,
                "OpenAI response intent must not be null."
        );
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "OpenAI response reason must not be blank."
            );
        }
    }
}
