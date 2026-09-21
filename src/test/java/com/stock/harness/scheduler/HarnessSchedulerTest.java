package com.stock.harness.scheduler;

import com.stock.harness.InvestmentHarness;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HarnessSchedulerTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);

    @Test
    void doesNotRunHarnessWhenSchedulerDisabled() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                false
        );

        scheduler.run();

        verify(investmentHarness, never()).run(STRATEGY_IDENTITY);
    }

    @Test
    void runsHarnessWhenSchedulerEnabled() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessScheduler scheduler = scheduler(
                investmentHarness,
                true
        );

        when(investmentHarness.run(STRATEGY_IDENTITY)).thenReturn(completedRunResult());

        scheduler.run();

        verify(investmentHarness, times(1)).run(STRATEGY_IDENTITY);
    }

    private HarnessScheduler scheduler(
            InvestmentHarness investmentHarness,
            boolean enabled
    ) {
        return new HarnessScheduler(
                investmentHarness,
                new HarnessSchedulerProperties(
                        enabled,
                        STRATEGY_IDENTITY.strategyId(),
                        STRATEGY_IDENTITY.strategyVersion(),
                        STRATEGY_IDENTITY.horizon()
                )
        );
    }

    private HarnessRunResult completedRunResult() {
        return HarnessRunResult.of(
                "run-1",
                STRATEGY_IDENTITY,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private LocalDateTime startedAt() {
        return LocalDateTime.of(2026, 1, 1, 9, 0);
    }

    private LocalDateTime finishedAt() {
        return startedAt().plusSeconds(1);
    }
}
