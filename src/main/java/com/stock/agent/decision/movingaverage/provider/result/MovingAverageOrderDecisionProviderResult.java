package com.stock.agent.decision.movingaverage.provider.result;

import com.stock.agent.decision.movingaverage.provider.identity.MovingAverageOrderDecisionProviderIdentity;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;

import java.util.Objects;

public record MovingAverageOrderDecisionProviderResult(
        MovingAverageOrderDecisionProviderIdentity providerIdentity,
        OrderQuantityProposal proposal
) {
    public MovingAverageOrderDecisionProviderResult {
        Objects.requireNonNull(
                providerIdentity,
                "providerIdentity must not be null."
        );
        Objects.requireNonNull(proposal, "proposal must not be null.");
    }
}
