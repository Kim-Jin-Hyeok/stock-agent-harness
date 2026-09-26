package com.stock.agent.decision.movingaverage.resolution;

import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.movingaverage.validation.MovingAverageOrderProposalValidationResult;
import com.stock.agent.decision.movingaverage.validation.MovingAverageOrderProposalValidationStatus;

import java.util.Objects;

public record MovingAverageOrderDecisionResolution(
        MovingAverageOrderProposalValidationResult validationResult,
        InvestmentDecision decision
) {
    public MovingAverageOrderDecisionResolution {
        Objects.requireNonNull(
                validationResult,
                "validationResult must not be null."
        );
        Objects.requireNonNull(
                validationResult.status(),
                "validationResult.status must not be null."
        );

        if (validationResult.status()
                == MovingAverageOrderProposalValidationStatus.VALID
                && decision == null) {
            throw new IllegalArgumentException(
                    "Valid resolution requires a decision."
            );
        }
        if (validationResult.status()
                == MovingAverageOrderProposalValidationStatus.INVALID
                && decision != null) {
            throw new IllegalArgumentException(
                    "Invalid resolution must not contain a decision."
            );
        }
    }

    public static MovingAverageOrderDecisionResolution resolved(
            MovingAverageOrderProposalValidationResult validationResult,
            InvestmentDecision decision
    ) {
        return new MovingAverageOrderDecisionResolution(
                validationResult,
                decision
        );
    }

    public static MovingAverageOrderDecisionResolution rejected(
            MovingAverageOrderProposalValidationResult validationResult
    ) {
        return new MovingAverageOrderDecisionResolution(
                validationResult,
                null
        );
    }

    public boolean isResolved() {
        return decision != null;
    }
}
