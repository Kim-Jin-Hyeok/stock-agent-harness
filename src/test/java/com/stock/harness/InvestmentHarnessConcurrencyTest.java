package com.stock.harness;

import com.stock.agent.AgentNextAction;
import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.harness.agent.validation.HarnessAgentActionValidator;
import com.stock.harness.api.HarnessController;
import com.stock.harness.execution.retry.HarnessRetryWaiter;
import com.stock.harness.scheduler.HarnessScheduler;
import com.stock.harness.scheduler.config.HarnessSchedulerProperties;
import com.stock.harness.scheduler.config.ScheduledStrategyProperties;
import com.stock.harness.scheduler.config.StrategyRunWindowProperties;
import com.stock.harness.scheduler.policy.StrategyExecutionDatePolicy;
import com.stock.harness.scheduler.policy.StrategyRunCadencePolicy;
import com.stock.harness.scheduler.policy.StrategyRunWindowPolicy;
import com.stock.harness.tool.HarnessToolAuthorizer;
import com.stock.harness.tool.HarnessToolExecutor;
import com.stock.harness.tool.validation.HarnessToolRequestValidator;
import com.stock.harness.tool.validation.HarnessToolResultValidator;
import com.stock.market.MarketService;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.observation.CurrentPriceObservationService;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskGuard;
import com.stock.risk.RiskReasonCode;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.universe.StrategyStockUniverseRegistry;
import com.stock.trade.TradeExecutor;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvestmentHarnessConcurrencyTest {
    private static final InvestmentStrategyIdentity STRATEGY =
            new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING);
    private static final InvestmentStrategyIdentity OTHER_STRATEGY =
            new InvestmentStrategyIdentity("OTHER_SWING", 1, InvestmentHorizon.SWING);
    private static final PortfolioSnapshot PORTFOLIO =
            new PortfolioSnapshot(1_000_000, 1_000_000, List.of());

    private final RiskGuard riskGuard = mock(RiskGuard.class);
    private final TradeExecutor tradeExecutor = mock(TradeExecutor.class);
    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final MarketService marketService = mock(MarketService.class);
    private final HarnessRunHistoryService historyService = mock(HarnessRunHistoryService.class);
    private final AgentNextActionProvider provider = mock(AgentNextActionProvider.class);
    private final HarnessToolExecutor toolExecutor = mock(HarnessToolExecutor.class);
    private final StrategyStockUniverseRegistry universe = mock(StrategyStockUniverseRegistry.class);
    private final TradeHistoryService tradeHistoryService = mock(TradeHistoryService.class);
    private final InvestmentHarness harness = new InvestmentHarness(
            riskGuard, tradeExecutor, portfolioService, marketService, historyService,
            provider, new HarnessProperties(10, 5), mock(HarnessToolAuthorizer.class),
            toolExecutor, mock(HarnessToolResultValidator.class),
            new HarnessAgentActionValidator(), new HarnessToolRequestValidator(),
            mock(HarnessRetryWaiter.class), universe, mock(CurrentPriceObservationService.class)
    );

    @BeforeEach
    void setUp() {
        InvestmentDecision decision = new InvestmentDecision(
                InvestmentAction.HOLD, null, null, null, "No order"
        );
        when(universe.getCandidateSymbols(any())).thenReturn(List.of("005930"));
        when(portfolioService.getCurrentSnapshot(any())).thenReturn(PORTFOLIO);
        when(marketService.getCurrentSnapshot()).thenReturn(new MarketSnapshot("KRX", true, "Open"));
        when(provider.next(any())).thenReturn(AgentNextAction.finalDecision(decision));
        when(riskGuard.validate(any(), any(), any())).thenReturn(new RiskCheckResult(
                RiskCheckStatus.APPROVED, InvestmentAction.HOLD, null, null, null, 0L,
                RiskReasonCode.HOLD_NO_ORDER_REQUIRED, "No order"
        ));
        when(tradeExecutor.execute(any(), any(), any(), any())).thenReturn(new TradeResult(
                TradeStatus.SKIPPED, InvestmentAction.HOLD, null, null, null, 0L,
                TradeReasonCode.HOLD_NO_ORDER, "No order"
        ));
    }

    @Test
    void concurrentDuplicatesAreRejectedBeforeMarketProviderAndTradeCalls() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstPortfolioLoad(entered, release);

        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            Future<HarnessRunResult> active = executor.submit(() -> harness.run(STRATEGY));
            try {
                await(entered);
                List<Future<HarnessRunResult>> duplicates = Stream.generate(() ->
                        executor.submit(() -> harness.run(new InvestmentStrategyIdentity(
                                STRATEGY.strategyId(), STRATEGY.strategyVersion(), STRATEGY.horizon()
                        )))
                ).limit(4).toList();
                for (Future<HarnessRunResult> duplicate : duplicates) {
                    assertThatThrownBy(() -> duplicate.get(5, TimeUnit.SECONDS))
                            .hasCauseInstanceOf(HarnessRunAlreadyInProgressException.class);
                }
                // A rejected caller must not release the active caller's registration.
                assertThatThrownBy(() -> harness.run(STRATEGY))
                        .isInstanceOf(HarnessRunAlreadyInProgressException.class);
                verify(universe).getCandidateSymbols(STRATEGY);
                verify(portfolioService).getCurrentSnapshot(STRATEGY);
                verifyNoInteractions(marketService, provider, toolExecutor, tradeExecutor, historyService);
            } finally {
                release.countDown();
            }
            assertThat(active.get(5, TimeUnit.SECONDS).status()).isEqualTo(HarnessRunStatus.COMPLETED);
        }

        assertThat(harness.run(STRATEGY).status()).isEqualTo(HarnessRunStatus.COMPLETED);
        verify(historyService, times(2)).record(any());
    }

    @ParameterizedTest
    @MethodSource("differentIdentities")
    void differentStrategyVersionOrHorizonCanRunWhileFirstIsActive(
            InvestmentStrategyIdentity other
    ) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstPortfolioLoad(entered, release);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<HarnessRunResult> active = executor.submit(() -> harness.run(STRATEGY));
            try {
                await(entered);
                assertThat(harness.run(other).status()).isEqualTo(HarnessRunStatus.COMPLETED);
                assertThatThrownBy(() -> harness.run(STRATEGY))
                        .isInstanceOf(HarnessRunAlreadyInProgressException.class);
            } finally {
                release.countDown();
            }
            assertThat(active.get(5, TimeUnit.SECONDS).status()).isEqualTo(HarnessRunStatus.COMPLETED);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"portfolio", "provider"})
    void failedRunReleasesRegistrationForNextRun(String failurePoint) {
        if (failurePoint.equals("portfolio")) {
            when(portfolioService.getCurrentSnapshot(STRATEGY))
                    .thenThrow(new IllegalStateException("Portfolio unavailable"))
                    .thenReturn(PORTFOLIO);
        } else {
            when(provider.next(any()))
                    .thenThrow(new IllegalStateException("Provider unavailable"))
                    .thenReturn(AgentNextAction.finalDecision(new InvestmentDecision(
                            InvestmentAction.HOLD, null, null, null, "No order"
                    )));
        }

        assertThat(harness.run(STRATEGY).status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(harness.run(STRATEGY).status()).isEqualTo(HarnessRunStatus.COMPLETED);
        verify(historyService, times(2)).record(any());
    }

    @Test
    void historyFailureEscapingRunStillReleasesRegistration() {
        IllegalStateException failure = new IllegalStateException("History unavailable");
        doThrow(failure).when(historyService).record(any());

        assertThatThrownBy(() -> harness.run(STRATEGY)).isSameAs(failure);

        doNothing().when(historyService).record(any());
        assertThat(harness.run(STRATEGY).status()).isEqualTo(HarnessRunStatus.COMPLETED);
    }

    @Test
    void registrationIsHeldUntilHistoryRecordingFinishes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            await(release);
            return null;
        }).when(historyService).record(any());

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<HarnessRunResult> active = executor.submit(() -> harness.run(STRATEGY));
            try {
                await(entered);
                assertThatThrownBy(() -> harness.run(STRATEGY))
                        .isInstanceOf(HarnessRunAlreadyInProgressException.class);
                verify(marketService).getCurrentSnapshot();
                verify(provider).next(any());
                verify(tradeExecutor).execute(any(), any(), any(), any());
                verify(historyService).record(any());
            } finally {
                release.countDown();
            }
            assertThat(active.get(5, TimeUnit.SECONDS).status()).isEqualTo(HarnessRunStatus.COMPLETED);
        }
        assertThat(harness.run(STRATEGY).status()).isEqualTo(HarnessRunStatus.COMPLETED);
    }

    @Test
    void manualApiRejectsDuplicateWhileSchedulerRunIsActive() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstPortfolioLoad(entered, release);
        HarnessScheduler scheduler = scheduler(List.of(STRATEGY));
        MockMvc api = api();

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> active = executor.submit(scheduler::run);
            try {
                await(entered);
                api.perform(runRequest()).andExpect(status().isConflict());
                verifyNoInteractions(marketService, provider, toolExecutor, tradeExecutor, tradeHistoryService);
                verify(historyService, never()).record(any());
            } finally {
                release.countDown();
            }
            active.get(5, TimeUnit.SECONDS);
        }
        api.perform(runRequest()).andExpect(status().isOk());
    }

    @Test
    void schedulerSkipsActiveManualApiRunAndContinuesNextStrategy() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstPortfolioLoad(entered, release);
        HarnessScheduler scheduler = scheduler(List.of(STRATEGY, OTHER_STRATEGY));
        MockMvc api = api();

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> active = executor.submit(() -> api.perform(runRequest()).andExpect(status().isOk()));
            try {
                await(entered);
                assertThatCode(scheduler::run).doesNotThrowAnyException();
                verify(universe).getCandidateSymbols(STRATEGY);
                verify(portfolioService).getCurrentSnapshot(STRATEGY);
                verify(provider).next(any());
                verify(tradeExecutor).execute(any(), eq(OTHER_STRATEGY), any(), any());
                verify(historyService).record(argThat(result -> result.strategyIdentity().equals(OTHER_STRATEGY)));
                verify(historyService, never()).record(argThat(result -> result.strategyIdentity().equals(STRATEGY)));
            } finally {
                release.countDown();
            }
            active.get(5, TimeUnit.SECONDS);
        }
    }

    private void blockFirstPortfolioLoad(CountDownLatch entered, CountDownLatch release) {
        AtomicBoolean first = new AtomicBoolean(true);
        when(portfolioService.getCurrentSnapshot(STRATEGY)).thenAnswer(invocation -> {
            if (first.getAndSet(false)) {
                entered.countDown();
                await(release);
            }
            return PORTFOLIO;
        });
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(5, TimeUnit.SECONDS)).as("Run synchronization latch").isTrue();
    }

    private static Stream<InvestmentStrategyIdentity> differentIdentities() {
        return Stream.of(
                OTHER_STRATEGY,
                new InvestmentStrategyIdentity(STRATEGY.strategyId(), 2, STRATEGY.horizon()),
                new InvestmentStrategyIdentity(STRATEGY.strategyId(), 1, InvestmentHorizon.LONG_TERM)
        );
    }

    private MockMvc api() {
        return MockMvcBuilders.standaloneSetup(new HarnessController(
                harness, tradeHistoryService, historyService, mock(HarnessStateService.class)
        )).build();
    }

    private MockHttpServletRequestBuilder runRequest() {
        return post("/api/harness/run").contentType(MediaType.APPLICATION_JSON).content("""
                {"strategyId":"SWING_V1","strategyVersion":1,"horizon":"SWING"}
                """);
    }

    private HarnessScheduler scheduler(List<InvestmentStrategyIdentity> strategies) {
        HarnessSchedulerProperties properties = new HarnessSchedulerProperties(
                true, "Asia/Seoul", strategies.stream().map(identity -> new ScheduledStrategyProperties(
                        true, identity.strategyId(), identity.strategyVersion(), identity.horizon(),
                        new StrategyRunWindowProperties(
                                Set.of(DayOfWeek.FRIDAY), LocalTime.of(9, 0), LocalTime.of(15, 30)
                        )
                )).toList()
        );
        StrategyExecutionDatePolicy executionDatePolicy = mock(StrategyExecutionDatePolicy.class);
        when(executionDatePolicy.isExecutionDate(any(), any())).thenReturn(true);
        when(historyService.getLatestRunStartedAt(any())).thenReturn(Optional.empty());
        return new HarnessScheduler(
                harness, properties, historyService, new StrategyRunCadencePolicy(),
                new StrategyRunWindowPolicy(), executionDatePolicy,
                Clock.fixed(Instant.parse("2026-01-09T00:00:00Z"), ZoneOffset.UTC)
        );
    }
}
