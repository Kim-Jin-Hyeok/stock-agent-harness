package com.stock.harness;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskGuard;
import com.stock.trade.TradeExecutor;
import com.stock.trade.TradeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentHarness {
    private final RiskGuard riskGuard;
    private final TradeExecutor tradeExecutor;

    public HarnessRunResult run() {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("Investment Harness started.");

        InvestmentDecision decision = new InvestmentDecision(
                InvestmentAction.HOLD,
                "Initial harness run uses fixed HOLD decision.");

        RiskCheckResult riskCheckResult = riskGuard.validate(decision);

        TradeResult tradeResult = tradeExecutor.execute(decision, riskCheckResult);

        List<HarnessStepResult> steps = List.of(
                new HarnessStepResult(HarnessStepType.LOAD_PORTFOLIO, HarnessStepStatus.SKIPPED, "Portfolio loading is not implemented yet."),
                new HarnessStepResult(HarnessStepType.LOAD_MARKET, HarnessStepStatus.SKIPPED, "Market loading is not implemented yet."),
                new HarnessStepResult(HarnessStepType.RUN_INVESTMENT_AGENT, HarnessStepStatus.COMPLETED, "Fixed HOLD decision was created."),
                new HarnessStepResult(HarnessStepType.VALIDATE_DECISION, HarnessStepStatus.COMPLETED, "Risk guard validated the decision."),
                new HarnessStepResult(HarnessStepType.EXECUTE_TRADE, HarnessStepStatus.COMPLETED, "Trade executor processed the decision.")
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
                decision,
                riskCheckResult,
                tradeResult
        );
    }
}
