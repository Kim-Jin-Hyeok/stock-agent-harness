package com.stock.harness.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
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

class HarnessDecisionSnapshotTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 23);

    @Test
    void getBuyDecisionSnapshot() {
        HarnessDecisionSnapshot snapshot = HarnessDecisionSnapshot.from(buyDecision());

        assertThat(snapshot.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.quantity()).isEqualTo(10L);
        assertThat(snapshot.expectedPriceKrw()).isEqualTo(70_000L);
        assertThat(snapshot.estimatedOrderAmountKrw()).isEqualTo(snapshot.quantity() * snapshot.expectedPriceKrw());
        assertThat(snapshot.reason()).isEqualTo("Buy Samsung Electronics.");
    }

    @Test
    void getSellDecisionSnapshot() {
        HarnessDecisionSnapshot snapshot = HarnessDecisionSnapshot.from(sellDecision());

        assertThat(snapshot.action()).isEqualTo(InvestmentAction.SELL);
        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.quantity()).isEqualTo(5L);
        assertThat(snapshot.expectedPriceKrw()).isEqualTo(75_000L);
        assertThat(snapshot.estimatedOrderAmountKrw()).isEqualTo(snapshot.quantity() * snapshot.expectedPriceKrw());
        assertThat(snapshot.reason()).isEqualTo("Sell Samsung Electronics.");
    }

    @Test
    void getHoldDecisionSnapshot() {
        HarnessDecisionSnapshot snapshot = HarnessDecisionSnapshot.from(holdDecision());

        assertThat(snapshot.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(snapshot.symbol()).isNull();
        assertThat(snapshot.quantity()).isNull();
        assertThat(snapshot.expectedPriceKrw()).isNull();
        assertThat(snapshot.estimatedOrderAmountKrw()).isZero();
        assertThat(snapshot.reason()).isEqualTo("No trade decision.");
        assertThat(snapshot.movingAverageEvidence()).isNull();
    }

    @Test
    void mapsMovingAverageEvidence() {
        MovingAverageAnalysisResult analysis =
                MovingAverageAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
                        60,
                        indicator(
                                AS_OF_DATE.minusDays(1),
                                "70000.00",
                                "70000.00"
                        ),
                        MovingAverageTrend.FLAT,
                        indicator(
                                AS_OF_DATE,
                                "71000.00",
                                "70000.00"
                        ),
                        MovingAverageTrend.UPTREND,
                        MovingAverageCrossoverSignal.GOLDEN_CROSS
                );
        InvestmentDecision decision = new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Moving average analyzed.",
                MovingAverageDecisionEvidence.analyzed(
                        analysis,
                        72_000L,
                        CurrentPriceLookupSource.PROVIDER
                )
        );

        HarnessDecisionSnapshot snapshot = HarnessDecisionSnapshot.from(
                decision
        );

        assertThat(snapshot.movingAverageEvidence().status())
                .isEqualTo(MovingAverageAnalysisStatus.ANALYZED);
        assertThat(snapshot.movingAverageEvidence().trend())
                .isEqualTo(MovingAverageTrend.UPTREND);
        assertThat(snapshot.movingAverageEvidence().currentPriceKrw())
                .isEqualTo(72_000L);
        assertThat(snapshot.movingAverageEvidence().currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                "Buy Samsung Electronics."
        );
    }

    private InvestmentDecision sellDecision() {
        return new InvestmentDecision(
                InvestmentAction.SELL,
                "005930",
                5L,
                75_000L,
                "Sell Samsung Electronics."
        );
    }

    private InvestmentDecision holdDecision() {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "No trade decision."
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
