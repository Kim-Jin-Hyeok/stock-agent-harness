package com.stock.agent.evidence.movingaverage;

import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverage;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageDecisionEvidenceTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 23);

    @Test
    void createsAnalyzedEvidenceWithCurrentPrice() {
        MovingAverageAnalysisResult analysis = analyzedResult();

        MovingAverageDecisionEvidence evidence =
                MovingAverageDecisionEvidence.analyzed(
                        analysis,
                        72_000L,
                        CurrentPriceLookupSource.PROVIDER
                );

        assertThat(evidence.analysis()).isEqualTo(analysis);
        assertThat(evidence.currentPriceKrw()).isEqualTo(72_000L);
        assertThat(evidence.currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
    }

    @Test
    void createsInsufficientDataEvidenceWithoutCurrentPrice() {
        MovingAverageAnalysisResult analysis =
                MovingAverageAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        "005930",
                        20,
                        19
                );

        MovingAverageDecisionEvidence evidence =
                MovingAverageDecisionEvidence.insufficientData(analysis);

        assertThat(evidence.analysis()).isEqualTo(analysis);
        assertThat(evidence.currentPriceKrw()).isNull();
        assertThat(evidence.currentPriceSource()).isNull();
    }

    @Test
    void rejectsAnalyzedEvidenceWithoutPositiveCurrentPrice() {
        assertThatThrownBy(() -> new MovingAverageDecisionEvidence(
                analyzedResult(),
                0L,
                CurrentPriceLookupSource.PROVIDER
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Analyzed evidence requires a positive "
                                + "currentPriceKrw."
                );
    }

    @Test
    void rejectsInsufficientDataEvidenceContainingCurrentPrice() {
        MovingAverageAnalysisResult analysis =
                MovingAverageAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        "005930",
                        20,
                        19
                );

        assertThatThrownBy(() -> new MovingAverageDecisionEvidence(
                analysis,
                72_000L,
                CurrentPriceLookupSource.CACHE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Insufficient data evidence must not contain "
                                + "current price."
                );
    }

    private MovingAverageAnalysisResult analyzedResult() {
        return MovingAverageAnalysisResult.analyzed(
                STRATEGY_IDENTITY,
                60,
                new MovingAverageIndicator(
                        "005930",
                        AS_OF_DATE,
                        movingAverage(5, "71000.00"),
                        movingAverage(20, "70000.00")
                ),
                MovingAverageTrend.UPTREND
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
