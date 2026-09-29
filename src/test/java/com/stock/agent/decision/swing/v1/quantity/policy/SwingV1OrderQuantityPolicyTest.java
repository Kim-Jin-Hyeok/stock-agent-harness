package com.stock.agent.decision.swing.v1.quantity.policy;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityReasonCode;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.valuation.PortfolioPositionValuation;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.risk.RiskProperties;
import com.stock.risk.capacity.OrderQuantityCapacityCalculator;
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

class SwingV1OrderQuantityPolicyTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 28);
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T00:10:00Z");
    private static final Instant PRICE_OBSERVED_AT =
            EVALUATED_AT.minusSeconds(30);

    private final SwingV1OrderQuantityPolicy policy =
            new SwingV1OrderQuantityPolicy(
                    new OrderQuantityCapacityCalculator(
                            new RiskProperties(0.1, 0.3)
                    )
            );

    @Test
    void limitsBuyQuantityByAtrRisk() {
        SwingV1OrderQuantityResult result = policy.calculate(
                buyAction(),
                analysis("1000.00"),
                currentPrice(SYMBOL, 10_000L),
                emptyValuation(10_000_000L)
        );

        assertThat(result.riskBudgetKrw()).isEqualTo(50_000L);
        assertThat(result.riskPerShareKrw())
                .isEqualByComparingTo("2000.000");
        assertThat(result.riskBasedQuantity()).isEqualTo(25L);
        assertThat(result.orderCapacity().maxAllowedQuantity())
                .isEqualTo(100L);
        assertThat(result.finalQuantity()).isEqualTo(25L);
        assertThat(result.reasonCode()).isEqualTo(
                SwingV1OrderQuantityReasonCode.ATR_RISK_LIMITED_BUY
        );
    }

    @Test
    void limitsBuyQuantityByHarnessCapacity() {
        SwingV1OrderQuantityResult result = policy.calculate(
                buyAction(),
                analysis("1000.00"),
                currentPrice(SYMBOL, 50_000L),
                emptyValuation(10_000_000L)
        );

        assertThat(result.riskBasedQuantity()).isEqualTo(25L);
        assertThat(result.orderCapacity().maxOrderRatioQuantity())
                .isEqualTo(20L);
        assertThat(result.finalQuantity()).isEqualTo(20L);
        assertThat(result.reasonCode()).isEqualTo(
                SwingV1OrderQuantityReasonCode
                        .HARNESS_CAPACITY_LIMITED_BUY
        );
    }

    @Test
    void limitsBuyQuantityByAvailableCash() {
        SwingV1OrderQuantityResult result = policy.calculate(
                buyAction(),
                analysis("1000.00"),
                currentPrice(SYMBOL, 50_000L),
                valuationWithOtherPosition()
        );

        assertThat(result.orderCapacity().maxAffordableQuantity())
                .isEqualTo(2L);
        assertThat(result.finalQuantity()).isEqualTo(2L);
        assertThat(result.reasonCode()).isEqualTo(
                SwingV1OrderQuantityReasonCode
                        .HARNESS_CAPACITY_LIMITED_BUY
        );
    }

    @Test
    void returnsZeroBuyQuantityWhenAtrIsZero() {
        SwingV1OrderQuantityResult result = policy.calculate(
                buyAction(),
                analysis("0"),
                currentPrice(SYMBOL, 10_000L),
                emptyValuation(10_000_000L)
        );

        assertThat(result.riskPerShareKrw()).isZero();
        assertThat(result.riskBasedQuantity()).isZero();
        assertThat(result.finalQuantity()).isZero();
        assertThat(result.reasonCode()).isEqualTo(
                SwingV1OrderQuantityReasonCode.ATR_RISK_UNAVAILABLE
        );
        assertThat(result.canOrder()).isFalse();
    }

    @Test
    void returnsZeroWhenRiskBudgetCannotCoverOneShareRisk() {
        SwingV1OrderQuantityResult result = policy.calculate(
                buyAction(),
                analysis("1000.00"),
                currentPrice(SYMBOL, 1_000L),
                emptyValuation(100_000L)
        );

        assertThat(result.riskBudgetKrw()).isEqualTo(500L);
        assertThat(result.riskBasedQuantity()).isZero();
        assertThat(result.finalQuantity()).isZero();
        assertThat(result.reasonCode()).isEqualTo(
                SwingV1OrderQuantityReasonCode
                        .ATR_RISK_BUDGET_INSUFFICIENT
        );
    }

    @Test
    void usesFullPositionQuantityForSell() {
        SwingV1OrderQuantityResult result = policy.calculate(
                sellAction(),
                analysis("1000.00"),
                currentPrice(SYMBOL, 70_000L),
                valuationWithTargetPosition()
        );

        assertThat(result.riskBudgetKrw()).isZero();
        assertThat(result.riskBasedQuantity()).isZero();
        assertThat(result.finalQuantity()).isEqualTo(10L);
        assertThat(result.reasonCode()).isEqualTo(
                SwingV1OrderQuantityReasonCode.FULL_POSITION_EXIT
        );
    }

    @Test
    void returnsZeroQuantityForHold() {
        SwingV1OrderQuantityResult result = policy.calculate(
                holdAction(),
                analysis("1000.00"),
                currentPrice(SYMBOL, 70_000L),
                emptyValuation(1_000_000L)
        );

        assertThat(result.finalQuantity()).isZero();
        assertThat(result.reasonCode()).isEqualTo(
                SwingV1OrderQuantityReasonCode.HOLD_NO_ORDER
        );
        assertThat(result.canOrder()).isFalse();
    }

    @Test
    void returnsZeroWhenValuedTotalAssetIsZero() {
        SwingV1OrderQuantityResult result = policy.calculate(
                buyAction(),
                analysis("1000.00"),
                currentPrice(SYMBOL, 1_000L),
                emptyValuation(0L)
        );

        assertThat(result.riskBudgetKrw()).isZero();
        assertThat(result.finalQuantity()).isZero();
        assertThat(result.reasonCode()).isEqualTo(
                SwingV1OrderQuantityReasonCode
                        .ATR_RISK_BUDGET_INSUFFICIENT
        );
    }

    @Test
    void rejectsCurrentPriceForDifferentSymbol() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.calculate(
                        buyAction(),
                        analysis("1000.00"),
                        currentPrice("000660", 50_000L),
                        emptyValuation(10_000_000L)
                ))
                .withMessage(
                        "Current price symbol must match analysis symbol."
                );
    }

    private SwingV1ActionPolicyResult buyAction() {
        return new SwingV1ActionPolicyResult(
                InvestmentAction.BUY,
                SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY,
                null,
                "Golden cross entry."
        );
    }

    private SwingV1ActionPolicyResult sellAction() {
        return new SwingV1ActionPolicyResult(
                InvestmentAction.SELL,
                SwingV1ActionReasonCode.ATR_INITIAL_STOP,
                new BigDecimal("68000.00"),
                "ATR stop exit."
        );
    }

    private SwingV1ActionPolicyResult holdAction() {
        return new SwingV1ActionPolicyResult(
                InvestmentAction.HOLD,
                SwingV1ActionReasonCode.NO_ENTRY_SIGNAL,
                null,
                "No entry signal."
        );
    }

    private SwingTechnicalAnalysisResult analysis(String atrKrw) {
        MovingAverageAnalysisResult movingAverageAnalysis =
                MovingAverageAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
                        61,
                        movingAverageIndicator(
                                AS_OF_DATE.minusDays(1),
                                MovingAverageTrend.DOWNTREND
                        ),
                        MovingAverageTrend.DOWNTREND,
                        movingAverageIndicator(
                                AS_OF_DATE,
                                MovingAverageTrend.UPTREND
                        ),
                        MovingAverageTrend.UPTREND,
                        MovingAverageCrossoverSignal.GOLDEN_CROSS
                );
        AverageTrueRangeAnalysisResult atrAnalysis =
                AverageTrueRangeAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
                        61,
                        new AverageTrueRange(
                                SYMBOL,
                                14,
                                new BigDecimal(atrKrw),
                                AS_OF_DATE.minusDays(59),
                                AS_OF_DATE
                        )
                );
        return SwingTechnicalAnalysisResult.from(
                movingAverageAnalysis,
                atrAnalysis
        );
    }

    private MovingAverageIndicator movingAverageIndicator(
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
                new SimpleMovingAverage(
                        SYMBOL,
                        20,
                        new BigDecimal(shortAveragePriceKrw),
                        date.minusDays(19),
                        date
                ),
                new SimpleMovingAverage(
                        SYMBOL,
                        60,
                        new BigDecimal("70000.00"),
                        date.minusDays(59),
                        date
                )
        );
    }

    private PortfolioValuationSnapshot emptyValuation(long cashAmountKrw) {
        return new PortfolioValuationSnapshot(
                EVALUATED_AT,
                cashAmountKrw,
                0L,
                cashAmountKrw,
                List.of()
        );
    }

    private PortfolioValuationSnapshot valuationWithOtherPosition() {
        PortfolioPositionValuation position = positionValuation(
                "000660",
                99L,
                100_000L,
                9_900_000L
        );
        return new PortfolioValuationSnapshot(
                EVALUATED_AT,
                100_000L,
                9_900_000L,
                10_000_000L,
                List.of(position)
        );
    }

    private PortfolioValuationSnapshot valuationWithTargetPosition() {
        PortfolioPositionValuation position = positionValuation(
                SYMBOL,
                10L,
                70_000L,
                700_000L
        );
        return new PortfolioValuationSnapshot(
                EVALUATED_AT,
                300_000L,
                700_000L,
                1_000_000L,
                List.of(position)
        );
    }

    private PortfolioPositionValuation positionValuation(
            String symbol,
            long quantity,
            long currentPriceKrw,
            long evaluationAmountKrw
    ) {
        return new PortfolioPositionValuation(
                symbol,
                quantity,
                currentPriceKrw,
                currentPriceKrw,
                evaluationAmountKrw,
                evaluationAmountKrw,
                0L,
                PRICE_OBSERVED_AT
        );
    }

    private CurrentPriceSnapshot currentPrice(
            String symbol,
            long priceKrw
    ) {
        return new CurrentPriceSnapshot(
                symbol,
                priceKrw,
                PRICE_OBSERVED_AT
        );
    }
}
