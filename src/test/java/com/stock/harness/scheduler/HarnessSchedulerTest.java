package com.stock.harness.scheduler;

import com.stock.harness.HarnessRunHistoryService;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;
import com.stock.harness.InvestmentHarness;
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
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDateTime SEOUL_NOW = LocalDateTime.of(2026, 1, 1, 9, 0);

    @Test
    void doesNotCheckCadenceOrRunHarnessWhenSchedulerDisabled() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessRunHistoryService harnessRunHistoryService = mock(HarnessRunHistoryService.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                harnessRunHistoryService,
                false
        );

        scheduler.run();

        verifyNoInteractions(harnessRunHistoryService);
        verify(investmentHarness, never()).run(any());
    }

    @Test
    void runsHarnessWhenStrategyHasNoRunHistory() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessRunHistoryService harnessRunHistoryService = mock(HarnessRunHistoryService.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                harnessRunHistoryService,
                true
        );
        when(harnessRunHistoryService.getLatestRunStartedAt(STRATEGY_IDENTITY))
                .thenReturn(Optional.empty());
        when(investmentHarness.run(STRATEGY_IDENTITY)).thenReturn(completedRunResult());

        scheduler.run();

        verify(harnessRunHistoryService).getLatestRunStartedAt(STRATEGY_IDENTITY);
        verify(investmentHarness).run(STRATEGY_IDENTITY);
    }

    @Test
    void doesNotRunHarnessBeforeDayTradingCadenceBoundary() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessRunHistoryService harnessRunHistoryService = mock(HarnessRunHistoryService.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                harnessRunHistoryService,
                true
        );
        when(harnessRunHistoryService.getLatestRunStartedAt(STRATEGY_IDENTITY))
                .thenReturn(Optional.of(SEOUL_NOW.minusMinutes(5).plusNanos(1)));

        scheduler.run();

        verify(investmentHarness, never()).run(any());
    }

    @Test
    void runsHarnessAtDayTradingCadenceBoundaryUsingConfiguredTimeZone() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessRunHistoryService harnessRunHistoryService = mock(HarnessRunHistoryService.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                harnessRunHistoryService,
                true
        );
        when(harnessRunHistoryService.getLatestRunStartedAt(STRATEGY_IDENTITY))
                .thenReturn(Optional.of(SEOUL_NOW.minusMinutes(5)));
        when(investmentHarness.run(STRATEGY_IDENTITY)).thenReturn(completedRunResult());

        scheduler.run();

        verify(investmentHarness).run(STRATEGY_IDENTITY);
    }

    private HarnessScheduler scheduler(
            InvestmentHarness investmentHarness,
            HarnessRunHistoryService harnessRunHistoryService,
            boolean enabled
    ) {
        return new HarnessScheduler(
                investmentHarness,
                new HarnessSchedulerProperties(
                        enabled,
                        STRATEGY_IDENTITY.strategyId(),
                        STRATEGY_IDENTITY.strategyVersion(),
                        STRATEGY_IDENTITY.horizon(),
                        "Asia/Seoul"
                ),
                harnessRunHistoryService,
                new StrategyRunCadencePolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private HarnessRunResult completedRunResult() {
        return HarnessRunResult.of(
                "run-1",
                STRATEGY_IDENTITY,
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
