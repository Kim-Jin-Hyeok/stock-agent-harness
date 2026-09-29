package com.stock.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stock.agent.evidence.InvestmentDecisionEvidence;
import com.stock.agent.evidence.movingaverage.MovingAverageDecisionEvidence;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;

public record InvestmentDecision(
        InvestmentAction action,
        String symbol,
        Long quantity,
        Long expectedPriceKrw,
        String reason,
        InvestmentDecisionEvidence evidence
) {
    public InvestmentDecision(
            InvestmentAction action,
            String symbol,
            Long quantity,
            Long expectedPriceKrw,
            String reason
    ) {
        this(
                action,
                symbol,
                quantity,
                expectedPriceKrw,
                reason,
                null
        );
    }

    public Long estimatedOrderAmountKrw() {
        if (InvestmentAction.HOLD.equals(action)
                || quantity == null
                || expectedPriceKrw == null) {
            return 0L;
        }

        return quantity * expectedPriceKrw;
    }

    @Override
    @JsonIgnore
    public InvestmentDecisionEvidence evidence() {
        return evidence;
    }

    @JsonProperty("movingAverageEvidence")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public MovingAverageDecisionEvidence movingAverageEvidence() {
        if (evidence instanceof MovingAverageDecisionEvidence movingAverage) {
            return movingAverage;
        }
        return null;
    }

    @JsonProperty("swingV1Evidence")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public SwingV1DecisionEvidence swingV1Evidence() {
        if (evidence instanceof SwingV1DecisionEvidence swingV1) {
            return swingV1;
        }
        return null;
    }
}
