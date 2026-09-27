package com.stock.agent.decision.movingaverage.resolution;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.movingaverage.validation.MovingAverageOrderProposalValidationResult;
import com.stock.agent.decision.movingaverage.validation.MovingAverageOrderProposalValidationStatus;
import com.stock.agent.decision.movingaverage.validation.MovingAverageOrderProposalValidator;
import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.agent.evidence.movingaverage.MovingAverageDecisionEvidence;
import com.stock.agent.evidence.movingaverage.order.MovingAverageOrderDecisionEvidence;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MovingAverageOrderDecisionResolver {
    private final MovingAverageOrderProposalValidator proposalValidator;

    public MovingAverageOrderDecisionResolver(
            MovingAverageOrderProposalValidator proposalValidator
    ) {
        this.proposalValidator = Objects.requireNonNull(
                proposalValidator,
                "proposalValidator must not be null."
        );
    }

    public MovingAverageOrderDecisionResolution resolve(
            MovingAverageOrderDecisionContext context,
            OrderQuantityProposal proposal
    ) {
        Objects.requireNonNull(context, "context must not be null.");

        MovingAverageOrderProposalValidationResult validationResult =
                proposalValidator.validate(context, proposal);
        if (validationResult.status()
                == MovingAverageOrderProposalValidationStatus.INVALID) {
            return MovingAverageOrderDecisionResolution.rejected(
                    validationResult
            );
        }

        InvestmentDecision decision = proposal.intent()
                == OrderDecisionIntent.HOLD
                ? holdDecision(context, proposal)
                : orderDecision(context, proposal);
        return MovingAverageOrderDecisionResolution.resolved(
                validationResult,
                decision
        );
    }

    private InvestmentDecision holdDecision(
            MovingAverageOrderDecisionContext context,
            OrderQuantityProposal proposal
    ) {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                proposal.reason(),
                evidence(context, proposal)
        );
    }

    private InvestmentDecision orderDecision(
            MovingAverageOrderDecisionContext context,
            OrderQuantityProposal proposal
    ) {
        return new InvestmentDecision(
                context.signalAction(),
                context.symbol(),
                proposal.quantity(),
                context.currentPriceSnapshot().priceKrw(),
                proposal.reason(),
                evidence(context, proposal)
        );
    }

    private MovingAverageDecisionEvidence evidence(
            MovingAverageOrderDecisionContext context,
            OrderQuantityProposal proposal
    ) {
        return MovingAverageDecisionEvidence.analyzed(
                context.analysisResult(),
                context.currentPriceSnapshot().priceKrw(),
                context.currentPriceSource(),
                new MovingAverageOrderDecisionEvidence(
                        context.signalAction(),
                        context.quantityCapacity(),
                        proposal
                )
        );
    }
}
