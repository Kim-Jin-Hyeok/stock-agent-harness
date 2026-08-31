package com.stock.harness.scheduler;

import com.stock.harness.InvestmentHarness;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class HarnessSchedulerTest {

    @Test
    void doesNotRunHarnessWhenSchedulerDisabled() {
        InvestmentHarness investmentHarness = mock(InvestmentHarness.class);
        HarnessSchedulerProperties properties = new HarnessSchedulerProperties(false);

        HarnessScheduler scheduler = new HarnessScheduler(
                investmentHarness,
                properties
        );

        scheduler.run();

        verify(investmentHarness, never()).run();
    }
}