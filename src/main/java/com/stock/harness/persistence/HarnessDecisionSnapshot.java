package com.stock.harness.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.evidence.InvestmentDecisionEvidence;
import com.stock.agent.evidence.movingaverage.MovingAverageDecisionEvidence;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;

public record HarnessDecisionSnapshot(
        InvestmentAction action,
        String symbol,
        Long quantity,
        Long expectedPriceKrw,
        Long estimatedOrderAmountKrw,
        String reason,
        HarnessMovingAverageEvidenceSnapshot movingAverageEvidence,
        HarnessSwingV1DecisionEvidenceSnapshot swingV1Evidence
) {
    public HarnessDecisionSnapshot(
            InvestmentAction action,
            String symbol,
            Long quantity,
            Long expectedPriceKrw,
            Long estimatedOrderAmountKrw,
            String reason
    ) {
        this(
                action,
                symbol,
                quantity,
                expectedPriceKrw,
                estimatedOrderAmountKrw,
                reason,
                null,
                null
        );
    }

    public HarnessDecisionSnapshot(
            InvestmentAction action,
            String symbol,
            Long quantity,
            Long expectedPriceKrw,
            Long estimatedOrderAmountKrw,
            String reason,
            HarnessMovingAverageEvidenceSnapshot movingAverageEvidence
    ) {
        this(
                action,
                symbol,
                quantity,
                expectedPriceKrw,
                estimatedOrderAmountKrw,
                reason,
                movingAverageEvidence,
                null
        );
    }

    public static HarnessDecisionSnapshot from(InvestmentDecision decision) {
        InvestmentDecisionEvidence evidence = decision.evidence();
        if (evidence != null
                && !(evidence instanceof MovingAverageDecisionEvidence)
                && !(evidence instanceof SwingV1DecisionEvidence)) {
            throw new IllegalArgumentException(
                    "Unsupported investment decision evidence type: "
                            + evidence.getClass().getName()
            );
        }

        return new HarnessDecisionSnapshot(
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                decision.reason(),
                evidence instanceof MovingAverageDecisionEvidence movingAverage
                        ? HarnessMovingAverageEvidenceSnapshot.from(
                                movingAverage
                        )
                        : null,
                evidence instanceof SwingV1DecisionEvidence swingV1
                        ? HarnessSwingV1DecisionEvidenceSnapshot.from(
                                swingV1
                        )
                        : null
        );
    }
}
