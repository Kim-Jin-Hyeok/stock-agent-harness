package com.stock.strategy.analysis.swing;

import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;
import com.stock.strategy.analysis.volatility.atr.AverageTrueRangeAnalysisResult;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverage;
import com.stock.strategy.indicator.volatility.atr.AverageTrueRange;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SwingTechnicalAnalysisResultTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 24);

    @Test
    void createsAnalyzedResultWhenAllAnalysesAreAvailable() {
        SwingTechnicalAnalysisResult result =
                SwingTechnicalAnalysisResult.from(
                        analyzedMovingAverage(61),
                        analyzedAverageTrueRange(61)
                );

        assertThat(result.strategyIdentity()).isEqualTo(STRATEGY_IDENTITY);
        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.status())
                .isEqualTo(SwingTechnicalAnalysisStatus.ANALYZED);
        assertThat(result.requiredBarCount()).isEqualTo(61);
        assertThat(result.availableBarCount()).isEqualTo(61);
        assertThat(result.movingAverageAnalysis().status())
                .isEqualTo(MovingAverageAnalysisStatus.ANALYZED);
        assertThat(result.averageTrueRangeAnalysis().averageTrueRange())
                .isEqualTo(averageTrueRange());
    }

    @Test
    void createsInsufficientResultWhenMovingAverageDataIsInsufficient() {
        MovingAverageAnalysisResult movingAverageAnalysis =
                MovingAverageAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        "005930",
                        61,
                        60
                );

        SwingTechnicalAnalysisResult result =
                SwingTechnicalAnalysisResult.from(
                        movingAverageAnalysis,
                        analyzedAverageTrueRange(60)
                );

        assertThat(result.status())
                .isEqualTo(SwingTechnicalAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(result.requiredBarCount()).isEqualTo(61);
        assertThat(result.availableBarCount()).isEqualTo(60);
        assertThat(result.averageTrueRangeAnalysis().averageTrueRange())
                .isNotNull();
    }

    @Test
    void rejectsMismatchedAnalysisIdentity() {
        InvestmentStrategyIdentity otherIdentity =
                new InvestmentStrategyIdentity(
                        "SWING_V2",
                        2,
                        InvestmentHorizon.SWING
                );
        AverageTrueRangeAnalysisResult averageTrueRangeAnalysis =
                AverageTrueRangeAnalysisResult.analyzed(
                        otherIdentity,
                        61,
                        averageTrueRange()
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> SwingTechnicalAnalysisResult.from(
                        analyzedMovingAverage(61),
                        averageTrueRangeAnalysis
                ))
                .withMessage(
                        "strategyIdentity must match analysis results."
                );
    }

    @Test
    void rejectsMismatchedAvailableBarCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SwingTechnicalAnalysisResult.from(
                        analyzedMovingAverage(61),
                        analyzedAverageTrueRange(60)
                ))
                .withMessage(
                        "availableBarCount must match analysis results."
                );
    }

    private MovingAverageAnalysisResult analyzedMovingAverage(
            int availableBarCount
    ) {
        return MovingAverageAnalysisResult.analyzed(
                STRATEGY_IDENTITY,
                availableBarCount,
                movingAverageIndicator(AS_OF_DATE.minusDays(1)),
                MovingAverageTrend.UPTREND,
                movingAverageIndicator(AS_OF_DATE),
                MovingAverageTrend.UPTREND,
                MovingAverageCrossoverSignal.NONE
        );
    }

    private AverageTrueRangeAnalysisResult analyzedAverageTrueRange(
            int availableBarCount
    ) {
        return AverageTrueRangeAnalysisResult.analyzed(
                STRATEGY_IDENTITY,
                availableBarCount,
                averageTrueRange()
        );
    }

    private MovingAverageIndicator movingAverageIndicator(LocalDate date) {
        return new MovingAverageIndicator(
                "005930",
                date,
                movingAverage(20, "71000.00", date),
                movingAverage(60, "70000.00", date)
        );
    }

    private SimpleMovingAverage movingAverage(
            int period,
            String priceKrw,
            LocalDate date
    ) {
        return new SimpleMovingAverage(
                "005930",
                period,
                new BigDecimal(priceKrw),
                date.minusDays(period - 1L),
                date
        );
    }

    private AverageTrueRange averageTrueRange() {
        return new AverageTrueRange(
                "005930",
                14,
                new BigDecimal("1250.50"),
                AS_OF_DATE.minusDays(59),
                AS_OF_DATE
        );
    }
}
