package com.stock.harness.scheduler;

import com.stock.harness.InvestmentHarness;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HarnessScheduler {
    private final InvestmentHarness investmentHarness;

    @Value("${harness.scheduler.enabled}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${harness.scheduler.fixed-delay-ms}")
    public void run() {
        if (!enabled) {
            log.debug("Harness scheduler is disabled");
        }

        log.info("Harness scheduler triggered");
        investmentHarness.run();
    }
}
