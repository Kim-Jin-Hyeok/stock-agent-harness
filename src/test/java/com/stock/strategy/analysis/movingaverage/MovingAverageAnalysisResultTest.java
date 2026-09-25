package com.stock.strategy.analysis.movingaverage;

import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverage;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
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
                        indicator,
                        MovingAverageTrend.UPTREND
                );

        assertThat(result.status())
                .isEqualTo(MovingAverageAnalysisStatus.ANALYZED);
        assertThat(result.requiredBarCount()).isEqualTo(20);
        assertThat(result.availableBarCount()).isEqualTo(60);
        assertThat(result.indicator()).isEqualTo(indicator);
        assertThat(result.trend()).isEqualTo(MovingAverageTrend.UPTREND);
    }

    @Test
    void createsInsufficientDataResultWithoutAnalysis() {
        MovingAverageAnalysisResult result =
                MovingAverageAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        "005930",
                        20,
                        19
                );

        assertThat(result.status())
                .isEqualTo(MovingAverageAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(result.indicator()).isNull();
        assertThat(result.trend()).isNull();
    }

    @Test
    void rejectsAnalyzedResultWithoutEnoughBars() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MovingAverageAnalysisResult(
                        STRATEGY_IDENTITY,
                        "005930",
                        MovingAverageAnalysisStatus.ANALYZED,
                        20,
                        19,
                        indicator(),
                        MovingAverageTrend.UPTREND
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
                        20,
                        19,
                        indicator(),
                        MovingAverageTrend.UPTREND
                ))
                .withMessage(
                        "Insufficient data result must not contain analysis."
                );
    }

    private MovingAverageIndicator indicator() {
        return new MovingAverageIndicator(
                "005930",
                AS_OF_DATE,
                movingAverage(5, "71000.00"),
                movingAverage(20, "70000.00")
        );
    }

    private SimpleMovingAverage movingAverage(
            int period,
            String averagePriceKrw
    ) {
        return new SimpleMovingAverage(
                "005930",
                period,
                new BigDecimal(averagePriceKrw),
                AS_OF_DATE.minusDays(period - 1L),
                AS_OF_DATE
        );
    }
}
