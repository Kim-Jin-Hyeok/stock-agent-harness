package com.stock.agent.decision.movingaverage;

import com.stock.agent.InvestmentAction;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageOrderDecisionContextTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    @Test
    void createsContextWhenRelatedValuesAreConsistent() {
        MovingAverageOrderDecisionContext context = context(
                STRATEGY_IDENTITY,
                new CurrentPriceSnapshot(
                        SYMBOL,
                        100_000L,
                        Instant.parse("2026-09-26T09:00:00Z")
                ),
                buyCapacity()
        );

        assertThat(context.signalAction()).isEqualTo(InvestmentAction.BUY);
        assertThat(context.symbol()).isEqualTo(SYMBOL);
        assertThat(context.quantityCapacity().maxAllowedQuantity())
                .isEqualTo(10L);
    }

    @Test
    void rejectsCurrentPriceForDifferentSymbol() {
        CurrentPriceSnapshot differentSymbolPrice =
                new CurrentPriceSnapshot(
                        "000660",
                        100_000L,
                        Instant.parse("2026-09-26T09:00:00Z")
                );

        assertThatThrownBy(() -> context(
                STRATEGY_IDENTITY,
                differentSymbolPrice,
                buyCapacity()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must match currentPriceSnapshot.");
    }

    @Test
    void rejectsCapacityForDifferentAction() {
        OrderQuantityCapacity holdCapacity = new OrderQuantityCapacity(
                InvestmentAction.HOLD,
                SYMBOL,
                100_000L,
                0,
                0,
                0,
                0,
                0,
                new BigDecimal("0.010000")
        );

        assertThatThrownBy(() -> context(
                STRATEGY_IDENTITY,
                new CurrentPriceSnapshot(
                        SYMBOL,
                        100_000L,
                        Instant.parse("2026-09-26T09:00:00Z")
                ),
                holdCapacity
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "signalAction must match quantityCapacity action."
                );
    }

    @Test
    void rejectsStrategyDifferentFromAnalysis() {
        InvestmentStrategyIdentity differentStrategy =
                new InvestmentStrategyIdentity(
                        "SWING_V1",
                        1,
                        InvestmentHorizon.SWING
                );

        assertThatThrownBy(() -> context(
                differentStrategy,
                new CurrentPriceSnapshot(
                        SYMBOL,
                        100_000L,
                        Instant.parse("2026-09-26T09:00:00Z")
                ),
                buyCapacity()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("strategyIdentity must match analysisResult.");
    }

    private MovingAverageOrderDecisionContext context(
            InvestmentStrategyIdentity strategyIdentity,
            CurrentPriceSnapshot currentPriceSnapshot,
            OrderQuantityCapacity quantityCapacity
    ) {
        return new MovingAverageOrderDecisionContext(
                strategyIdentity,
                InvestmentAction.BUY,
                SYMBOL,
                new PortfolioSnapshot(
                        10_000_000L,
                        10_000_000L,
                        List.of()
                ),
                currentPriceSnapshot,
                CurrentPriceLookupSource.PROVIDER,
                analyzedResult(MovingAverageCrossoverSignal.GOLDEN_CROSS),
                quantityCapacity
        );
    }

    private OrderQuantityCapacity buyCapacity() {
        return new OrderQuantityCapacity(
                InvestmentAction.BUY,
                SYMBOL,
                100_000L,
                0,
                100,
                10,
                30,
                10,
                new BigDecimal("0.010000")
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
                indicator(currentDate, "71000.00"),
                MovingAverageTrend.UPTREND,
                signal
        );
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
