package com.stock.agent.decision.movingaverage;

import com.stock.agent.InvestmentAction;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskProperties;
import com.stock.risk.capacity.OrderQuantityCapacityCalculator;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverage;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageOrderDecisionContextFactoryTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final MovingAverageOrderDecisionContextFactory factory =
            new MovingAverageOrderDecisionContextFactory(
                    new MovingAverageActionPolicy(),
                    new OrderQuantityCapacityCalculator(
                            new RiskProperties(0.1, 0.3)
                    )
            );

    @Test
    void createsBuyContextForGoldenCrossWithoutPosition() {
        MovingAverageOrderDecisionContext context = factory.create(
                STRATEGY_IDENTITY,
                portfolioWithoutPosition(10_000_000L),
                analyzedResult(MovingAverageCrossoverSignal.GOLDEN_CROSS),
                currentPrice(100_000L)
        );

        assertThat(context.signalAction()).isEqualTo(InvestmentAction.BUY);
        assertThat(context.quantityCapacity().maxAllowedQuantity())
                .isEqualTo(10L);
        assertThat(context.currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
    }

    @Test
    void createsSellContextForDeadCrossWithPosition() {
        MovingAverageOrderDecisionContext context = factory.create(
                STRATEGY_IDENTITY,
                portfolioWithPosition(5L),
                analyzedResult(MovingAverageCrossoverSignal.DEAD_CROSS),
                currentPrice(100_000L)
        );

        assertThat(context.signalAction()).isEqualTo(InvestmentAction.SELL);
        assertThat(context.quantityCapacity().maxAllowedQuantity())
                .isEqualTo(5L);
    }

    @Test
    void createsHoldContextWhenCrossoverSignalIsNone() {
        MovingAverageOrderDecisionContext context = factory.create(
                STRATEGY_IDENTITY,
                portfolioWithoutPosition(10_000_000L),
                analyzedResult(MovingAverageCrossoverSignal.NONE),
                currentPrice(100_000L)
        );

        assertThat(context.signalAction()).isEqualTo(InvestmentAction.HOLD);
        assertThat(context.quantityCapacity().maxAllowedQuantity()).isZero();
    }

    @Test
    void preservesZeroBuyCapacityWhenOneShareExceedsOrderLimit() {
        MovingAverageOrderDecisionContext context = factory.create(
                STRATEGY_IDENTITY,
                portfolioWithoutPosition(3_333_334L),
                analyzedResult(MovingAverageCrossoverSignal.GOLDEN_CROSS),
                currentPrice(400_000L)
        );

        assertThat(context.signalAction()).isEqualTo(InvestmentAction.BUY);
        assertThat(context.quantityCapacity().maxAllowedQuantity()).isZero();
        assertThat(context.quantityCapacity().canOrder()).isFalse();
    }

    @Test
    void rejectsAnalysisWithInsufficientData() {
        MovingAverageAnalysisResult insufficientData =
                MovingAverageAnalysisResult.insufficientData(
                        STRATEGY_IDENTITY,
                        SYMBOL,
                        21,
                        20
                );

        assertThatThrownBy(() -> factory.create(
                STRATEGY_IDENTITY,
                portfolioWithoutPosition(10_000_000L),
                insufficientData,
                currentPrice(100_000L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("analysisResult must have ANALYZED status.");
    }

    @Test
    void rejectsStrategyDifferentFromAnalysis() {
        InvestmentStrategyIdentity differentStrategy =
                new InvestmentStrategyIdentity(
                        "SWING_V1",
                        1,
                        InvestmentHorizon.SWING
                );

        assertThatThrownBy(() -> factory.create(
                differentStrategy,
                portfolioWithoutPosition(10_000_000L),
                analyzedResult(MovingAverageCrossoverSignal.GOLDEN_CROSS),
                currentPrice(100_000L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("strategyIdentity must match analysisResult.");
    }

    private CurrentPriceLookupResult currentPrice(long priceKrw) {
        return CurrentPriceLookupResult.provider(
                new CurrentPriceSnapshot(
                        SYMBOL,
                        priceKrw,
                        Instant.parse("2026-09-26T09:00:00Z")
                )
        );
    }

    private PortfolioSnapshot portfolioWithoutPosition(long totalAssetKrw) {
        return new PortfolioSnapshot(
                totalAssetKrw,
                totalAssetKrw,
                List.of()
        );
    }

    private PortfolioSnapshot portfolioWithPosition(long quantity) {
        return new PortfolioSnapshot(
                9_500_000L,
                10_000_000L,
                List.of(new PortfolioPosition(
                        SYMBOL,
                        quantity,
                        100_000L,
                        quantity * 100_000L
                ))
        );
    }

    private MovingAverageAnalysisResult analyzedResult(
            MovingAverageCrossoverSignal signal
    ) {
        LocalDate currentDate = LocalDate.of(2026, 9, 26);
        return MovingAverageAnalysisResult.analyzed(
                STRATEGY_IDENTITY,
                60,
                indicator(currentDate.minusDays(1), "70000.00"),
                MovingAverageTrend.FLAT,
                indicator(currentDate, currentShortAverage(signal)),
                currentTrend(signal),
                signal
        );
    }

    private String currentShortAverage(
            MovingAverageCrossoverSignal signal
    ) {
        return switch (signal) {
            case GOLDEN_CROSS -> "71000.00";
            case DEAD_CROSS -> "69000.00";
            case NONE -> "70000.00";
        };
    }

    private MovingAverageTrend currentTrend(
            MovingAverageCrossoverSignal signal
    ) {
        return switch (signal) {
            case GOLDEN_CROSS -> MovingAverageTrend.UPTREND;
            case DEAD_CROSS -> MovingAverageTrend.DOWNTREND;
            case NONE -> MovingAverageTrend.FLAT;
        };
    }

    private MovingAverageIndicator indicator(
            LocalDate asOfDate,
            String shortAveragePriceKrw
    ) {
        return new MovingAverageIndicator(
                SYMBOL,
                asOfDate,
                movingAverage(5, shortAveragePriceKrw, asOfDate),
                movingAverage(20, "70000.00", asOfDate)
        );
    }

    private SimpleMovingAverage movingAverage(
            int period,
            String averagePriceKrw,
            LocalDate asOfDate
    ) {
        return new SimpleMovingAverage(
                SYMBOL,
                period,
                new BigDecimal(averagePriceKrw),
                asOfDate.minusDays(period - 1L),
                asOfDate
        );
    }
}
