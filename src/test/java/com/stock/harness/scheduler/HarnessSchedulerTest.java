package com.stock.harness.scheduler;

import com.stock.harness.HarnessRunHistoryService;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;
import com.stock.harness.InvestmentHarness;
import com.stock.harness.scheduler.config.HarnessSchedulerProperties;
import com.stock.harness.scheduler.config.ScheduledStrategyProperties;
import com.stock.harness.scheduler.policy.StrategyRunCadencePolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HarnessSchedulerTest {
    private static final InvestmentStrategyIdentity DAY_TRADING =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);
    private static final InvestmentStrategyIdentity SWING =
            new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING);
    private static final InvestmentStrategyIdentity LONG_TERM =
            new InvestmentStrategyIdentity("LONG_TERM_V1", 1, InvestmentHorizon.LONG_TERM);
    private static final Instant NOW = Instant.parse("2026-01-05T00:00:00Z");
    private static final LocalDateTime SEOUL_NOW = LocalDateTime.of(2026, 1, 5, 9, 0);

    @Test
    void doesNotInspectStrategiesWhenSchedulerDisabled() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessRunHistoryService historyService = mock(HarnessRunHistoryService.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                historyService,
                properties(false, enabledStrategies())
        );

        scheduler.run();

        verifyNoInteractions(historyService);
        verify(investmentHarness, never()).run(any());
    }

    @Test
    void doesNotInspectDisabledStrategy() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessRunHistoryService historyService = mock(HarnessRunHistoryService.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                historyService,
                properties(true, List.of(
                        scheduledStrategy(false, DAY_TRADING),
                        scheduledStrategy(true, SWING)
                ))
        );
        when(historyService.getLatestRunStartedAt(SWING)).thenReturn(Optional.empty());
        when(investmentHarness.run(SWING)).thenReturn(completedRunResult(SWING));

        scheduler.run();

        verify(historyService, never()).getLatestRunStartedAt(DAY_TRADING);
        verify(investmentHarness, never()).run(DAY_TRADING);
        verify(historyService).getLatestRunStartedAt(SWING);
        verify(investmentHarness).run(SWING);
    }

    @Test
    void runsAllEnabledStrategiesWithoutRunHistory() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessRunHistoryService historyService = mock(HarnessRunHistoryService.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                historyService,
                properties(true, enabledStrategies())
        );
        when(historyService.getLatestRunStartedAt(any())).thenReturn(Optional.empty());
        when(investmentHarness.run(any())).thenAnswer(invocation ->
                completedRunResult(invocation.getArgument(0))
        );

        scheduler.run();

        verify(investmentHarness).run(DAY_TRADING);
        verify(investmentHarness).run(SWING);
        verify(investmentHarness).run(LONG_TERM);
    }

    @Test
    void runsOnlyStrategiesWhoseCadenceIsDue() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessRunHistoryService historyService = mock(HarnessRunHistoryService.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                historyService,
                properties(true, enabledStrategies())
        );
        when(historyService.getLatestRunStartedAt(DAY_TRADING))
                .thenReturn(Optional.of(SEOUL_NOW.minusMinutes(5)));
        when(historyService.getLatestRunStartedAt(SWING))
                .thenReturn(Optional.of(SEOUL_NOW.minusDays(1).plusHours(1)));
        when(historyService.getLatestRunStartedAt(LONG_TERM))
                .thenReturn(Optional.of(SEOUL_NOW.minusHours(1)));
        when(investmentHarness.run(DAY_TRADING)).thenReturn(completedRunResult(DAY_TRADING));
        when(investmentHarness.run(SWING)).thenReturn(completedRunResult(SWING));

        scheduler.run();

        verify(investmentHarness).run(DAY_TRADING);
        verify(investmentHarness).run(SWING);
        verify(investmentHarness, never()).run(LONG_TERM);
    }

    private HarnessScheduler scheduler(
            InvestmentHarness investmentHarness,
            HarnessRunHistoryService historyService,
            HarnessSchedulerProperties properties
    ) {
        return new HarnessScheduler(
                investmentHarness,
                properties,
                historyService,
                new StrategyRunCadencePolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private HarnessSchedulerProperties properties(
            boolean enabled,
            List<ScheduledStrategyProperties> strategies
    ) {
        return new HarnessSchedulerProperties(enabled, "Asia/Seoul", strategies);
    }

    private List<ScheduledStrategyProperties> enabledStrategies() {
        return List.of(
                scheduledStrategy(true, DAY_TRADING),
                scheduledStrategy(true, SWING),
                scheduledStrategy(true, LONG_TERM)
        );
    }

    private ScheduledStrategyProperties scheduledStrategy(
            boolean enabled,
            InvestmentStrategyIdentity strategyIdentity
    ) {
        return new ScheduledStrategyProperties(
                enabled,
                strategyIdentity.strategyId(),
                strategyIdentity.strategyVersion(),
                strategyIdentity.horizon()
        );
    }

    private HarnessRunResult completedRunResult(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        return HarnessRunResult.of(
                "run-" + strategyIdentity.strategyId(),
                strategyIdentity,
                HarnessRunStatus.COMPLETED,
                SEOUL_NOW,
                SEOUL_NOW.plusSeconds(1),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
