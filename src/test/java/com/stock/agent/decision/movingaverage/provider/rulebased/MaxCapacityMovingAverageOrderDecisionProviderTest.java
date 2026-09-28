package com.stock.agent.decision.movingaverage.provider.rulebased;

import com.stock.agent.decision.movingaverage.MovingAverageActionPolicy;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContextFactory;
import com.stock.agent.decision.movingaverage.provider.result.MovingAverageOrderDecisionProviderResult;
import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
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

class MaxCapacityMovingAverageOrderDecisionProviderTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final MaxCapacityMovingAverageOrderDecisionProvider provider =
            new MaxCapacityMovingAverageOrderDecisionProvider();
    private final MovingAverageOrderDecisionContextFactory contextFactory =
            new MovingAverageOrderDecisionContextFactory(
                    new MovingAverageActionPolicy(),
                    new OrderQuantityCapacityCalculator(
                            new RiskProperties(0.1, 0.3)
                    )
            );

    @Test
    void proposesMaximumAllowedQuantityForBuySignal() {
        MovingAverageOrderDecisionProviderResult result = provider.propose(
                buyContext(100_000L, 10_000_000L)
        );
        OrderQuantityProposal proposal = result.proposal();

        assertThat(result.providerIdentity().providerId())
                .isEqualTo("MAX_CAPACITY_RULE_BASED");
        assertThat(result.providerIdentity().providerVersion()).isEqualTo(1);
        assertThat(proposal.intent())
                .isEqualTo(OrderDecisionIntent.EXECUTE_ORDER);
        assertThat(proposal.quantity()).isEqualTo(10L);
        assertThat(proposal.reason())
                .contains("action=BUY")
                .contains("symbol=" + SYMBOL)
                .contains("quantity=10");
    }

    @Test
    void proposesCurrentPositionQuantityForSellSignal() {
        OrderQuantityProposal proposal = provider.propose(
                sellContext(5L)
        ).proposal();

        assertThat(proposal.intent())
                .isEqualTo(OrderDecisionIntent.EXECUTE_ORDER);
        assertThat(proposal.quantity()).isEqualTo(5L);
        assertThat(proposal.reason())
                .contains("action=SELL")
                .contains("quantity=5");
    }

    @Test
    void proposesHoldForHoldSignal() {
        OrderQuantityProposal proposal = provider.propose(
                holdContext()
        ).proposal();

        assertThat(proposal.intent()).isEqualTo(OrderDecisionIntent.HOLD);
        assertThat(proposal.quantity()).isNull();
        assertThat(proposal.reason())
                .contains("signal action is HOLD")
                .contains("symbol=" + SYMBOL);
    }

    @Test
    void proposesHoldWhenOrderCapacityIsUnavailable() {
        OrderQuantityProposal proposal = provider.propose(
                buyContext(400_000L, 3_333_334L)
        ).proposal();

        assertThat(proposal.intent()).isEqualTo(OrderDecisionIntent.HOLD);
        assertThat(proposal.quantity()).isNull();
        assertThat(proposal.reason())
                .contains("capacity is unavailable")
                .contains("action=BUY")
                .contains("maxAllowedQuantity=0");
    }

    @Test
    void rejectsNullContext() {
        assertThatThrownBy(() -> provider.propose(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("context must not be null.");
    }

    private MovingAverageOrderDecisionContext buyContext(
            long currentPriceKrw,
            long totalAssetAmountKrw
    ) {
        return context(
                MovingAverageCrossoverSignal.GOLDEN_CROSS,
                emptyPortfolio(totalAssetAmountKrw),
                currentPriceKrw
        );
    }

    private MovingAverageOrderDecisionContext sellContext(long quantity) {
        return context(
                MovingAverageCrossoverSignal.DEAD_CROSS,
                new PortfolioSnapshot(
                        9_500_000L,
                        10_000_000L,
                        List.of(new PortfolioPosition(
                                SYMBOL,
                                quantity,
                                100_000L,
                                quantity * 100_000L
                        ))
                ),
                100_000L
        );
    }

    private MovingAverageOrderDecisionContext holdContext() {
        return context(
                MovingAverageCrossoverSignal.NONE,
                emptyPortfolio(10_000_000L),
                100_000L
        );
    }

    private MovingAverageOrderDecisionContext context(
            MovingAverageCrossoverSignal signal,
            PortfolioSnapshot portfolioSnapshot,
            long currentPriceKrw
    ) {
        return contextFactory.create(
                STRATEGY_IDENTITY,
                portfolioSnapshot,
                analyzedResult(signal),
                CurrentPriceLookupResult.provider(
                        new CurrentPriceSnapshot(
                                SYMBOL,
                                currentPriceKrw,
                                Instant.parse("2026-09-26T09:00:00Z")
                        )
                )
        );
    }

    private PortfolioSnapshot emptyPortfolio(long totalAssetAmountKrw) {
        return new PortfolioSnapshot(
                totalAssetAmountKrw,
                totalAssetAmountKrw,
                List.of()
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
