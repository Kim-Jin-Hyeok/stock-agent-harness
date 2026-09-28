package com.stock.agent.decision.movingaverage.provider.ai.openai.response;

import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OpenAiMovingAverageOrderDecisionResponseMapper {

    public OrderQuantityProposal map(
            OpenAiMovingAverageOrderDecisionResponse response
    ) {
        Objects.requireNonNull(response, "response must not be null.");
        return new OrderQuantityProposal(
                response.intent(),
                response.quantity(),
                response.reason()
        );
    }
}
