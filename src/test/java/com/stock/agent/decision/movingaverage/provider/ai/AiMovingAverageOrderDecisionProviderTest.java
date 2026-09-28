package com.stock.agent.decision.movingaverage.provider.ai;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.MovingAverageActionPolicy;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContext;
import com.stock.agent.decision.movingaverage.MovingAverageOrderDecisionContextFactory;
import com.stock.agent.decision.movingaverage.provider.ai.execution.MovingAverageOrderDecisionAiExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPrompt;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPromptFactory;
import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequestFactory;
import com.stock.agent.decision.movingaverage.provider.identity.MovingAverageOrderDecisionProviderIdentity;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiMovingAverageOrderDecisionProviderTest {
    private static final String SYMBOL = "005930";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );
    private static final MovingAverageOrderDecisionProviderIdentity
            AI_IDENTITY = new MovingAverageOrderDecisionProviderIdentity(
                    "OPENAI_GPT_6_LUNA",
                    1
            );

    private final MovingAverageOrderDecisionContextFactory contextFactory =
            new MovingAverageOrderDecisionContextFactory(
                    new MovingAverageActionPolicy(),
                    new OrderQuantityCapacityCalculator(
                            new RiskProperties(0.1, 0.3)
                    )
            );
    private final MovingAverageOrderDecisionAiRequestFactory requestFactory =
            new MovingAverageOrderDecisionAiRequestFactory();
    private final MovingAverageOrderDecisionAiPromptFactory promptFactory =
            new MovingAverageOrderDecisionAiPromptFactory();

    @Test
    void delegatesActionableBuyContextToAiExecutor() {
        AtomicReference<MovingAverageOrderDecisionAiPrompt> capturedPrompt =
                new AtomicReference<>();
        AtomicInteger executionCount = new AtomicInteger();
        MovingAverageOrderDecisionProviderResult expected = aiResult(5L);
        AiMovingAverageOrderDecisionProvider provider = provider(prompt -> {
            executionCount.incrementAndGet();
            capturedPrompt.set(prompt);
            return expected;
        });

        MovingAverageOrderDecisionProviderResult result = provider.propose(
                context(
                        MovingAverageCrossoverSignal.GOLDEN_CROSS,
                        portfolioWithoutPosition(10_000_000L),
                        100_000L
                )
        );

        assertThat(result).isSameAs(expected);
        assertThat(executionCount).hasValue(1);
        assertThat(capturedPrompt.get()).isNotNull();
        assertThat(capturedPrompt.get().request().signalAction())
                .isEqualTo(InvestmentAction.BUY);
        assertThat(capturedPrompt.get().request().maxAllowedQuantity())
                .isEqualTo(10L);
    }

    @Test
    void delegatesActionableSellContextToAiExecutor() {
        AtomicReference<MovingAverageOrderDecisionAiPrompt> capturedPrompt =
                new AtomicReference<>();
        AtomicInteger executionCount = new AtomicInteger();
        AiMovingAverageOrderDecisionProvider provider = provider(prompt -> {
            executionCount.incrementAndGet();
            capturedPrompt.set(prompt);
            return aiResult(3L);
        });

        MovingAverageOrderDecisionProviderResult result = provider.propose(
                context(
                        MovingAverageCrossoverSignal.DEAD_CROSS,
                        portfolioWithPosition(5L),
                        100_000L
                )
        );

        assertThat(result.proposal().quantity()).isEqualTo(3L);
        assertThat(executionCount).hasValue(1);
        assertThat(capturedPrompt.get()).isNotNull();
        assertThat(capturedPrompt.get().request().signalAction())
                .isEqualTo(InvestmentAction.SELL);
        assertThat(capturedPrompt.get().request().maxAllowedQuantity())
                .isEqualTo(5L);
    }

    @Test
    void skipsAiExecutorForHoldSignal() {
        AtomicInteger executionCount = new AtomicInteger();
        AiMovingAverageOrderDecisionProvider provider = provider(prompt -> {
            executionCount.incrementAndGet();
            return aiResult(1L);
        });

        MovingAverageOrderDecisionProviderResult result = provider.propose(
                context(
                        MovingAverageCrossoverSignal.NONE,
                        portfolioWithoutPosition(10_000_000L),
                        100_000L
                )
        );

        assertThat(executionCount).hasValue(0);
        assertThat(result.providerIdentity().providerId())
                .isEqualTo("AI_INVOCATION_SKIPPED");
        assertThat(result.proposal().intent())
                .isEqualTo(OrderDecisionIntent.HOLD);
        assertThat(result.proposal().quantity()).isNull();
        assertThat(result.proposal().reason())
                .contains("signal action is HOLD")
                .contains("symbol=" + SYMBOL);
    }

    @Test
    void skipsAiExecutorWhenOrderCapacityIsUnavailable() {
        AtomicInteger executionCount = new AtomicInteger();
        AiMovingAverageOrderDecisionProvider provider = provider(prompt -> {
            executionCount.incrementAndGet();
            return aiResult(1L);
        });

        MovingAverageOrderDecisionProviderResult result = provider.propose(
                context(
                        MovingAverageCrossoverSignal.GOLDEN_CROSS,
                        portfolioWithoutPosition(3_333_334L),
                        400_000L
                )
        );

        assertThat(executionCount).hasValue(0);
        assertThat(result.providerIdentity().providerId())
                .isEqualTo("AI_INVOCATION_SKIPPED");
        assertThat(result.proposal().reason())
                .contains("order capacity is unavailable")
                .contains("action=BUY")
                .contains("maxAllowedQuantity=0");
    }

    @Test
    void rejectsNullContext() {
        AiMovingAverageOrderDecisionProvider provider = provider(
                prompt -> aiResult(1L)
        );

        assertThatThrownBy(() -> provider.propose(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("context must not be null.");
    }

    @Test
    void rejectsNullAiExecutorResult() {
        AiMovingAverageOrderDecisionProvider provider = provider(
                prompt -> null
        );

        assertThatThrownBy(() -> provider.propose(context(
                MovingAverageCrossoverSignal.GOLDEN_CROSS,
                portfolioWithoutPosition(10_000_000L),
                100_000L
        )))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("AI executor result must not be null.");
    }

    private AiMovingAverageOrderDecisionProvider provider(
            MovingAverageOrderDecisionAiExecutor executor
    ) {
        return new AiMovingAverageOrderDecisionProvider(
                requestFactory,
                promptFactory,
                executor
        );
    }

    private MovingAverageOrderDecisionProviderResult aiResult(long quantity) {
        return new MovingAverageOrderDecisionProviderResult(
                AI_IDENTITY,
                OrderQuantityProposal.execute(
                        quantity,
                        "AI selected an allowed order quantity."
                )
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
                                Instant.parse("2026-09-28T00:00:00Z")
                        )
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
        LocalDate currentDate = LocalDate.of(2026, 9, 28);
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
