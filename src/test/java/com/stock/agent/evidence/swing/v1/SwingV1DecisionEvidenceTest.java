package com.stock.agent.evidence.swing.v1;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityReasonCode;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.agent.evidence.InvestmentDecisionEvidence;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.risk.capacity.OrderQuantityCapacity;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SwingV1DecisionEvidenceTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 29);
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-29T06:00:00Z");
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T06:01:00Z");

    @Test
    void createsEvidenceWithConsistentSwingDecisionContext() {
        SwingV1DecisionEvidence evidence = evidence(
                buyQuantity(SYMBOL, 0L)
        );

        assertThat(evidence).isInstanceOf(InvestmentDecisionEvidence.class);
        assertThat(evidence.analysis().symbol()).isEqualTo(SYMBOL);
        assertThat(evidence.actionPolicyResult().action())
                .isEqualTo(InvestmentAction.BUY);
        assertThat(evidence.orderQuantityResult().finalQuantity())
                .isEqualTo(14L);
    }

    @Test
    void rejectsDifferentActionsBetweenPolicyAndQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> evidence(holdQuantity()))
                .withMessage(
                        "actionPolicyResult action must match "
                                + "orderQuantityResult action."
                );
    }

    @Test
    void rejectsOrderQuantityForDifferentSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> evidence(
                        buyQuantity("000660", 0L)
                ))
                .withMessage(
                        "orderQuantityResult symbol must match analysis "
                                + "symbol."
                );
    }

    @Test
    void rejectsPositionQuantityDifferentFromPortfolioValuation() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> evidence(
                        buyQuantity(SYMBOL, 1L)
                ))
                .withMessage(
                        "Portfolio position quantity must match order "
                                + "capacity."
                );
    }

    @Test
    void rejectsAnalysisForDifferentSwingStrategy() {
        SwingTechnicalAnalysisResult analysis = analysis(
                new InvestmentStrategyIdentity(
                        "SWING_V2",
                        2,
                        InvestmentHorizon.SWING
                )
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SwingV1DecisionEvidence(
                        analysis,
                        currentPrice(),
                        CurrentPriceLookupSource.PROVIDER,
                        emptyPortfolioValuation(),
                        buyAction(),
                        buyQuantity(SYMBOL, 0L)
                ))
                .withMessage(
                        "analysis must use SWING_V1 strategy identity."
                );
    }

    private SwingV1DecisionEvidence evidence(
            SwingV1OrderQuantityResult orderQuantityResult
    ) {
        return new SwingV1DecisionEvidence(
                analysis(),
                currentPrice(),
                CurrentPriceLookupSource.PROVIDER,
                emptyPortfolioValuation(),
                buyAction(),
                orderQuantityResult
        );
    }

    private SwingV1ActionPolicyResult buyAction() {
        return new SwingV1ActionPolicyResult(
                InvestmentAction.BUY,
                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                null,
                "Golden cross entry signal detected."
        );
    }

    private SwingV1OrderQuantityResult buyQuantity(
            String symbol,
            long currentPositionQuantity
    ) {
        return new SwingV1OrderQuantityResult(
                InvestmentAction.BUY,
                symbol,
                50_000L,
                new BigDecimal("2000.0"),
                25L,
                new OrderQuantityCapacity(
                        InvestmentAction.BUY,
                        symbol,
                        70_000L,
                        currentPositionQuantity,
                        142L,
                        14L,
                        42L,
                        14L,
                        new BigDecimal("0.007000")
                ),
                14L,
                SwingV1OrderQuantityReasonCode
                        .HARNESS_CAPACITY_LIMITED_BUY,
                "Harness capacity limited the BUY quantity."
        );
    }

    private SwingV1OrderQuantityResult holdQuantity() {
        return new SwingV1OrderQuantityResult(
                InvestmentAction.HOLD,
                SYMBOL,
                0L,
                BigDecimal.ZERO,
                0L,
                new OrderQuantityCapacity(
                        InvestmentAction.HOLD,
                        SYMBOL,
                        70_000L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        new BigDecimal("0.007000")
                ),
                0L,
                SwingV1OrderQuantityReasonCode.HOLD_NO_ORDER,
                "HOLD action does not create an order."
        );
    }

    private SwingTechnicalAnalysisResult analysis() {
        return analysis(STRATEGY_IDENTITY);
    }

    private SwingTechnicalAnalysisResult analysis(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        MovingAverageAnalysisResult movingAverage =
                MovingAverageAnalysisResult.analyzed(
                        strategyIdentity,
                        61,
                        indicator(
                                AS_OF_DATE.minusDays(1),
                                "69000.00"
                        ),
                        MovingAverageTrend.DOWNTREND,
                        indicator(AS_OF_DATE, "71000.00"),
                        MovingAverageTrend.UPTREND,
                        MovingAverageCrossoverSignal.GOLDEN_CROSS
                );
        AverageTrueRangeAnalysisResult averageTrueRange =
                AverageTrueRangeAnalysisResult.analyzed(
                        strategyIdentity,
                        61,
                        new AverageTrueRange(
                                SYMBOL,
                                14,
                                new BigDecimal("1000.00"),
                                AS_OF_DATE.minusDays(13),
                                AS_OF_DATE
                        )
                );
        return SwingTechnicalAnalysisResult.from(
                movingAverage,
                averageTrueRange
        );
    }

    private MovingAverageIndicator indicator(
            LocalDate date,
            String shortAveragePriceKrw
    ) {
        return new MovingAverageIndicator(
                SYMBOL,
                date,
                movingAverage(20, shortAveragePriceKrw, date),
                movingAverage(60, "70000.00", date)
        );
    }

    private SimpleMovingAverage movingAverage(
            int period,
            String averagePriceKrw,
            LocalDate date
    ) {
        return new SimpleMovingAverage(
                SYMBOL,
                period,
                new BigDecimal(averagePriceKrw),
                date.minusDays(period - 1L),
                date
        );
    }

    private CurrentPriceSnapshot currentPrice() {
        return new CurrentPriceSnapshot(
                SYMBOL,
                70_000L,
                OBSERVED_AT
        );
    }

    private PortfolioValuationSnapshot emptyPortfolioValuation() {
        return new PortfolioValuationSnapshot(
                EVALUATED_AT,
                10_000_000L,
                0L,
                10_000_000L,
                List.of()
        );
    }
}
