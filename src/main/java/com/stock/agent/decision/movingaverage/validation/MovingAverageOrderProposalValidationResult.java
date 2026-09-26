package com.stock.agent.decision.movingaverage.validation;

import com.stock.agent.decision.order.proposal.OrderDecisionIntent;

public record MovingAverageOrderProposalValidationResult(
        MovingAverageOrderProposalValidationStatus status,
        OrderDecisionIntent intent,
        MovingAverageOrderProposalValidationReasonCode reasonCode,
        String reason
) {
    public static MovingAverageOrderProposalValidationResult valid(
            OrderDecisionIntent intent
    ) {
        return new MovingAverageOrderProposalValidationResult(
                MovingAverageOrderProposalValidationStatus.VALID,
                intent,
                MovingAverageOrderProposalValidationReasonCode
                        .ORDER_PROPOSAL_VALID,
                "Order quantity proposal is valid."
        );
    }

    public static MovingAverageOrderProposalValidationResult invalid(
            OrderDecisionIntent intent,
            MovingAverageOrderProposalValidationReasonCode reasonCode,
            String reason
    ) {
        return new MovingAverageOrderProposalValidationResult(
                MovingAverageOrderProposalValidationStatus.INVALID,
                intent,
                reasonCode,
                reason
        );
    }
}
