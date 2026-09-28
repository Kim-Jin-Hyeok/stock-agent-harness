package com.stock.harness.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import com.stock.agent.evidence.movingaverage.order.MovingAverageOrderDecisionEvidence;
import com.stock.risk.capacity.OrderQuantityCapacity;

import java.math.BigDecimal;
import java.util.Objects;

public record HarnessMovingAverageOrderDecisionEvidenceSnapshot(
        InvestmentAction signalAction,
        long currentPositionQuantity,
        long maxAffordableQuantity,
        long maxOrderRatioQuantity,
        long maxPositionRatioQuantity,
        long maxAllowedQuantity,
        BigDecimal oneSharePortfolioRatio,
        OrderDecisionIntent proposalIntent,
        Long proposedQuantity,
        String proposalReason,
        String providerId,
        Integer providerVersion
) {
    public HarnessMovingAverageOrderDecisionEvidenceSnapshot(
            InvestmentAction signalAction,
            long currentPositionQuantity,
            long maxAffordableQuantity,
            long maxOrderRatioQuantity,
            long maxPositionRatioQuantity,
            long maxAllowedQuantity,
            BigDecimal oneSharePortfolioRatio,
            OrderDecisionIntent proposalIntent,
            Long proposedQuantity,
            String proposalReason
    ) {
        this(
                signalAction,
                currentPositionQuantity,
                maxAffordableQuantity,
                maxOrderRatioQuantity,
                maxPositionRatioQuantity,
                maxAllowedQuantity,
                oneSharePortfolioRatio,
                proposalIntent,
                proposedQuantity,
                proposalReason,
                null,
                null
        );
    }

    public HarnessMovingAverageOrderDecisionEvidenceSnapshot {
        Objects.requireNonNull(
                signalAction,
                "signalAction must not be null."
        );
        Objects.requireNonNull(
                oneSharePortfolioRatio,
                "oneSharePortfolioRatio must not be null."
        );
        Objects.requireNonNull(
                proposalIntent,
                "proposalIntent must not be null."
        );
        if (proposalReason == null || proposalReason.isBlank()) {
            throw new IllegalArgumentException(
                    "proposalReason must not be blank."
            );
        }
        if (currentPositionQuantity < 0
                || maxAffordableQuantity < 0
                || maxOrderRatioQuantity < 0
                || maxPositionRatioQuantity < 0
                || maxAllowedQuantity < 0) {
            throw new IllegalArgumentException(
                    "Order quantity capacities must not be negative."
            );
        }
        if (oneSharePortfolioRatio.signum() < 0) {
            throw new IllegalArgumentException(
                    "oneSharePortfolioRatio must not be negative."
            );
        }
        validateProviderIdentity(providerId, providerVersion);

        switch (proposalIntent) {
            case EXECUTE_ORDER -> validateOrderProposal(
                    signalAction,
                    maxAllowedQuantity,
                    proposedQuantity
            );
            case HOLD -> {
                if (proposedQuantity != null) {
                    throw new IllegalArgumentException(
                            "HOLD proposedQuantity must be null."
                    );
                }
            }
        }
    }

    public static HarnessMovingAverageOrderDecisionEvidenceSnapshot from(
            MovingAverageOrderDecisionEvidence evidence
    ) {
        Objects.requireNonNull(evidence, "evidence must not be null.");
        OrderQuantityCapacity capacity = evidence.quantityCapacity();
        return new HarnessMovingAverageOrderDecisionEvidenceSnapshot(
                evidence.signalAction(),
                capacity.currentPositionQuantity(),
                capacity.maxAffordableQuantity(),
                capacity.maxOrderRatioQuantity(),
                capacity.maxPositionRatioQuantity(),
                capacity.maxAllowedQuantity(),
                capacity.oneSharePortfolioRatio(),
                evidence.proposal().intent(),
                evidence.proposal().quantity(),
                evidence.proposal().reason(),
                evidence.providerIdentity().providerId(),
                evidence.providerIdentity().providerVersion()
        );
    }

    private static void validateProviderIdentity(
            String providerId,
            Integer providerVersion
    ) {
        if (providerId == null && providerVersion == null) {
            return;
        }
        if (providerId == null || providerVersion == null) {
            throw new IllegalArgumentException(
                    "Provider identity fields must be all present or all null."
            );
        }
        if (providerId.isBlank()) {
            throw new IllegalArgumentException(
                    "providerId must not be blank."
            );
        }
        if (providerVersion < 1) {
            throw new IllegalArgumentException(
                    "providerVersion must be at least 1."
            );
        }
    }

    private static void validateOrderProposal(
            InvestmentAction signalAction,
            long maxAllowedQuantity,
            Long proposedQuantity
    ) {
        if (signalAction == InvestmentAction.HOLD) {
            throw new IllegalArgumentException(
                    "HOLD signal must not contain an order proposal."
            );
        }
        if (proposedQuantity == null || proposedQuantity <= 0) {
            throw new IllegalArgumentException(
                    "EXECUTE_ORDER proposedQuantity must be positive."
            );
        }
        if (proposedQuantity > maxAllowedQuantity) {
            throw new IllegalArgumentException(
                    "proposedQuantity must not exceed maxAllowedQuantity."
            );
        }
    }
}
