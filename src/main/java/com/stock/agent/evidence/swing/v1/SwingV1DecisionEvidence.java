package com.stock.agent.evidence.swing.v1;

import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.evidence.InvestmentDecisionEvidence;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.util.Objects;

public record SwingV1DecisionEvidence(
        SwingTechnicalAnalysisResult analysis,
        CurrentPriceSnapshot currentPrice,
        CurrentPriceLookupSource currentPriceSource,
        PortfolioValuationSnapshot portfolioValuation,
        SwingV1ActionPolicyResult actionPolicyResult,
        SwingV1OrderQuantityResult orderQuantityResult
) implements InvestmentDecisionEvidence {
    private static final String STRATEGY_ID = "SWING_V1";
    private static final int STRATEGY_VERSION = 1;

    public SwingV1DecisionEvidence {
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

        validateStrategyIdentity(analysis.strategyIdentity());
        validateCurrentPrice(analysis, currentPrice);
        validateAction(actionPolicyResult, orderQuantityResult);
        validateOrderContext(
                analysis,
                currentPrice,
                portfolioValuation,
                orderQuantityResult
        );
    }

    private static void validateStrategyIdentity(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        if (!STRATEGY_ID.equals(strategyIdentity.strategyId())
                || strategyIdentity.strategyVersion() != STRATEGY_VERSION
                || strategyIdentity.horizon() != InvestmentHorizon.SWING) {
            throw new IllegalArgumentException(
                    "analysis must use SWING_V1 strategy identity."
            );
        }
    }

    private static void validateCurrentPrice(
            SwingTechnicalAnalysisResult analysis,
            CurrentPriceSnapshot currentPrice
    ) {
        if (!analysis.symbol().equals(currentPrice.symbol())) {
            throw new IllegalArgumentException(
                    "currentPrice symbol must match analysis symbol."
            );
        }
        if (currentPrice.priceKrw() <= 0) {
            throw new IllegalArgumentException(
                    "currentPrice priceKrw must be positive."
            );
        }
        Objects.requireNonNull(
                currentPrice.observedAt(),
                "currentPrice observedAt must not be null."
        );
    }

    private static void validateAction(
            SwingV1ActionPolicyResult actionPolicyResult,
            SwingV1OrderQuantityResult orderQuantityResult
    ) {
        if (actionPolicyResult.action() != orderQuantityResult.action()) {
            throw new IllegalArgumentException(
                    "actionPolicyResult action must match "
                            + "orderQuantityResult action."
            );
        }
    }

    private static void validateOrderContext(
            SwingTechnicalAnalysisResult analysis,
            CurrentPriceSnapshot currentPrice,
            PortfolioValuationSnapshot portfolioValuation,
            SwingV1OrderQuantityResult orderQuantityResult
    ) {
        if (!analysis.symbol().equals(orderQuantityResult.symbol())) {
            throw new IllegalArgumentException(
                    "orderQuantityResult symbol must match analysis symbol."
            );
        }
        if (currentPrice.priceKrw()
                != orderQuantityResult.orderCapacity().currentPriceKrw()) {
            throw new IllegalArgumentException(
                    "currentPrice must match order capacity current price."
            );
        }

        long positionQuantity = portfolioValuation.positionQuantity(
                analysis.symbol()
        );
        if (positionQuantity != orderQuantityResult
                .orderCapacity()
                .currentPositionQuantity()) {
            throw new IllegalArgumentException(
                    "Portfolio position quantity must match order capacity."
            );
        }
    }
}
