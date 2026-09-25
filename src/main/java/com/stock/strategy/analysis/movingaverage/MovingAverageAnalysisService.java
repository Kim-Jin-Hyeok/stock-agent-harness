package com.stock.strategy.analysis.movingaverage;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicatorCalculator;
import com.stock.strategy.indicator.movingaverage.MovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.policy.StrategyMovingAveragePeriodPolicy;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import com.stock.strategy.signal.movingaverage.MovingAverageTrendEvaluator;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class MovingAverageAnalysisService {
    private final StrategyMovingAveragePeriodPolicy periodPolicy;
    private final MovingAverageIndicatorCalculator indicatorCalculator;
    private final MovingAverageTrendEvaluator trendEvaluator;

    public MovingAverageAnalysisService(
            StrategyMovingAveragePeriodPolicy periodPolicy,
            MovingAverageIndicatorCalculator indicatorCalculator,
            MovingAverageTrendEvaluator trendEvaluator
    ) {
        this.periodPolicy = Objects.requireNonNull(
                periodPolicy,
                "periodPolicy must not be null."
        );
        this.indicatorCalculator = Objects.requireNonNull(
                indicatorCalculator,
                "indicatorCalculator must not be null."
        );
        this.trendEvaluator = Objects.requireNonNull(
                trendEvaluator,
                "trendEvaluator must not be null."
        );
    }

    public MovingAverageAnalysisResult analyze(
            InvestmentStrategyIdentity strategyIdentity,
            DailyPriceHistory history
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Objects.requireNonNull(history, "history must not be null.");

        MovingAveragePeriods periods = periodPolicy.getPeriods(
                strategyIdentity
        );
        Optional<MovingAverageIndicator> indicator =
                indicatorCalculator.calculate(history, periods);
        if (indicator.isEmpty()) {
            return MovingAverageAnalysisResult.insufficientData(
                    strategyIdentity,
                    history.symbol(),
                    periods.longPeriod(),
                    history.bars().size()
            );
        }

        MovingAverageIndicator calculatedIndicator =
                indicator.orElseThrow();
        MovingAverageTrend trend = trendEvaluator.evaluate(
                calculatedIndicator
        );
        return MovingAverageAnalysisResult.analyzed(
                strategyIdentity,
                history.bars().size(),
                calculatedIndicator,
                trend
        );
    }
}
