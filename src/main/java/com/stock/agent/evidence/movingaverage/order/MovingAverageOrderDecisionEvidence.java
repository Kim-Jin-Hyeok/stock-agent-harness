package com.stock.agent.evidence.movingaverage.order;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.provider.identity.MovingAverageOrderDecisionProviderIdentity;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.risk.capacity.OrderQuantityCapacity;

import java.util.Objects;

public record MovingAverageOrderDecisionEvidence(
        InvestmentAction signalAction,
        OrderQuantityCapacity quantityCapacity,
        MovingAverageOrderDecisionProviderIdentity providerIdentity,
        OrderQuantityProposal proposal
) {
    public MovingAverageOrderDecisionEvidence {
        Objects.requireNonNull(
                signalAction,
                "signalAction must not be null."
        );
        Objects.requireNonNull(
                quantityCapacity,
                "quantityCapacity must not be null."
        );
        Objects.requireNonNull(
                providerIdentity,
                "providerIdentity must not be null."
        );
        Objects.requireNonNull(proposal, "proposal must not be null.");

        if (signalAction != quantityCapacity.action()) {
            throw new IllegalArgumentException(
                    "signalAction must match quantityCapacity action."
            );
        }
    }
}
