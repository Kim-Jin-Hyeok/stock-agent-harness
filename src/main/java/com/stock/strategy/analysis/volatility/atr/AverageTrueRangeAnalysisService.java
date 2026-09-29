package com.stock.strategy.analysis.volatility.atr;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.indicator.volatility.atr.AverageTrueRange;
import com.stock.strategy.indicator.volatility.atr.WilderAverageTrueRangeCalculator;
import com.stock.strategy.indicator.volatility.atr.policy.StrategyAverageTrueRangePeriodPolicy;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AverageTrueRangeAnalysisService {
    private final StrategyAverageTrueRangePeriodPolicy periodPolicy;
    private final WilderAverageTrueRangeCalculator calculator;

    public AverageTrueRangeAnalysisService(
            StrategyAverageTrueRangePeriodPolicy periodPolicy,
            WilderAverageTrueRangeCalculator calculator
    ) {
        this.periodPolicy = Objects.requireNonNull(
                periodPolicy,
                "periodPolicy must not be null."
        );
        this.calculator = Objects.requireNonNull(
                calculator,
                "calculator must not be null."
        );
    }

    public AverageTrueRangeAnalysisResult analyze(
            InvestmentStrategyIdentity strategyIdentity,
            DailyPriceHistory history
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Objects.requireNonNull(history, "history must not be null.");

        int period = periodPolicy.getPeriod(strategyIdentity);
        int requiredBarCount = Math.addExact(period, 1);
        int availableBarCount = history.bars().size();
        if (availableBarCount < requiredBarCount) {
            return AverageTrueRangeAnalysisResult.insufficientData(
                    strategyIdentity,
                    history.symbol(),
                    requiredBarCount,
                    availableBarCount
            );
        }

        AverageTrueRange averageTrueRange = calculator
                .calculate(history, period)
                .orElseThrow(() -> new IllegalStateException(
                        "Average true range must be available."
                ));
        return AverageTrueRangeAnalysisResult.analyzed(
                strategyIdentity,
                availableBarCount,
                averageTrueRange
        );
    }
}
