package com.stock.harness.persistence;

import com.stock.agent.evidence.movingaverage.MovingAverageDecisionEvidence;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;
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

class HarnessMovingAverageEvidenceSnapshotTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 23);

    @Test
    void createsAnalyzedSnapshot() {
        HarnessMovingAverageEvidenceSnapshot snapshot =
                HarnessMovingAverageEvidenceSnapshot.from(
                        MovingAverageDecisionEvidence.analyzed(
                                analyzedResult(),
                                72_000L,
                                CurrentPriceLookupSource.PROVIDER
                        )
                );

        assertThat(snapshot.status())
                .isEqualTo(MovingAverageAnalysisStatus.ANALYZED);
        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.requiredBarCount()).isEqualTo(21);
        assertThat(snapshot.availableBarCount()).isEqualTo(60);
        assertThat(snapshot.trend()).isEqualTo(MovingAverageTrend.UPTREND);
        assertThat(snapshot.shortPeriod()).isEqualTo(5);
        assertThat(snapshot.shortAveragePriceKrw())
                .isEqualByComparingTo("71000.00");
        assertThat(snapshot.longPeriod()).isEqualTo(20);
        assertThat(snapshot.longAveragePriceKrw())
                .isEqualByComparingTo("70000.00");
        assertThat(snapshot.asOfTradingDate()).isEqualTo(AS_OF_DATE);
        assertThat(snapshot.currentPriceKrw()).isEqualTo(72_000L);
        assertThat(snapshot.currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
    }

    @Test
    void createsInsufficientDataSnapshotWithoutAnalysis() {
        MovingAverageAnalysisResult analysis =
                MovingAverageAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        "005930",
                        21,
                        20
                );

        HarnessMovingAverageEvidenceSnapshot snapshot =
                HarnessMovingAverageEvidenceSnapshot.from(
                        MovingAverageDecisionEvidence.insufficientData(
                                analysis
                        )
                );

        assertThat(snapshot.status())
                .isEqualTo(MovingAverageAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(snapshot.requiredBarCount()).isEqualTo(21);
        assertThat(snapshot.availableBarCount()).isEqualTo(20);
        assertThat(snapshot.trend()).isNull();
        assertThat(snapshot.shortPeriod()).isNull();
        assertThat(snapshot.longPeriod()).isNull();
        assertThat(snapshot.currentPriceKrw()).isNull();
        assertThat(snapshot.currentPriceSource()).isNull();
    }

    private MovingAverageAnalysisResult analyzedResult() {
        return MovingAverageAnalysisResult.analyzed(
                STRATEGY_IDENTITY,
                60,
                indicator(
                        AS_OF_DATE.minusDays(1),
                        "70000.00",
                        "70000.00"
                ),
                MovingAverageTrend.FLAT,
                indicator(AS_OF_DATE, "71000.00", "70000.00"),
                MovingAverageTrend.UPTREND,
                MovingAverageCrossoverSignal.GOLDEN_CROSS
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
