package com.stock.agent.decision.movingaverage.provider.ai.request;

import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.risk.capacity.OrderQuantityCapacity;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MovingAverageOrderDecisionAiRequestFactory {

    public MovingAverageOrderDecisionAiRequest create(
            MovingAverageOrderDecisionContext context
    ) {
        Objects.requireNonNull(context, "context must not be null.");

        InvestmentStrategyIdentity strategy = context.strategyIdentity();
        MovingAverageAnalysisResult analysis = context.analysisResult();
        MovingAverageIndicator indicator = analysis.indicator();
        OrderQuantityCapacity capacity = context.quantityCapacity();

        return new MovingAverageOrderDecisionAiRequest(
                strategy.strategyId(),
                strategy.strategyVersion(),
                strategy.horizon(),
                context.signalAction(),
                context.symbol(),
                context.currentPriceSnapshot().priceKrw(),
                context.currentPriceSource(),
                context.currentPriceSnapshot().observedAt(),
                context.portfolioSnapshot().cashAmountKrw(),
                context.portfolioSnapshot().totalAssetAmountKrw(),
                capacity.currentPositionQuantity(),
                analysis.previousTrend(),
                analysis.trend(),
                analysis.crossoverSignal(),
                indicator.shortMovingAverage().period(),
                indicator.shortMovingAverage().averagePriceKrw(),
                indicator.longMovingAverage().period(),
                indicator.longMovingAverage().averagePriceKrw(),
                indicator.asOfTradingDate(),
                capacity.maxAffordableQuantity(),
                capacity.maxOrderRatioQuantity(),
                capacity.maxPositionRatioQuantity(),
                capacity.maxAllowedQuantity(),
                capacity.oneSharePortfolioRatio()
        );
    }
}
