package com.stock.harness.persistence;

import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;

import java.util.Objects;

public record HarnessSwingV1DecisionEvidenceSnapshot(
        SwingTechnicalAnalysisResult analysis,
        CurrentPriceSnapshot currentPrice,
        CurrentPriceLookupSource currentPriceSource,
        PortfolioValuationSnapshot portfolioValuation,
        SwingV1ActionPolicyResult actionPolicyResult,
        SwingV1OrderQuantityResult orderQuantityResult
) {
    public HarnessSwingV1DecisionEvidenceSnapshot {
        Objects.requireNonNull(analysis, "analysis must not be null.");
        Objects.requireNonNull(
                currentPrice,
                "currentPrice must not be null."
        );
        Objects.requireNonNull(
                currentPriceSource,
                "currentPriceSource must not be null."
        );
        Objects.requireNonNull(
                portfolioValuation,
                "portfolioValuation must not be null."
        );
        Objects.requireNonNull(
                actionPolicyResult,
                "actionPolicyResult must not be null."
        );
        Objects.requireNonNull(
                orderQuantityResult,
                "orderQuantityResult must not be null."
        );

        new SwingV1DecisionEvidence(
                analysis,
                currentPrice,
                currentPriceSource,
                portfolioValuation,
                actionPolicyResult,
                orderQuantityResult
        );
    }

    public static HarnessSwingV1DecisionEvidenceSnapshot from(
            SwingV1DecisionEvidence evidence
    ) {
        Objects.requireNonNull(evidence, "evidence must not be null.");
        return new HarnessSwingV1DecisionEvidenceSnapshot(
                evidence.analysis(),
                evidence.currentPrice(),
                evidence.currentPriceSource(),
                evidence.portfolioValuation(),
                evidence.actionPolicyResult(),
                evidence.orderQuantityResult()
        );
    }
}
