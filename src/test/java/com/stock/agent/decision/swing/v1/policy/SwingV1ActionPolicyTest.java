package com.stock.agent.decision.swing.v1.policy;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
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

class SwingV1ActionPolicyTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 9, 28);
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-29T00:10:00Z");

    private final SwingV1ActionPolicy policy = new SwingV1ActionPolicy();

    @Test
    void returnsBuyForGoldenCrossWithoutPosition() {
        SwingV1ActionPolicyResult result = policy.decide(
                analyzed(MovingAverageCrossoverSignal.GOLDEN_CROSS),
                portfolioWithoutPosition(),
                currentPrice(71_000L)
        );

        assertThat(result.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(result.reasonCode())
                .isEqualTo(SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY);
        assertThat(result.atrStopPriceKrw()).isNull();
    }

    @Test
    void returnsHoldForGoldenCrossWithPosition() {
        SwingV1ActionPolicyResult result = policy.decide(
                analyzed(MovingAverageCrossoverSignal.GOLDEN_CROSS),
                portfolioWithPosition(),
                currentPrice(71_000L)
        );

        assertThat(result.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(result.reasonCode())
                .isEqualTo(
                        SwingV1ActionReasonCode.POSITION_ALREADY_HELD
                );
        assertThat(result.atrStopPriceKrw())
                .isEqualByComparingTo("67499.000");
    }

    @Test
    void returnsSellWhenCurrentPriceReachesAtrInitialStop() {
        SwingV1ActionPolicyResult result = policy.decide(
                analyzed(MovingAverageCrossoverSignal.NONE),
                portfolioWithPosition(),
                currentPrice(67_499L)
        );

        assertThat(result.action()).isEqualTo(InvestmentAction.SELL);
        assertThat(result.reasonCode())
                .isEqualTo(SwingV1ActionReasonCode.ATR_INITIAL_STOP);
        assertThat(result.atrStopPriceKrw())
                .isEqualByComparingTo("67499.000");
    }

    @Test
    void returnsSellForDeadCrossWithPosition() {
        SwingV1ActionPolicyResult result = policy.decide(
                analyzed(MovingAverageCrossoverSignal.DEAD_CROSS),
                portfolioWithPosition(),
                currentPrice(70_000L)
        );

        assertThat(result.action()).isEqualTo(InvestmentAction.SELL);
        assertThat(result.reasonCode())
                .isEqualTo(SwingV1ActionReasonCode.DEAD_CROSS_EXIT);
    }

    @Test
    void prioritizesAtrInitialStopOverDeadCross() {
        SwingV1ActionPolicyResult result = policy.decide(
                analyzed(MovingAverageCrossoverSignal.DEAD_CROSS),
                portfolioWithPosition(),
                currentPrice(67_000L)
        );

        assertThat(result.action()).isEqualTo(InvestmentAction.SELL);
        assertThat(result.reasonCode())
                .isEqualTo(SwingV1ActionReasonCode.ATR_INITIAL_STOP);
    }

    @Test
    void returnsHoldForDeadCrossWithoutPosition() {
        SwingV1ActionPolicyResult result = policy.decide(
                analyzed(MovingAverageCrossoverSignal.DEAD_CROSS),
                portfolioWithoutPosition(),
                currentPrice(70_000L)
        );

        assertThat(result.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(result.reasonCode())
                .isEqualTo(SwingV1ActionReasonCode.NO_ENTRY_SIGNAL);
    }

    @Test
    void returnsHoldWhenPositionHasNoExitSignal() {
        SwingV1ActionPolicyResult result = policy.decide(
                analyzed(MovingAverageCrossoverSignal.NONE),
                portfolioWithPosition(),
                currentPrice(70_000L)
        );

        assertThat(result.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(result.reasonCode())
                .isEqualTo(SwingV1ActionReasonCode.HOLD_POSITION);
    }

    @Test
    void returnsHoldWhenTechnicalDataIsInsufficient() {
        SwingV1ActionPolicyResult result = policy.decide(
                insufficientData(),
                portfolioWithoutPosition(),
                currentPrice(70_000L)
        );

        assertThat(result.action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(result.reasonCode()).isEqualTo(
                SwingV1ActionReasonCode.INSUFFICIENT_DAILY_PRICE_HISTORY
        );
    }

    @Test
    void rejectsCurrentPriceForDifferentSymbol() {
        CurrentPriceSnapshot differentSymbolPrice =
                new CurrentPriceSnapshot(
                        "000660",
                        170_000L,
                        OBSERVED_AT
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.decide(
                        analyzed(MovingAverageCrossoverSignal.NONE),
                        portfolioWithoutPosition(),
                        differentSymbolPrice
                ))
                .withMessage(
                        "Current price symbol must match analysis symbol."
                );
    }

    @Test
    void rejectsStrategyOtherThanSwingV1() {
        InvestmentStrategyIdentity swingV2 =
                new InvestmentStrategyIdentity(
                        "SWING_V2",
                        2,
                        InvestmentHorizon.SWING
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.decide(
                        analyzed(
                                swingV2,
                                MovingAverageCrossoverSignal.NONE
                        ),
                        portfolioWithoutPosition(),
                        currentPrice(70_000L)
                ))
                .withMessage(
                        "analysis must use SWING_V1 strategy identity."
                );
    }

    private SwingTechnicalAnalysisResult analyzed(
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        return analyzed(STRATEGY_IDENTITY, crossoverSignal);
    }

    private SwingTechnicalAnalysisResult analyzed(
            InvestmentStrategyIdentity strategyIdentity,
            MovingAverageCrossoverSignal crossoverSignal
    ) {
        MovingAverageTrend previousTrend = switch (crossoverSignal) {
            case GOLDEN_CROSS -> MovingAverageTrend.DOWNTREND;
            case DEAD_CROSS, NONE -> MovingAverageTrend.UPTREND;
        };
        MovingAverageTrend currentTrend = switch (crossoverSignal) {
            case GOLDEN_CROSS, NONE -> MovingAverageTrend.UPTREND;
            case DEAD_CROSS -> MovingAverageTrend.DOWNTREND;
        };
        MovingAverageAnalysisResult movingAverageAnalysis =
                MovingAverageAnalysisResult.analyzed(
                        strategyIdentity,
                        61,
                        movingAverageIndicator(
                                AS_OF_DATE.minusDays(1),
                                previousTrend
                        ),
                        previousTrend,
                        movingAverageIndicator(
                                AS_OF_DATE,
                                currentTrend
                        ),
                        currentTrend,
                        crossoverSignal
                );
        AverageTrueRangeAnalysisResult atrAnalysis =
                AverageTrueRangeAnalysisResult.analyzed(
                        strategyIdentity,
                        61,
                        averageTrueRange()
                );
        return SwingTechnicalAnalysisResult.from(
                movingAverageAnalysis,
                atrAnalysis
        );
    }

    private SwingTechnicalAnalysisResult insufficientData() {
        MovingAverageAnalysisResult movingAverageAnalysis =
                MovingAverageAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        SYMBOL,
                        61,
                        60
                );
        AverageTrueRangeAnalysisResult atrAnalysis =
                AverageTrueRangeAnalysisResult.analyzed(
                        STRATEGY_IDENTITY,
                        60,
                        averageTrueRange()
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
        String shortAveragePrice = switch (trend) {
            case UPTREND -> "71000.00";
            case DOWNTREND -> "69000.00";
            case FLAT -> "70000.00";
        };
        return new MovingAverageIndicator(
                SYMBOL,
                date,
                movingAverage(20, shortAveragePrice, date),
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

    private AverageTrueRange averageTrueRange() {
        return new AverageTrueRange(
                SYMBOL,
                14,
                new BigDecimal("1250.50"),
                AS_OF_DATE.minusDays(59),
                AS_OF_DATE
        );
    }

    private PortfolioSnapshot portfolioWithoutPosition() {
        return new PortfolioSnapshot(
                1_000_000L,
                1_000_000L,
                List.of()
        );
    }

    private PortfolioSnapshot portfolioWithPosition() {
        return new PortfolioSnapshot(
                300_000L,
                1_000_000L,
                List.of(new PortfolioPosition(
                        SYMBOL,
                        10L,
                        70_000L,
                        700_000L
                ))
        );
    }

    private CurrentPriceSnapshot currentPrice(long priceKrw) {
        return new CurrentPriceSnapshot(
                SYMBOL,
                priceKrw,
                OBSERVED_AT
        );
    }
}
