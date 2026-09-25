package com.stock.strategy.analysis.movingaverage;

import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverage;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MovingAverageAnalysisResultTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 24);

    @Test
    void createsAnalyzedResult() {
        MovingAverageIndicator indicator = indicator();

        MovingAverageAnalysisResult result =
                MovingAverageAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
                        60,
                        previousIndicator(),
                        MovingAverageTrend.FLAT,
                        indicator,
                        MovingAverageTrend.UPTREND,
                        MovingAverageCrossoverSignal.GOLDEN_CROSS
                );

        assertThat(result.status())
                .isEqualTo(MovingAverageAnalysisStatus.ANALYZED);
        assertThat(result.requiredBarCount()).isEqualTo(21);
        assertThat(result.availableBarCount()).isEqualTo(60);
        assertThat(result.previousIndicator())
                .isEqualTo(previousIndicator());
        assertThat(result.previousTrend()).isEqualTo(MovingAverageTrend.FLAT);
        assertThat(result.indicator()).isEqualTo(indicator);
        assertThat(result.trend()).isEqualTo(MovingAverageTrend.UPTREND);
        assertThat(result.crossoverSignal())
                .isEqualTo(MovingAverageCrossoverSignal.GOLDEN_CROSS);
    }

    @Test
    void createsInsufficientDataResultWithoutAnalysis() {
        MovingAverageAnalysisResult result =
                MovingAverageAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        "005930",
                        21,
                        20
                );

        assertThat(result.status())
                .isEqualTo(MovingAverageAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(result.previousIndicator()).isNull();
        assertThat(result.previousTrend()).isNull();
        assertThat(result.indicator()).isNull();
        assertThat(result.trend()).isNull();
        assertThat(result.crossoverSignal()).isNull();
    }

    @Test
    void rejectsAnalyzedResultWithoutEnoughBars() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovingAverageAnalysisResult(
                        STRATEGY_IDENTITY,
                        "005930",
                        MovingAverageAnalysisStatus.ANALYZED,
                        21,
                        20,
                        previousIndicator(),
                        MovingAverageTrend.FLAT,
                        indicator(),
                        MovingAverageTrend.UPTREND,
                        MovingAverageCrossoverSignal.GOLDEN_CROSS
                ))
                .withMessage(
                        "Analyzed result requires enough available bars."
                );
    }

    @Test
    void rejectsInsufficientDataResultContainingAnalysis() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovingAverageAnalysisResult(
                        STRATEGY_IDENTITY,
                        "005930",
                        MovingAverageAnalysisStatus.INSUFFICIENT_DATA,
                        21,
                        20,
                        previousIndicator(),
                        MovingAverageTrend.FLAT,
                        indicator(),
                        MovingAverageTrend.UPTREND,
                        MovingAverageCrossoverSignal.GOLDEN_CROSS
                ))
                .withMessage(
                        "Insufficient data result must not contain analysis."
                );
    }

    private MovingAverageIndicator indicator() {
        return indicator(
                AS_OF_DATE,
                "71000.00",
                "70000.00"
        );
    }

    private MovingAverageIndicator previousIndicator() {
        return indicator(
                AS_OF_DATE.minusDays(1),
                "70000.00",
                "70000.00"
        );
    }

    private MovingAverageIndicator indicator(
            LocalDate asOfDate,
            String shortAveragePriceKrw,
            String longAveragePriceKrw
    ) {
        return new MovingAverageIndicator(
                "005930",
                asOfDate,
                movingAverage(5, shortAveragePriceKrw, asOfDate),
                movingAverage(20, longAveragePriceKrw, asOfDate)
        );
    }

    private SimpleMovingAverage movingAverage(
            int period,
            String averagePriceKrw,
            LocalDate asOfDate
    ) {
        return new SimpleMovingAverage(
                "005930",
                period,
                new BigDecimal(averagePriceKrw),
                asOfDate.minusDays(period - 1L),
                asOfDate
        );
    }
}
