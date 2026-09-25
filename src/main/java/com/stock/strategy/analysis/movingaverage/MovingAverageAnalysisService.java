package com.stock.strategy.analysis.movingaverage;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicatorCalculator;
import com.stock.strategy.indicator.movingaverage.MovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.policy.StrategyMovingAveragePeriodPolicy;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignalEvaluator;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import com.stock.strategy.signal.movingaverage.MovingAverageTrendEvaluator;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class MovingAverageAnalysisService {
    private final StrategyMovingAveragePeriodPolicy periodPolicy;
    private final MovingAverageIndicatorCalculator indicatorCalculator;
    private final MovingAverageTrendEvaluator trendEvaluator;
    private final MovingAverageCrossoverSignalEvaluator crossoverSignalEvaluator;

    public MovingAverageAnalysisService(
            StrategyMovingAveragePeriodPolicy periodPolicy,
            MovingAverageIndicatorCalculator indicatorCalculator,
            MovingAverageTrendEvaluator trendEvaluator,
            MovingAverageCrossoverSignalEvaluator crossoverSignalEvaluator
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
        this.crossoverSignalEvaluator = Objects.requireNonNull(
                crossoverSignalEvaluator,
                "crossoverSignalEvaluator must not be null."
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
        int requiredBarCount = Math.addExact(periods.longPeriod(), 1);
        if (history.bars().size() < requiredBarCount) {
            return MovingAverageAnalysisResult.insufficientData(
                    strategyIdentity,
                    history.symbol(),
                    requiredBarCount,
                    history.bars().size()
            );
        }

        MovingAverageIndicator calculatedIndicator = indicatorCalculator
                .calculate(history, periods)
                .orElseThrow(() -> new IllegalStateException(
                        "Current moving average indicator must be available."
                ));
        DailyPriceHistory previousHistory = new DailyPriceHistory(
                history.symbol(),
                history.bars().subList(0, history.bars().size() - 1)
        );
        MovingAverageIndicator previousIndicator = indicatorCalculator
                .calculate(previousHistory, periods)
                .orElseThrow(() -> new IllegalStateException(
                        "Previous moving average indicator must be available."
                ));
        MovingAverageTrend previousTrend = trendEvaluator.evaluate(
                previousIndicator
        );
        MovingAverageTrend trend = trendEvaluator.evaluate(
                calculatedIndicator
        );
        MovingAverageCrossoverSignal crossoverSignal =
                crossoverSignalEvaluator.evaluate(previousTrend, trend);
        return MovingAverageAnalysisResult.analyzed(
                strategyIdentity,
                history.bars().size(),
                previousIndicator,
                previousTrend,
                calculatedIndicator,
                trend,
                crossoverSignal
        );
    }
}
