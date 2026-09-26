package com.stock.agent.decision.movingaverage.validation;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.risk.capacity.OrderQuantityCapacity;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MovingAverageOrderProposalValidator {

    public MovingAverageOrderProposalValidationResult validate(
            MovingAverageOrderDecisionContext context,
            OrderQuantityProposal proposal
    ) {
        Objects.requireNonNull(context, "context must not be null.");

        if (proposal == null) {
            return MovingAverageOrderProposalValidationResult.invalid(
                    null,
                    MovingAverageOrderProposalValidationReasonCode
                            .PROPOSAL_MISSING,
                    "Order quantity proposal is missing."
            );
        }
        if (proposal.intent() == OrderDecisionIntent.HOLD) {
            return MovingAverageOrderProposalValidationResult.valid(
                    proposal.intent()
            );
        }
        if (context.signalAction() == InvestmentAction.HOLD) {
            return MovingAverageOrderProposalValidationResult.invalid(
                    proposal.intent(),
                    MovingAverageOrderProposalValidationReasonCode
                            .SIGNAL_ACTION_DOES_NOT_ALLOW_ORDER,
                    "Order execution is not allowed for HOLD signal action."
            );
        }

        OrderQuantityCapacity capacity = context.quantityCapacity();
        if (!capacity.canOrder()) {
            return MovingAverageOrderProposalValidationResult.invalid(
                    proposal.intent(),
                    MovingAverageOrderProposalValidationReasonCode
                            .ORDER_CAPACITY_UNAVAILABLE,
                    "Order capacity is unavailable. action="
                            + context.signalAction()
                            + ", symbol="
                            + context.symbol()
            );
        }
        if (proposal.quantity() > capacity.maxAllowedQuantity()) {
            return MovingAverageOrderProposalValidationResult.invalid(
                    proposal.intent(),
                    MovingAverageOrderProposalValidationReasonCode
                            .QUANTITY_EXCEEDS_ALLOWED_CAPACITY,
                    "Proposed quantity exceeds allowed capacity. "
                            + "proposedQuantity="
                            + proposal.quantity()
                            + ", maxAllowedQuantity="
                            + capacity.maxAllowedQuantity()
            );
        }

        return MovingAverageOrderProposalValidationResult.valid(
                proposal.intent()
        );
    }
}
