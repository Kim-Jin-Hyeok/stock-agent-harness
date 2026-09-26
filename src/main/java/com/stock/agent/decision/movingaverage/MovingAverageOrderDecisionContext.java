package com.stock.agent.decision.movingaverage;

import com.stock.agent.InvestmentAction;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.capacity.OrderQuantityCapacity;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.util.Objects;

public record MovingAverageOrderDecisionContext(
        InvestmentStrategyIdentity strategyIdentity,
        InvestmentAction signalAction,
        String symbol,
        PortfolioSnapshot portfolioSnapshot,
        CurrentPriceSnapshot currentPriceSnapshot,
        CurrentPriceLookupSource currentPriceSource,
        MovingAverageAnalysisResult analysisResult,
        OrderQuantityCapacity quantityCapacity
) {
    public MovingAverageOrderDecisionContext {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Objects.requireNonNull(
                signalAction,
                "signalAction must not be null."
        );
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );
        Objects.requireNonNull(
                currentPriceSnapshot,
                "currentPriceSnapshot must not be null."
        );
        Objects.requireNonNull(
                currentPriceSnapshot.observedAt(),
                "currentPriceSnapshot.observedAt must not be null."
        );
        Objects.requireNonNull(
                currentPriceSource,
                "currentPriceSource must not be null."
        );
        Objects.requireNonNull(
                analysisResult,
                "analysisResult must not be null."
        );
        Objects.requireNonNull(
                quantityCapacity,
                "quantityCapacity must not be null."
        );

        if (analysisResult.status()
                != MovingAverageAnalysisStatus.ANALYZED) {
            throw new IllegalArgumentException(
                    "analysisResult must have ANALYZED status."
            );
        }
        if (!strategyIdentity.equals(analysisResult.strategyIdentity())) {
            throw new IllegalArgumentException(
                    "strategyIdentity must match analysisResult."
            );
        }
        if (!symbol.equals(analysisResult.symbol())) {
            throw new IllegalArgumentException(
                    "symbol must match analysisResult."
            );
        }
        if (!symbol.equals(currentPriceSnapshot.symbol())) {
            throw new IllegalArgumentException(
                    "symbol must match currentPriceSnapshot."
            );
        }
        if (!symbol.equals(quantityCapacity.symbol())) {
            throw new IllegalArgumentException(
                    "symbol must match quantityCapacity."
            );
        }
        if (signalAction != quantityCapacity.action()) {
            throw new IllegalArgumentException(
                    "signalAction must match quantityCapacity action."
            );
        }
        if (currentPriceSnapshot.priceKrw()
                != quantityCapacity.currentPriceKrw()) {
            throw new IllegalArgumentException(
                    "Current price must match quantityCapacity."
            );
        }
    }
}
