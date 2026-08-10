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

        List<String> steps = List.of(
                "LOAD_PORTFOLIO",
                "LOAD_MARKET",
                "RUN_INVESTMENT_AGENT",
                "VALIDATE_DECISION",
                "EXECUTE_TRADE"
        );

        steps.forEach(step -> log.info("Harness step: {}", step));

        InvestmentDecision decision = new InvestmentDecision(
                InvestmentAction.HOLD,
                "Initial harness run uses fixed HOLD decision.");

        RiskCheckResult riskCheckResult = riskGuard.validate(decision);

        TradeResult tradeResult = tradeExecutor.execute(decision, riskCheckResult);

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
