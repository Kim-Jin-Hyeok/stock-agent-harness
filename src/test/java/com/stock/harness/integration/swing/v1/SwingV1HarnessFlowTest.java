package com.stock.harness.integration.swing.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.swing.v1.policy.SwingV1ActionPolicy;
import com.stock.agent.decision.swing.v1.quantity.policy.SwingV1OrderQuantityPolicy;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityReasonCode;
import com.stock.agent.decision.swing.v1.resolution.SwingV1DecisionResolver;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionReasonCode;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.agent.provider.rulebased.StrategyRuleBasedAgentNextActionProvider;
import com.stock.agent.provider.rulebased.swing.v1.SwingV1AgentNextActionProvider;
import com.stock.harness.HarnessProperties;
import com.stock.harness.HarnessRunHistoryService;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;
import com.stock.harness.InvestmentHarness;
import com.stock.harness.agent.validation.HarnessAgentActionValidator;
import com.stock.harness.execution.retry.HarnessRetryWaiter;
import com.stock.harness.persistence.HarnessDecisionSnapshot;
import com.stock.harness.persistence.HarnessRunEntity;
import com.stock.harness.persistence.HarnessRunRepository;
import com.stock.harness.persistence.HarnessRunSnapshotJsonConverter;
import com.stock.harness.persistence.HarnessStepRepository;
import com.stock.harness.tool.HarnessToolAuthorizer;
import com.stock.harness.tool.HarnessToolExecutor;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.harness.tool.validation.HarnessToolRequestValidator;
import com.stock.harness.tool.validation.HarnessToolResultValidator;
import com.stock.market.MarketService;
import com.stock.market.calendar.MarketCalendarProperties;
import com.stock.market.calendar.MarketTradingDayPolicy;
import com.stock.market.price.CurrentPriceService;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.cache.CurrentPriceCache;
import com.stock.market.price.cache.CurrentPriceCacheProperties;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.market.price.observation.CurrentPriceObservationService;
import com.stock.market.price.provider.CurrentPriceProvider;
import com.stock.market.price.validation.CurrentPriceFreshnessPolicy;
import com.stock.market.price.validation.CurrentPriceFreshnessProperties;
import com.stock.market.session.MarketSessionPolicy;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshotStore;
import com.stock.portfolio.valuation.PortfolioValuationService;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskGuard;
import com.stock.risk.RiskProperties;
import com.stock.risk.capacity.OrderQuantityCapacityCalculator;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisService;
import com.stock.strategy.analysis.volatility.atr.AverageTrueRangeAnalysisService;
import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.data.history.config.ConfiguredStrategyDailyPriceHistoryLimit;
import com.stock.strategy.data.history.config.StrategyDailyPriceHistoryProperties;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicatorCalculator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverageCalculator;
import com.stock.strategy.indicator.movingaverage.config.ConfiguredStrategyMovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.config.StrategyMovingAverageProperties;
import com.stock.strategy.indicator.movingaverage.policy.StrategyMovingAveragePeriodPolicy;
import com.stock.strategy.indicator.volatility.atr.WilderAverageTrueRangeCalculator;
import com.stock.strategy.indicator.volatility.atr.config.ConfiguredStrategyAverageTrueRangePeriod;
import com.stock.strategy.indicator.volatility.atr.config.StrategyAverageTrueRangeProperties;
import com.stock.strategy.indicator.volatility.atr.policy.StrategyAverageTrueRangePeriodPolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignalEvaluator;
import com.stock.strategy.signal.movingaverage.MovingAverageTrendEvaluator;
import com.stock.strategy.universe.StrategyStockUniverseRegistry;
import com.stock.strategy.universe.config.ConfiguredStrategyStockUniverse;
import com.stock.strategy.universe.config.StrategyStockUniverseProperties;
import com.stock.trade.TradeExecutor;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeStatus;
import com.stock.trade.execution.virtual.VirtualTradeExecutionHandler;
import com.stock.trade.persistence.TradeRecordEntity;
import com.stock.trade.persistence.TradeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.stock.portfolio.support.PortfolioSnapshotStoreFixture.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SwingV1HarnessFlowTest {
    private static final String CANDIDATE_SYMBOL = "005930";
    private static final String POSITION_SYMBOL = "000660";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-29T06:00:00Z");
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T06:01:00Z");
    private static final Clock CLOCK =
            Clock.fixed(EVALUATED_AT, ZoneOffset.UTC);

    private final DailyPriceHistoryQueryService dailyPriceHistoryQueryService =
            mock(DailyPriceHistoryQueryService.class);
    private final CurrentPriceProvider currentPriceProvider =
            mock(CurrentPriceProvider.class);
    private final CurrentPriceObservationService observationService =
            mock(CurrentPriceObservationService.class);
    private final HarnessRunRepository harnessRunRepository =
            mock(HarnessRunRepository.class);
    private final HarnessStepRepository harnessStepRepository =
            mock(HarnessStepRepository.class);
    private final TradeRecordRepository tradeRecordRepository =
            mock(TradeRecordRepository.class);
    private final AgentNextActionProvider defaultProvider =
            mock(AgentNextActionProvider.class);
    private final HarnessRetryWaiter retryWaiter =
            mock(HarnessRetryWaiter.class);

    private final PortfolioSnapshotStore portfolioSnapshotStore = create();
    private final PortfolioService portfolioService =
            new PortfolioService(portfolioSnapshotStore);
    private final HarnessRunSnapshotJsonConverter snapshotJsonConverter =
            new HarnessRunSnapshotJsonConverter(
                    new ObjectMapper().findAndRegisterModules()
            );

    private InvestmentHarness investmentHarness;

    @BeforeEach
    void setUp() {
        StrategyDailyPriceHistoryPolicy historyPolicy = historyPolicy();
        CurrentPriceFreshnessPolicy freshnessPolicy = freshnessPolicy();
        RiskProperties riskProperties = new RiskProperties(0.1, 0.3);
        OrderQuantityCapacityCalculator capacityCalculator =
                new OrderQuantityCapacityCalculator(riskProperties);

        when(dailyPriceHistoryQueryService.getLatestDailyPriceHistory(
                CANDIDATE_SYMBOL,
                120
        )).thenReturn(goldenCrossHistory());
        when(currentPriceProvider.getCurrentPrice(CANDIDATE_SYMBOL))
                .thenReturn(currentPrice(CANDIDATE_SYMBOL, 110_000L));
        when(currentPriceProvider.getCurrentPrice(POSITION_SYMBOL))
                .thenReturn(currentPrice(POSITION_SYMBOL, 130_000L));

        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                POSITION_SYMBOL,
                5L,
                120_000L
        );

        investmentHarness = new InvestmentHarness(
                new RiskGuard(capacityCalculator),
                tradeExecutor(),
                portfolioService,
                marketService(),
                runHistoryService(),
                strategyAgent(historyPolicy, freshnessPolicy, capacityCalculator),
                new HarnessProperties(10, 5, 0, 5),
                new HarnessToolAuthorizer(),
                toolExecutor(historyPolicy, freshnessPolicy),
                new HarnessToolResultValidator(freshnessPolicy),
                new HarnessAgentActionValidator(),
                new HarnessToolRequestValidator(),
                retryWaiter,
                stockUniverseRegistry(),
                observationService
        );
    }

    @Test
    void runExecutesSwingV1BuyThroughHarnessAgentLoop() {
        HarnessRunResult result = investmentHarness.run(STRATEGY_IDENTITY);

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.candidateSymbols()).containsExactly(CANDIDATE_SYMBOL);
        assertThat(result.toolResults())
                .extracting(toolResult -> toolResult.type())
                .containsExactly(
                        HarnessToolType.GET_DAILY_PRICE_HISTORY,
                        HarnessToolType.GET_CURRENT_PRICE,
                        HarnessToolType.GET_CURRENT_PRICE
                );
        assertThat(result.toolResults())
                .extracting(toolResult -> toolResult.request())
                .containsExactly(
                        HarnessToolRequest.dailyPriceHistory(CANDIDATE_SYMBOL),
                        HarnessToolRequest.currentPrice(CANDIDATE_SYMBOL),
                        HarnessToolRequest.currentPrice(POSITION_SYMBOL)
                );

        assertThat(result.decision().action()).isEqualTo(InvestmentAction.BUY);
        assertThat(result.decision().symbol()).isEqualTo(CANDIDATE_SYMBOL);
        assertThat(result.decision().quantity()).isEqualTo(9L);
        assertThat(result.decision().expectedPriceKrw()).isEqualTo(110_000L);
        assertThat(result.decision().swingV1Evidence()).isNotNull();
        assertThat(result.decision().swingV1Evidence()
                .analysis()
                .movingAverageAnalysis()
                .crossoverSignal())
                .isEqualTo(MovingAverageCrossoverSignal.GOLDEN_CROSS);
        assertThat(result.decision().swingV1Evidence()
                .actionPolicyResult()
                .reasonCode())
                .isEqualTo(SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY);
        assertThat(result.decision().swingV1Evidence()
                .orderQuantityResult()
                .reasonCode())
                .isEqualTo(
                        SwingV1OrderQuantityReasonCode.ATR_RISK_LIMITED_BUY
                );
        assertThat(result.decision().swingV1Evidence()
                .portfolioValuation()
                .totalAssetAmountKrw())
                .isEqualTo(10_050_000L);

        assertThat(result.riskCheckResult().status())
                .isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(result.tradeResult().status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(result.portfolioSnapshot().cashAmountKrw())
                .isEqualTo(8_410_000L);
        assertThat(result.portfolioSnapshot().positions())
                .extracting(
                        position -> position.symbol(),
                        position -> position.quantity(),
                        position -> position.averagePriceKrw(),
                        position -> position.marketValueKrw()
                )
                .containsExactly(
                        tuple(POSITION_SYMBOL, 5L, 120_000L, 600_000L),
                        tuple(CANDIDATE_SYMBOL, 9L, 110_000L, 990_000L)
                );

        verifyExternalBoundaries(result);
        verifyPersistedRun(result);
        verifyNoInteractions(defaultProvider);
    }

    private void verifyExternalBoundaries(HarnessRunResult result) {
        verify(dailyPriceHistoryQueryService)
                .getLatestDailyPriceHistory(CANDIDATE_SYMBOL, 120);
        verify(currentPriceProvider).getCurrentPrice(CANDIDATE_SYMBOL);
        verify(currentPriceProvider).getCurrentPrice(POSITION_SYMBOL);
        verify(observationService).record(
                result.runId(),
                STRATEGY_IDENTITY,
                currentPrice(CANDIDATE_SYMBOL, 110_000L),
                CurrentPriceLookupSource.PROVIDER
        );
        verify(observationService).record(
                result.runId(),
                STRATEGY_IDENTITY,
                currentPrice(POSITION_SYMBOL, 130_000L),
                CurrentPriceLookupSource.PROVIDER
        );

        ArgumentCaptor<TradeRecordEntity> tradeCaptor =
                ArgumentCaptor.forClass(TradeRecordEntity.class);
        verify(tradeRecordRepository).save(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getRunId()).isEqualTo(result.runId());
        assertThat(tradeCaptor.getValue().getAction())
                .isEqualTo(InvestmentAction.BUY);
        assertThat(tradeCaptor.getValue().getQuantity()).isEqualTo(9L);
    }

    private void verifyPersistedRun(HarnessRunResult result) {
        ArgumentCaptor<HarnessRunEntity> runCaptor =
                ArgumentCaptor.forClass(HarnessRunEntity.class);
        verify(harnessRunRepository).save(runCaptor.capture());
        verify(harnessStepRepository).saveAll(any());

        HarnessRunEntity savedRun = runCaptor.getValue();
        HarnessDecisionSnapshot savedDecision = snapshotJsonConverter
                .toDecisionSnapshot(savedRun.getDecisionSnapshotJson());

        assertThat(savedRun.getRunId()).isEqualTo(result.runId());
        assertThat(savedRun.getStrategyId())
                .isEqualTo(STRATEGY_IDENTITY.strategyId());
        assertThat(savedRun.getStrategyVersion())
                .isEqualTo(STRATEGY_IDENTITY.strategyVersion());
        assertThat(savedRun.getHorizon())
                .isEqualTo(STRATEGY_IDENTITY.horizon());
        assertThat(savedDecision.swingV1Evidence()).isNotNull();
        assertThat(savedDecision.swingV1Evidence()
                .actionPolicyResult()
                .reasonCode())
                .isEqualTo(SwingV1ActionReasonCode.GOLDEN_CROSS_ENTRY);
    }

    private AgentNextActionProvider strategyAgent(
            StrategyDailyPriceHistoryPolicy historyPolicy,
            CurrentPriceFreshnessPolicy freshnessPolicy,
            OrderQuantityCapacityCalculator capacityCalculator
    ) {
        SwingV1AgentNextActionProvider swingV1Provider =
                new SwingV1AgentNextActionProvider(
                        swingAnalysisService(historyPolicy),
                        new SwingV1ActionPolicy(),
                        new PortfolioValuationService(freshnessPolicy),
                        new SwingV1OrderQuantityPolicy(capacityCalculator),
                        new SwingV1DecisionResolver(),
                        CLOCK
                );
        return new StrategyRuleBasedAgentNextActionProvider(
                defaultProvider,
                swingV1Provider
        );
    }

    private SwingTechnicalAnalysisService swingAnalysisService(
            StrategyDailyPriceHistoryPolicy historyPolicy
    ) {
        StrategyMovingAveragePeriodPolicy movingAveragePeriodPolicy =
                new StrategyMovingAveragePeriodPolicy(
                        new StrategyMovingAverageProperties(List.of(
                                new ConfiguredStrategyMovingAveragePeriods(
                                        STRATEGY_IDENTITY.strategyId(),
                                        STRATEGY_IDENTITY.strategyVersion(),
                                        STRATEGY_IDENTITY.horizon(),
                                        20,
                                        60
                                )
                        )),
                        historyPolicy
                );
        MovingAverageAnalysisService movingAverageAnalysisService =
                new MovingAverageAnalysisService(
                        movingAveragePeriodPolicy,
                        new MovingAverageIndicatorCalculator(
                                new SimpleMovingAverageCalculator()
                        ),
                        new MovingAverageTrendEvaluator(),
                        new MovingAverageCrossoverSignalEvaluator()
                );
        StrategyAverageTrueRangePeriodPolicy atrPeriodPolicy =
                new StrategyAverageTrueRangePeriodPolicy(
                        new StrategyAverageTrueRangeProperties(List.of(
                                new ConfiguredStrategyAverageTrueRangePeriod(
                                        STRATEGY_IDENTITY.strategyId(),
                                        STRATEGY_IDENTITY.strategyVersion(),
                                        STRATEGY_IDENTITY.horizon(),
                                        14
                                )
                        )),
                        historyPolicy
                );
        AverageTrueRangeAnalysisService atrAnalysisService =
                new AverageTrueRangeAnalysisService(
                        atrPeriodPolicy,
                        new WilderAverageTrueRangeCalculator()
                );

        return new SwingTechnicalAnalysisService(
                movingAverageAnalysisService,
                atrAnalysisService
        );
    }

    private HarnessToolExecutor toolExecutor(
            StrategyDailyPriceHistoryPolicy historyPolicy,
            CurrentPriceFreshnessPolicy freshnessPolicy
    ) {
        CurrentPriceService currentPriceService = new CurrentPriceService(
                currentPriceProvider,
                new CurrentPriceCache(
                        new CurrentPriceCacheProperties(Duration.ofMinutes(1)),
                        CLOCK,
                        freshnessPolicy
                ),
                freshnessPolicy
        );
        return new HarnessToolExecutor(
                portfolioService,
                marketService(),
                currentPriceService,
                dailyPriceHistoryQueryService,
                historyPolicy
        );
    }

    private StrategyDailyPriceHistoryPolicy historyPolicy() {
        return new StrategyDailyPriceHistoryPolicy(
                new StrategyDailyPriceHistoryProperties(List.of(
                        new ConfiguredStrategyDailyPriceHistoryLimit(
                                STRATEGY_IDENTITY.strategyId(),
                                STRATEGY_IDENTITY.strategyVersion(),
                                STRATEGY_IDENTITY.horizon(),
                                120
                        )
                ))
        );
    }

    private CurrentPriceFreshnessPolicy freshnessPolicy() {
        return new CurrentPriceFreshnessPolicy(
                new CurrentPriceFreshnessProperties(Duration.ofMinutes(5)),
                CLOCK
        );
    }

    private MarketService marketService() {
        return new MarketService(
                new MarketSessionPolicy(
                        new MarketTradingDayPolicy(
                                new MarketCalendarProperties(Set.of())
                        )
                ),
                CLOCK
        );
    }

    private TradeExecutor tradeExecutor() {
        return new TradeExecutor(
                new VirtualTradeExecutionHandler(portfolioService),
                new TradeHistoryService(tradeRecordRepository)
        );
    }

    private HarnessRunHistoryService runHistoryService() {
        return new HarnessRunHistoryService(
                snapshotJsonConverter,
                harnessRunRepository,
                harnessStepRepository
        );
    }

    private StrategyStockUniverseRegistry stockUniverseRegistry() {
        return new StrategyStockUniverseRegistry(
                new StrategyStockUniverseProperties(List.of(
                        new ConfiguredStrategyStockUniverse(
                                STRATEGY_IDENTITY.strategyId(),
                                STRATEGY_IDENTITY.strategyVersion(),
                                STRATEGY_IDENTITY.horizon(),
                                List.of(CANDIDATE_SYMBOL)
                        )
                ))
        );
    }

    private DailyPriceHistory goldenCrossHistory() {
        List<DailyPriceBar> bars = IntStream.range(0, 61)
                .mapToObj(index -> {
                    long closePriceKrw = index < 60
                            ? 100_000L
                            : 110_000L;
                    return new DailyPriceBar(
                            LocalDate.of(2026, 7, 1).plusDays(index),
                            closePriceKrw,
                            closePriceKrw + 1_000L,
                            closePriceKrw - 1_000L,
                            closePriceKrw,
                            1_000_000L + index
                    );
                })
                .toList();
        return new DailyPriceHistory(CANDIDATE_SYMBOL, bars);
    }

    private CurrentPriceSnapshot currentPrice(String symbol, long priceKrw) {
        return new CurrentPriceSnapshot(symbol, priceKrw, OBSERVED_AT);
    }
}
