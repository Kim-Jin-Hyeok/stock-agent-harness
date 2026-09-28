package com.stock.agent.provider.ai.openai.response;

import com.stock.agent.AgentNextActionType;
import com.stock.agent.InvestmentAction;
import com.stock.harness.tool.HarnessToolType;

import java.util.Objects;

public record OpenAiAgentNextActionResponse(
        AgentNextActionType type,
        ToolRequest toolRequest,
        InvestmentDecision investmentDecision
) {
    public OpenAiAgentNextActionResponse {
        Objects.requireNonNull(
                type,
                "OpenAI agent action type must not be null."
        );
    }

    public record ToolRequest(
            HarnessToolType type,
            String symbol
    ) {
        public ToolRequest {
            Objects.requireNonNull(
                    type,
                    "OpenAI tool request type must not be null."
            );
        }
    }

    public record InvestmentDecision(
            InvestmentAction action,
            String symbol,
            Long quantity,
            Long expectedPriceKrw,
            String reason
    ) {
        public InvestmentDecision {
            Objects.requireNonNull(
                    action,
                    "OpenAI investment action must not be null."
            );
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "OpenAI investment decision reason must not be blank."
                );
            }
        }
    }
}
