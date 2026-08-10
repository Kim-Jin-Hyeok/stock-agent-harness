package com.stock.harness;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class InvestmentHarness {

    public HarnessRunResult run() {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("Investment Harness started.");

        List<String> steps = List.of(
                "LOAD_PORTFOLIO",
                "LOAD_MARKET",
                "RUN_INVESTMENT_AGENT",
                "VALIDATE_DECISION",
                "EXECUTE_VIRTUAL_TRADE"
        );

        steps.forEach(step -> log.info("Harness step: {}", step));

        LocalDateTime finishedAt = LocalDateTime.now();

        log.info("Investment Harness finished.");

        return new HarnessRunResult(
                UUID.randomUUID().toString(),
                HarnessRunStatus.COMPLETED,
                startedAt,
                finishedAt,
                steps,
                new InvestmentDecision(InvestmentAction.HOLD, "Initial harness run uses fixed HOLD decision.")
        );
    }
}
