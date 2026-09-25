package com.stock.agent;

import com.stock.harness.HarnessRunContext;
import com.stock.harness.HarnessRunLimits;
import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;
import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.data.history.config.ConfiguredStrategyDailyPriceHistoryLimit;
import com.stock.strategy.data.history.config.StrategyDailyPriceHistoryProperties;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicatorCalculator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverageCalculator;
import com.stock.strategy.indicator.movingaverage.config.ConfiguredStrategyMovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.config.StrategyMovingAverageProperties;
import com.stock.strategy.indicator.movingaverage.policy.StrategyMovingAveragePeriodPolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignalEvaluator;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import com.stock.strategy.signal.movingaverage.MovingAverageTrendEvaluator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class InvestmentAgentTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );
    private static final LocalDate FIRST_TRADING_DATE =
            LocalDate.of(2026, 9, 1);

    private final InvestmentAgent agent = new InvestmentAgent(
            movingAverageAnalysisService()
    );

    @Test
    void nextRequestsDailyPriceHistoryForFirstCandidate() {
        HarnessRunContext context = runContext(
                List.of("005930", "000660"),
                List.of()
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.dailyPriceHistory("005930")
        );
        assertThat(action.investmentDecision()).isNull();
    }

    @Test
    void nextRequestsCurrentPriceAfterDailyPriceHistoryIsReceived() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(dailyPriceHistoryResult("005930", dailyBars(21)))
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.currentPrice("005930")
        );
        assertThat(action.investmentDecision()).isNull();
    }

    @Test
    void nextReturnsHoldAfterDailyPriceHistoryAndCurrentPriceAreReceived() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(
                        dailyPriceHistoryResult(
                                "005930",
                                dailyBars(21)
                        ),
                        currentPriceResult("005930", 80_000L)
                )
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(action.toolRequest()).isNull();
        assertThat(action.investmentDecision().action())
                .isEqualTo(InvestmentAction.HOLD);
        assertThat(action.investmentDecision().symbol()).isNull();
        assertThat(action.investmentDecision().quantity()).isNull();
        assertThat(action.investmentDecision().expectedPriceKrw()).isNull();
        assertThat(action.investmentDecision().reason())
                .isEqualTo(
                        "Moving average analyzed. symbol=005930, "
                                + "trend=UPTREND, shortPeriod=5, "
                                + "shortAveragePriceKrw=78000.00, "
                                + "longPeriod=20, "
                                + "longAveragePriceKrw=70500.00, "
                                + "asOfTradingDate=2026-09-21, "
                                + "currentPriceKrw=80000, source=PROVIDER"
                );
        assertThat(action.investmentDecision().movingAverageEvidence()
                .analysis().status())
                .isEqualTo(MovingAverageAnalysisStatus.ANALYZED);
        assertThat(action.investmentDecision().movingAverageEvidence()
                .analysis().trend())
                .isEqualTo(MovingAverageTrend.UPTREND);
        assertThat(action.investmentDecision().movingAverageEvidence()
                .currentPriceKrw())
                .isEqualTo(80_000L);
        assertThat(action.investmentDecision().movingAverageEvidence()
                .currentPriceSource())
                .isEqualTo(CurrentPriceLookupSource.PROVIDER);
    }

    @Test
    void nextReturnsHoldWithoutCurrentPriceWhenHistoryIsInsufficient() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(dailyPriceHistoryResult("005930", dailyBars(20)))
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(action.investmentDecision().action())
                .isEqualTo(InvestmentAction.HOLD);
        assertThat(action.investmentDecision().reason()).isEqualTo(
                "Moving average data is insufficient. symbol=005930, "
                        + "requiredBars=21, availableBars=20"
        );
        assertThat(action.investmentDecision().movingAverageEvidence()
                .analysis().status())
                .isEqualTo(MovingAverageAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(action.investmentDecision().movingAverageEvidence()
                .currentPriceKrw()).isNull();
        assertThat(action.investmentDecision().movingAverageEvidence()
                .currentPriceSource()).isNull();
    }

    @Test
    void nextRequestsCandidateHistoryWhenAnotherSymbolResultExists() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(dailyPriceHistoryResult(
                        "000660",
                        dailyBars(20)
                ))
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.dailyPriceHistory("005930")
        );
    }

    @Test
    void nextRequestsCandidatePriceWhenAnotherSymbolResultExists() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(
                        dailyPriceHistoryResult(
                                "005930",
                                dailyBars(21)
                        ),
                        currentPriceResult("000660", 120_000L)
                )
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.currentPrice("005930")
        );
    }

    @Test
    void nextRejectsEmptyCandidateSymbols() {
        HarnessRunContext context = runContext(List.of(), List.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> agent.next(context))
                .withMessage(
                        "Investment agent requires at least one candidate symbol."
                );
    }

    private HarnessRunContext runContext(
            List<String> candidateSymbols,
            List<HarnessToolExecutionResult> toolResults
    ) {
        return new HarnessRunContext(
                "run-1",
                STRATEGY_IDENTITY,
                new HarnessRunLimits(10, 5),
                HarnessAllowedTools.readOnly(),
                portfolioSnapshot(),
                marketSnapshot(),
                candidateSymbols,
                toolResults
        );
    }

    private HarnessToolExecutionResult dailyPriceHistoryResult(
            String symbol,
            List<DailyPriceBar> bars
    ) {
        return HarnessToolExecutionResult.executed(
                HarnessToolRequest.dailyPriceHistory(symbol),
                HarnessToolOutput.dailyPriceHistory(
                        new DailyPriceHistory(symbol, bars)
                )
        );
    }

    private HarnessToolExecutionResult currentPriceResult(
            String symbol,
            long priceKrw
    ) {
        CurrentPriceSnapshot snapshot = new CurrentPriceSnapshot(
                symbol,
                priceKrw,
                Instant.parse("2026-09-24T00:00:00Z")
        );
        return HarnessToolExecutionResult.executed(
                HarnessToolRequest.currentPrice(symbol),
                HarnessToolOutput.currentPrice(
                        CurrentPriceLookupResult.provider(snapshot)
                )
        );
    }

    private List<DailyPriceBar> dailyBars(int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> dailyBar(
                        FIRST_TRADING_DATE.plusDays(index),
                        60_000L + index * 1_000L
                ))
                .toList();
    }

    private DailyPriceBar dailyBar(
            LocalDate tradingDate,
            long closePriceKrw
    ) {
        return new DailyPriceBar(
                tradingDate,
                closePriceKrw,
                closePriceKrw + 1_000L,
                closePriceKrw - 1_000L,
                closePriceKrw,
                1_000_000L
        );
    }

    private MovingAverageAnalysisService movingAverageAnalysisService() {
        StrategyDailyPriceHistoryPolicy historyPolicy =
                new StrategyDailyPriceHistoryPolicy(
                        new StrategyDailyPriceHistoryProperties(List.of(
                                new ConfiguredStrategyDailyPriceHistoryLimit(
                                        STRATEGY_IDENTITY.strategyId(),
                                        STRATEGY_IDENTITY.strategyVersion(),
                                        STRATEGY_IDENTITY.horizon(),
                                        60
                                )
                        ))
                );
        StrategyMovingAveragePeriodPolicy periodPolicy =
                new StrategyMovingAveragePeriodPolicy(
                        new StrategyMovingAverageProperties(List.of(
                                new ConfiguredStrategyMovingAveragePeriods(
                                        STRATEGY_IDENTITY.strategyId(),
                                        STRATEGY_IDENTITY.strategyVersion(),
                                        STRATEGY_IDENTITY.horizon(),
                                        5,
                                        20
                                )
                        )),
                        historyPolicy
                );
        SimpleMovingAverageCalculator simpleCalculator =
                new SimpleMovingAverageCalculator();
        return new MovingAverageAnalysisService(
                periodPolicy,
                new MovingAverageIndicatorCalculator(simpleCalculator),
                new MovingAverageTrendEvaluator(),
                new MovingAverageCrossoverSignalEvaluator()
        );
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                1_000_000L,
                1_000_000L,
                List.of()
        );
    }

    private MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "KOSPI",
                true,
                "Test market snapshot."
        );
    }
}
