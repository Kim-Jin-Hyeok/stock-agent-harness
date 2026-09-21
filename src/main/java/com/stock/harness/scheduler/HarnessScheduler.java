package com.stock.harness.scheduler;

import com.stock.harness.HarnessRunHistoryService;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.InvestmentHarness;
import com.stock.harness.scheduler.config.HarnessSchedulerProperties;
import com.stock.harness.scheduler.config.ScheduledStrategyProperties;
import com.stock.harness.scheduler.policy.StrategyExecutionDatePolicy;
import com.stock.harness.scheduler.policy.StrategyRunCadencePolicy;
import com.stock.harness.scheduler.policy.StrategyRunWindowPolicy;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class HarnessScheduler {
    private final InvestmentHarness investmentHarness;
    private final HarnessSchedulerProperties harnessSchedulerProperties;
    private final HarnessRunHistoryService harnessRunHistoryService;
    private final StrategyRunCadencePolicy strategyRunCadencePolicy;
    private final StrategyRunWindowPolicy strategyRunWindowPolicy;
    private final StrategyExecutionDatePolicy strategyExecutionDatePolicy;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${harness.scheduler.fixed-delay-ms}")
    public void run() {
        if (!harnessSchedulerProperties.enabled()) {
            log.debug("Harness scheduler is disabled");
            return;
        }

        LocalDateTime evaluatedAt = LocalDateTime.ofInstant(
                clock.instant(),
                harnessSchedulerProperties.schedulerZoneId()
        );

        for (ScheduledStrategyProperties strategy : harnessSchedulerProperties.strategies()) {
            if (!strategy.enabled()) {
                continue;
            }

            InvestmentStrategyIdentity strategyIdentity = strategy.strategyIdentity();
            if (!strategyExecutionDatePolicy.isExecutionDate(
                    strategyIdentity,
                    evaluatedAt.toLocalDate()
            )) {
                log.info(
                        "Harness scheduler skipped strategy outside execution date. strategyId={}, evaluatedAt={}",
                        strategy.strategyId(),
                        evaluatedAt
                );
                continue;
            }

            if (!strategyRunWindowPolicy.isWithinWindow(strategy.runWindow(), evaluatedAt)) {
                log.info(
                        "Harness scheduler skipped strategy outside run window. strategyId={}, evaluatedAt={}",
                        strategy.strategyId(),
                        evaluatedAt
                );
                continue;
            }

            runStrategy(strategyIdentity, evaluatedAt);
        }
    }

    private void runStrategy(
            InvestmentStrategyIdentity strategyIdentity,
            LocalDateTime evaluatedAt
    ) {
        var latestRunStartedAt = harnessRunHistoryService.getLatestRunStartedAt(strategyIdentity);

        if (!strategyRunCadencePolicy.isDue(strategyIdentity, evaluatedAt, latestRunStartedAt)) {
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
                "Harness scheduler completed. runId={}, strategyId={}, status={}",
                result.runId(),
                strategyIdentity.strategyId(),
                result.status()
        );
    }
}
