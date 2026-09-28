package com.stock.agent.evidence.movingaverage;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.provider.identity.MovingAverageOrderDecisionProviderIdentity;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.agent.evidence.movingaverage.order.MovingAverageOrderDecisionEvidence;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.risk.capacity.OrderQuantityCapacity;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
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
                        CurrentPriceLookupSource.PROVIDER,
                        orderDecisionEvidence()
                );

        assertThat(evidence.analysis()).isEqualTo(analysis);
        assertThat(evidence.currentPriceKrw()).isEqualTo(72_000L);
        assertThat(evidence.currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
        assertThat(evidence.orderDecisionEvidence())
                .isEqualTo(orderDecisionEvidence());
    }

    @Test
    void createsInsufficientDataEvidenceWithoutCurrentPrice() {
        MovingAverageAnalysisResult analysis =
                MovingAverageAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        "005930",
                        21,
                        20
                );

        MovingAverageDecisionEvidence evidence =
                MovingAverageDecisionEvidence.insufficientData(analysis);

        assertThat(evidence.analysis()).isEqualTo(analysis);
        assertThat(evidence.currentPriceKrw()).isNull();
        assertThat(evidence.currentPriceSource()).isNull();
        assertThat(evidence.orderDecisionEvidence()).isNull();
    }

    @Test
    void rejectsAnalyzedEvidenceWithoutPositiveCurrentPrice() {
        assertThatThrownBy(() -> new MovingAverageDecisionEvidence(
                analyzedResult(),
                0L,
                CurrentPriceLookupSource.PROVIDER,
                orderDecisionEvidence()
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
                        21,
                        20
                );

        assertThatThrownBy(() -> new MovingAverageDecisionEvidence(
                analysis,
                72_000L,
                CurrentPriceLookupSource.CACHE,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Insufficient data evidence must not contain decision "
                                + "details."
                );
    }

    @Test
    void rejectsAnalyzedEvidenceWithoutOrderDecisionEvidence() {
        assertThatThrownBy(() -> new MovingAverageDecisionEvidence(
                analyzedResult(),
                72_000L,
                CurrentPriceLookupSource.PROVIDER,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(
                        "Analyzed evidence requires orderDecisionEvidence."
                );
    }

    private MovingAverageOrderDecisionEvidence orderDecisionEvidence() {
        return new MovingAverageOrderDecisionEvidence(
                InvestmentAction.BUY,
                new OrderQuantityCapacity(
                        InvestmentAction.BUY,
                        "005930",
                        72_000L,
                        0L,
                        13L,
                        1L,
                        4L,
                        1L,
                        new BigDecimal("0.007200")
                ),
                new MovingAverageOrderDecisionProviderIdentity(
                        "MAX_CAPACITY_RULE_BASED",
                        1
                ),
                OrderQuantityProposal.execute(
                        1L,
                        "Execute within the allowed capacity."
                )
        );
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
