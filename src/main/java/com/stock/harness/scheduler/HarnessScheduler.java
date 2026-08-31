package com.stock.harness.scheduler;

import com.stock.harness.HarnessRunResult;
import com.stock.harness.InvestmentHarness;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HarnessScheduler {
    private final InvestmentHarness investmentHarness;
    private final HarnessSchedulerProperties harnessSchedulerProperties;

    @Scheduled(fixedDelayString = "${harness.scheduler.fixed-delay-ms}")
    public void run() {
        if (!harnessSchedulerProperties.enabled()) {
            log.debug("Harness scheduler is disabled");
            return;
        }

        log.info("Harness scheduler triggered");

        HarnessRunResult result = investmentHarness.run();

        log.info(
                "Harness scheduler completed. runId={}, status={}",
                result.runId(),
                result.status()
        );
    }
}
