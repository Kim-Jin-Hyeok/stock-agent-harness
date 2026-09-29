package com.stock.agent.decision.swing.v1.resolution;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityReasonCode;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.valuation.PortfolioPositionValuation;
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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SwingV1DecisionResolverTest {
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

    private final SwingV1DecisionResolver resolver =
            new SwingV1DecisionResolver();

    @Test
    void resolvesExecutableBuyDecision() {
        SwingV1DecisionEvidence evidence = buyEvidence(14L);

        InvestmentDecision decision = resolver.resolve(evidence);

        assertThat(decision.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(decision.symbol()).isEqualTo(SYMBOL);
        assertThat(decision.quantity()).isEqualTo(14L);
        assertThat(decision.expectedPriceKrw()).isEqualTo(70_000L);
        assertThat(decision.reason())
                .isEqualTo("Golden cross entry signal detected.");
        assertThat(decision.evidence()).isSameAs(evidence);
    }

    @Test
    void resolvesExecutableSellDecision() {
        SwingV1DecisionEvidence evidence = sellEvidence();

        InvestmentDecision decision = resolver.resolve(evidence);

        assertThat(decision.action()).isEqualTo(InvestmentAction.SELL);
        assertThat(decision.symbol()).isEqualTo(SYMBOL);
        assertThat(decision.quantity()).isEqualTo(10L);
        assertThat(decision.expectedPriceKrw()).isEqualTo(70_000L);
        assertThat(decision.reason())
                .isEqualTo("Dead cross exit signal detected.");
        assertThat(decision.evidence()).isSameAs(evidence);
    }

    @Test
    void resolvesPolicyHoldWithoutOrderFields() {
        SwingV1DecisionEvidence evidence = holdEvidence();

        InvestmentDecision decision = resolver.resolve(evidence);

        assertThat(decision.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(decision.symbol()).isNull();
        assertThat(decision.quantity()).isNull();
        assertThat(decision.expectedPriceKrw()).isNull();
        assertThat(decision.reason())
                .isEqualTo("No swing entry signal detected.");
        assertThat(decision.evidence()).isSameAs(evidence);
    }

    @Test
    void resolvesUnavailableBuyOrderAsHold() {
        SwingV1DecisionEvidence evidence = buyEvidence(0L);

        InvestmentDecision decision = resolver.resolve(evidence);

        assertThat(decision.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(decision.symbol()).isNull();
        assertThat(decision.quantity()).isNull();
        assertThat(decision.expectedPriceKrw()).isNull();
        assertThat(decision.reason())
                .isEqualTo("Order capacity is unavailable.");
        assertThat(decision.evidence()).isSameAs(evidence);
        assertThat(decision.swingV1Evidence()
                .actionPolicyResult()
                .action()).isEqualTo(InvestmentAction.BUY);
    }

    @Test
    void rejectsNullEvidence() {
        assertThatNullPointerException()
                .isThrownBy(() -> resolver.resolve(null))
                .withMessage("evidence must not be null.");
    }

    private SwingV1DecisionEvidence buyEvidence(long finalQuantity) {
        long maxAllowedQuantity = finalQuantity == 0L ? 0L : 14L;
        SwingV1OrderQuantityReasonCode quantityReasonCode =
                finalQuantity == 0L
                        ? SwingV1OrderQuantityReasonCode
                                .ORDER_CAPACITY_UNAVAILABLE
                        : SwingV1OrderQuantityReasonCode
                                .HARNESS_CAPACITY_LIMITED_BUY;
        String quantityReason = finalQuantity == 0L
                ? "Order capacity is unavailable."
                : "Harness capacity limited the BUY quantity.";

        return evidence(
                InvestmentAction.BUY,
                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                "Golden cross entry signal detected.",
                MovingAverageCrossoverSignal.GOLDEN_CROSS,
                emptyValuation(),
                new SwingV1OrderQuantityResult(
                        InvestmentAction.BUY,
                        SYMBOL,
                        50_000L,
                        new BigDecimal("2000.0"),
                        25L,
                        new OrderQuantityCapacity(
                                InvestmentAction.BUY,
                                SYMBOL,
                                70_000L,
                                0L,
                                142L,
                                maxAllowedQuantity,
                                42L,
                                maxAllowedQuantity,
                                new BigDecimal("0.007000")
                        ),
                        finalQuantity,
                        quantityReasonCode,
                        quantityReason
                )
        );
    }

    private SwingV1DecisionEvidence sellEvidence() {
        return evidence(
                InvestmentAction.SELL,
                SwingV1ActionReasonCode.DEAD_CROSS_EXIT,
                "Dead cross exit signal detected.",
                MovingAverageCrossoverSignal.DEAD_CROSS,
                valuationWithPosition(),
                new SwingV1OrderQuantityResult(
                        InvestmentAction.SELL,
                        SYMBOL,
                        0L,
                        BigDecimal.ZERO,
                        0L,
                        new OrderQuantityCapacity(
                                InvestmentAction.SELL,
                                SYMBOL,
                                70_000L,
                                10L,
                                0L,
                                0L,
                                0L,
                                10L,
                                new BigDecimal("0.007000")
                        ),
                        10L,
                        SwingV1OrderQuantityReasonCode.FULL_POSITION_EXIT,
                        "Use the full current position for SWING_V1 exit."
                )
        );
    }

    private SwingV1DecisionEvidence holdEvidence() {
        return evidence(
                InvestmentAction.HOLD,
                SwingV1ActionReasonCode.NO_ENTRY_SIGNAL,
                "No swing entry signal detected.",
                MovingAverageCrossoverSignal.NONE,
                emptyValuation(),
                new SwingV1OrderQuantityResult(
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
                )
        );
    }

    private SwingV1DecisionEvidence evidence(
            InvestmentAction action,
            SwingV1ActionReasonCode actionReasonCode,
            String actionReason,
            MovingAverageCrossoverSignal crossoverSignal,
            PortfolioValuationSnapshot valuation,
            SwingV1OrderQuantityResult quantityResult
    ) {
        return new SwingV1DecisionEvidence(
                analysis(crossoverSignal),
                currentPrice(),
                CurrentPriceLookupSource.PROVIDER,
                valuation,
                new SwingV1ActionPolicyResult(
                        action,
                        actionReasonCode,
                        action == InvestmentAction.SELL
                                ? new BigDecimal("68000.00")
                                : null,
                        actionReason
                ),
                quantityResult
        );
    }

    private SwingTechnicalAnalysisResult analysis(
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        MovingAverageTrend previousTrend = switch (crossoverSignal) {
            case GOLDEN_CROSS -> MovingAverageTrend.DOWNTREND;
            case DEAD_CROSS, NONE -> MovingAverageTrend.UPTREND;
        };
        MovingAverageTrend trend = switch (crossoverSignal) {
            case GOLDEN_CROSS -> MovingAverageTrend.UPTREND;
            case DEAD_CROSS -> MovingAverageTrend.DOWNTREND;
            case NONE -> MovingAverageTrend.UPTREND;
        };
        MovingAverageAnalysisResult movingAverage =
                MovingAverageAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
                        61,
                        indicator(
                                AS_OF_DATE.minusDays(1),
                                previousTrend
                        ),
                        previousTrend,
                        indicator(AS_OF_DATE, trend),
                        trend,
                        crossoverSignal
                );
        AverageTrueRangeAnalysisResult averageTrueRange =
                AverageTrueRangeAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
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
            MovingAverageTrend trend
    ) {
        String shortAveragePriceKrw = switch (trend) {
            case UPTREND -> "71000.00";
            case DOWNTREND -> "69000.00";
            case FLAT -> "70000.00";
        };
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

    private PortfolioValuationSnapshot emptyValuation() {
        return new PortfolioValuationSnapshot(
                EVALUATED_AT,
                10_000_000L,
                0L,
                10_000_000L,
                List.of()
        );
    }

    private PortfolioValuationSnapshot valuationWithPosition() {
        PortfolioPositionValuation position =
                new PortfolioPositionValuation(
                        SYMBOL,
                        10L,
                        75_000L,
                        70_000L,
                        750_000L,
                        700_000L,
                        -50_000L,
                        OBSERVED_AT
                );
        return new PortfolioValuationSnapshot(
                EVALUATED_AT,
                9_300_000L,
                700_000L,
                10_000_000L,
                List.of(position)
        );
    }
}
