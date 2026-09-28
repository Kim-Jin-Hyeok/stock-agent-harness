package com.stock.agent.provider.ai.openai.response;

import com.stock.agent.AgentNextAction;
import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.tool.HarnessToolRequest;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OpenAiAgentNextActionResponseMapper {

    public AgentNextAction map(OpenAiAgentNextActionResponse response) {
        Objects.requireNonNull(response, "response must not be null.");

        return switch (response.type()) {
            case REQUEST_TOOL -> mapToolRequest(response);
            case FINAL_DECISION -> mapFinalDecision(response);
        };
    }

    private AgentNextAction mapToolRequest(
            OpenAiAgentNextActionResponse response
    ) {
        OpenAiAgentNextActionResponse.ToolRequest toolRequest =
                response.toolRequest();
        if (toolRequest == null) {
            throw new IllegalArgumentException(
                    "OpenAI REQUEST_TOOL response must include toolRequest."
            );
        }
        if (response.investmentDecision() != null) {
            throw new IllegalArgumentException(
                    "OpenAI REQUEST_TOOL response must not include "
                            + "investmentDecision."
            );
        }

        return AgentNextAction.requestTool(new HarnessToolRequest(
                toolRequest.type(),
                toolRequest.symbol()
        ));
    }

    private AgentNextAction mapFinalDecision(
            OpenAiAgentNextActionResponse response
    ) {
        OpenAiAgentNextActionResponse.InvestmentDecision responseDecision =
                response.investmentDecision();
        if (responseDecision == null) {
            throw new IllegalArgumentException(
                    "OpenAI FINAL_DECISION response must include "
                            + "investmentDecision."
            );
        }
        if (response.toolRequest() != null) {
            throw new IllegalArgumentException(
                    "OpenAI FINAL_DECISION response must not include "
                            + "toolRequest."
            );
        }
        validateHoldDecision(responseDecision);

        InvestmentDecision decision = new InvestmentDecision(
                responseDecision.action(),
                responseDecision.symbol(),
                responseDecision.quantity(),
                responseDecision.expectedPriceKrw(),
                responseDecision.reason()
        );
        return AgentNextAction.finalDecision(decision);
    }

    private void validateHoldDecision(
            OpenAiAgentNextActionResponse.InvestmentDecision decision
    ) {
        if (decision.action() == InvestmentAction.HOLD
                && (decision.symbol() != null
                || decision.quantity() != null
                || decision.expectedPriceKrw() != null)) {
            throw new IllegalArgumentException(
                    "OpenAI HOLD decision must not include order fields."
            );
        }
    }
}
