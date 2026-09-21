package com.stock.harness.scheduler;

import com.stock.harness.HarnessRunHistoryService;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.InvestmentHarness;
import com.stock.harness.scheduler.policy.StrategyRunCadencePolicy;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class HarnessScheduler {
    private final InvestmentHarness investmentHarness;
    private final HarnessSchedulerProperties harnessSchedulerProperties;
    private final HarnessRunHistoryService harnessRunHistoryService;
    private final StrategyRunCadencePolicy strategyRunCadencePolicy;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${harness.scheduler.fixed-delay-ms}")
    public void run() {
        if (!harnessSchedulerProperties.enabled()) {
            log.debug("Harness scheduler is disabled");
            return;
        }

        InvestmentStrategyIdentity strategyIdentity = harnessSchedulerProperties.strategyIdentity();
        LocalDateTime evaluatedAt = LocalDateTime.ofInstant(
                clock.instant(),
                harnessSchedulerProperties.schedulerZoneId()
        );
        Optional<LocalDateTime> latestRunStartedAt = harnessRunHistoryService
                .getLatestRunStartedAt(strategyIdentity);

        if (!strategyRunCadencePolicy.isDue(
                strategyIdentity,
                evaluatedAt,
                latestRunStartedAt
        )) {
            log.info(
                    "Harness scheduler skipped strategy. strategyId={}, evaluatedAt={}, latestRunStartedAt={}",
                    strategyIdentity.strategyId(),
                    evaluatedAt,
                    latestRunStartedAt.orElse(null)
            );
            return;
        }

        log.info(
                "Harness scheduler triggered. strategyId={}, evaluatedAt={}",
                strategyIdentity.strategyId(),
                evaluatedAt
        );

        HarnessRunResult result = investmentHarness.run(strategyIdentity);

        log.info(
                "Harness scheduler completed. runId={}, status={}",
                result.runId(),
                result.status()
        );
    }
}
