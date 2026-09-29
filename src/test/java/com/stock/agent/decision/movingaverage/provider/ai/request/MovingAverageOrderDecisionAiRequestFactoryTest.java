package com.stock.agent.decision.movingaverage.provider.ai.request;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.MovingAverageActionPolicy;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContextFactory;
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

class MovingAverageOrderDecisionAiRequestFactoryTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final MovingAverageOrderDecisionAiRequestFactory factory =
            new MovingAverageOrderDecisionAiRequestFactory();
    private final MovingAverageOrderDecisionContextFactory contextFactory =
            new MovingAverageOrderDecisionContextFactory(
                    new MovingAverageActionPolicy(),
                    new OrderQuantityCapacityCalculator(
                            new RiskProperties(0.1, 0.3)
                    )
            );

    @Test
    void createsAiRequestFromBuyContext() {
        MovingAverageOrderDecisionAiRequest request = factory.create(
                context(
                        MovingAverageCrossoverSignal.GOLDEN_CROSS,
                        portfolioWithoutPosition(10_000_000L),
                        100_000L,
                        CurrentPriceLookupSource.PROVIDER
                )
        );

        assertThat(request.strategyId()).isEqualTo("DAY_TRADING_V1");
        assertThat(request.strategyVersion()).isEqualTo(1);
        assertThat(request.horizon())
                .isEqualTo(InvestmentHorizon.DAY_TRADING);
        assertThat(request.signalAction()).isEqualTo(InvestmentAction.BUY);
        assertThat(request.symbol()).isEqualTo(SYMBOL);
        assertThat(request.currentPriceKrw()).isEqualTo(100_000L);
        assertThat(request.currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
        assertThat(request.currentPriceObservedAt())
                .isEqualTo(Instant.parse("2026-09-26T09:00:00Z"));
        assertThat(request.cashAmountKrw()).isEqualTo(10_000_000L);
        assertThat(request.totalAssetAmountKrw()).isEqualTo(10_000_000L);
        assertThat(request.currentPositionQuantity()).isZero();
        assertThat(request.previousTrend()).isEqualTo(MovingAverageTrend.FLAT);
        assertThat(request.currentTrend())
                .isEqualTo(MovingAverageTrend.UPTREND);
        assertThat(request.crossoverSignal())
                .isEqualTo(MovingAverageCrossoverSignal.GOLDEN_CROSS);
        assertThat(request.shortMovingAveragePeriod()).isEqualTo(5);
        assertThat(request.shortMovingAveragePriceKrw())
                .isEqualByComparingTo("71000.00");
        assertThat(request.longMovingAveragePeriod()).isEqualTo(20);
        assertThat(request.longMovingAveragePriceKrw())
                .isEqualByComparingTo("70000.00");
        assertThat(request.analysisAsOfTradingDate())
                .isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(request.maxAffordableQuantity()).isEqualTo(100L);
        assertThat(request.maxOrderRatioQuantity()).isEqualTo(10L);
        assertThat(request.maxPositionRatioQuantity()).isEqualTo(30L);
        assertThat(request.maxAllowedQuantity()).isEqualTo(10L);
        assertThat(request.oneSharePortfolioRatio())
                .isEqualByComparingTo("0.010000");
    }

    @Test
    void createsAiRequestFromSellContext() {
        MovingAverageOrderDecisionAiRequest request = factory.create(
                context(
                        MovingAverageCrossoverSignal.DEAD_CROSS,
                        portfolioWithPosition(5L),
                        100_000L,
                        CurrentPriceLookupSource.CACHE
                )
        );

        assertThat(request.signalAction()).isEqualTo(InvestmentAction.SELL);
        assertThat(request.currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.CACHE);
        assertThat(request.currentPositionQuantity()).isEqualTo(5L);
        assertThat(request.maxAllowedQuantity()).isEqualTo(5L);
        assertThat(request.crossoverSignal())
                .isEqualTo(MovingAverageCrossoverSignal.DEAD_CROSS);
    }

    @Test
    void createsAiRequestFromHoldContextWithZeroAllowedQuantity() {
        MovingAverageOrderDecisionAiRequest request = factory.create(
                context(
                        MovingAverageCrossoverSignal.NONE,
                        portfolioWithoutPosition(10_000_000L),
                        100_000L,
                        CurrentPriceLookupSource.PROVIDER
                )
        );

        assertThat(request.signalAction()).isEqualTo(InvestmentAction.HOLD);
        assertThat(request.maxAllowedQuantity()).isZero();
        assertThat(request.crossoverSignal())
                .isEqualTo(MovingAverageCrossoverSignal.NONE);
    }

    @Test
    void rejectsNullContext() {
        assertThatThrownBy(() -> factory.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("context must not be null.");
    }

    private MovingAverageOrderDecisionContext context(
            MovingAverageCrossoverSignal signal,
            PortfolioSnapshot portfolioSnapshot,
            long currentPriceKrw,
            CurrentPriceLookupSource source
    ) {
        CurrentPriceSnapshot snapshot = new CurrentPriceSnapshot(
                SYMBOL,
                currentPriceKrw,
                Instant.parse("2026-09-26T09:00:00Z")
        );
        CurrentPriceLookupResult lookupResult = switch (source) {
            case PROVIDER -> CurrentPriceLookupResult.provider(snapshot);
            case CACHE -> CurrentPriceLookupResult.cache(snapshot);
            case BACKTEST_DAILY_CLOSE -> throw new IllegalArgumentException(
                    "Backtest price source is not used by this test helper."
            );
        };

        return contextFactory.create(
                STRATEGY_IDENTITY,
                portfolioSnapshot,
                analyzedResult(signal),
                lookupResult
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
