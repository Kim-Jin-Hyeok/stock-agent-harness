package com.stock.agent.decision.movingaverage;

import com.stock.agent.InvestmentAction;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.capacity.OrderQuantityCapacity;
import com.stock.risk.capacity.OrderQuantityCapacityCalculator;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MovingAverageOrderDecisionContextFactory {
    private final MovingAverageActionPolicy actionPolicy;
    private final OrderQuantityCapacityCalculator quantityCapacityCalculator;

    public MovingAverageOrderDecisionContextFactory(
            MovingAverageActionPolicy actionPolicy,
            OrderQuantityCapacityCalculator quantityCapacityCalculator
    ) {
        this.actionPolicy = Objects.requireNonNull(
                actionPolicy,
                "actionPolicy must not be null."
        );
        this.quantityCapacityCalculator = Objects.requireNonNull(
                quantityCapacityCalculator,
                "quantityCapacityCalculator must not be null."
        );
    }

    public MovingAverageOrderDecisionContext create(
            InvestmentStrategyIdentity strategyIdentity,
            PortfolioSnapshot portfolioSnapshot,
            MovingAverageAnalysisResult analysisResult,
            CurrentPriceLookupResult currentPriceLookupResult
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );
        Objects.requireNonNull(
                analysisResult,
                "analysisResult must not be null."
        );
        Objects.requireNonNull(
                currentPriceLookupResult,
                "currentPriceLookupResult must not be null."
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
        CurrentPriceSnapshot currentPriceSnapshot = Objects.requireNonNull(
                currentPriceLookupResult.snapshot(),
                "currentPriceLookupResult.snapshot must not be null."
        );
        CurrentPriceLookupSource currentPriceSource = Objects.requireNonNull(
                currentPriceLookupResult.source(),
                "currentPriceLookupResult.source must not be null."
        );

        InvestmentAction signalAction = actionPolicy.decide(
                analysisResult.crossoverSignal(),
                analysisResult.symbol(),
                portfolioSnapshot
        );
        OrderQuantityCapacity quantityCapacity =
                quantityCapacityCalculator.calculate(
                        signalAction,
                        analysisResult.symbol(),
                        currentPriceSnapshot.priceKrw(),
                        portfolioSnapshot
                );

        return new MovingAverageOrderDecisionContext(
                strategyIdentity,
                signalAction,
                analysisResult.symbol(),
                portfolioSnapshot,
                currentPriceSnapshot,
                currentPriceSource,
                analysisResult,
                quantityCapacity
        );
    }
}
