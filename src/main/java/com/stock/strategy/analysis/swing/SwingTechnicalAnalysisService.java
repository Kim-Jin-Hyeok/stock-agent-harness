package com.stock.strategy.analysis.swing;

import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import com.stock.strategy.analysis.volatility.atr.AverageTrueRangeAnalysisResult;
import com.stock.strategy.analysis.volatility.atr.AverageTrueRangeAnalysisService;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SwingTechnicalAnalysisService {
    private final MovingAverageAnalysisService movingAverageAnalysisService;
    private final AverageTrueRangeAnalysisService averageTrueRangeAnalysisService;

    public SwingTechnicalAnalysisService(
            MovingAverageAnalysisService movingAverageAnalysisService,
            AverageTrueRangeAnalysisService averageTrueRangeAnalysisService
    ) {
        this.movingAverageAnalysisService = Objects.requireNonNull(
                movingAverageAnalysisService,
                "movingAverageAnalysisService must not be null."
        );
        this.averageTrueRangeAnalysisService = Objects.requireNonNull(
                averageTrueRangeAnalysisService,
                "averageTrueRangeAnalysisService must not be null."
        );
    }

    public SwingTechnicalAnalysisResult analyze(
            InvestmentStrategyIdentity strategyIdentity,
            DailyPriceHistory history
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Objects.requireNonNull(history, "history must not be null.");
        if (strategyIdentity.horizon() != InvestmentHorizon.SWING) {
            throw new IllegalArgumentException(
                    "strategyIdentity horizon must be SWING."
            );
        }

        MovingAverageAnalysisResult movingAverageAnalysis =
                movingAverageAnalysisService.analyze(
                        strategyIdentity,
                        history
                );
        AverageTrueRangeAnalysisResult averageTrueRangeAnalysis =
                averageTrueRangeAnalysisService.analyze(
                        strategyIdentity,
                        history
                );
        return SwingTechnicalAnalysisResult.from(
                movingAverageAnalysis,
                averageTrueRangeAnalysis
        );
    }
}
