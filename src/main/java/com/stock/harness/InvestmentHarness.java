package com.stock.harness;

import com.stock.agent.InvestmentAgent;
import com.stock.agent.InvestmentDecision;
import com.stock.market.MarketService;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskGuard;
import com.stock.trade.TradeExecutor;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
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
    private final PortfolioService portfolioService;
    private final MarketService marketService;
    private final InvestmentAgent investmentAgent;
    private final HarnessProperties harnessProperties;

    public HarnessRunResult run() {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("Investment Harness started.");

        String runId = UUID.randomUUID().toString();

        try {
            PortfolioSnapshot portfolioSnapshot = portfolioService.getCurrentSnapshot();
            MarketSnapshot marketSnapshot = marketService.getCurrentSnapshot();

            HarnessRunContext context = new HarnessRunContext(
                    runId,
                    portfolioSnapshot,
                    marketSnapshot
            );

            InvestmentDecision decision = investmentAgent.decide(
                    context.portfolioSnapshot(),
                    context.marketSnapshot()
            );

            RiskCheckResult riskCheckResult = riskGuard.validate(decision);

            TradeResult tradeResult = tradeExecutor.execute(decision, riskCheckResult);

            HarnessStepRecorder stepRecorder = new HarnessStepRecorder();
            stepRecorder.completed(HarnessStepType.LOAD_PORTFOLIO, "Portfolio loading complete.");
            stepRecorder.completed(HarnessStepType.LOAD_MARKET, "Market loading complete.");
            stepRecorder.completed(HarnessStepType.RUN_INVESTMENT_AGENT, decision.reason());

            if (riskCheckResult.status() == RiskCheckStatus.APPROVED) {
                stepRecorder.completed(HarnessStepType.VALIDATE_DECISION, riskCheckResult.reason());
            } else {
                stepRecorder.failed(HarnessStepType.VALIDATE_DECISION, riskCheckResult.reason());
            }

            if (tradeResult.status() == TradeStatus.REJECTED) {
                stepRecorder.failed(HarnessStepType.EXECUTE_TRADE, tradeResult.reason());
            } else {
                stepRecorder.completed(HarnessStepType.EXECUTE_TRADE, tradeResult.reason());
            }

            boolean stepLimitExceeded = stepRecorder.size() > harnessProperties.maxSteps();
            String stepLimitMessage = "Executable steps: "
                    + stepRecorder.size()
                    + ", max steps: "
                    + harnessProperties.maxSteps();

            if (stepLimitExceeded) {
                stepRecorder.failed(HarnessStepType.CHECK_STEP_LIMIT, stepLimitMessage);
            } else {
                stepRecorder.completed(HarnessStepType.CHECK_STEP_LIMIT, stepLimitMessage);
            }

            List<HarnessStepResult> steps = stepRecorder.steps();

            boolean hasFailedStep = steps.stream()
                    .anyMatch(step -> step.status() == HarnessStepStatus.FAILED);

            HarnessRunStatus runStatus = hasFailedStep
                    ? HarnessRunStatus.FAILED
                    : HarnessRunStatus.COMPLETED;

            steps.forEach(step -> log.info("Harness step: {}", step));

            LocalDateTime finishedAt = LocalDateTime.now();

            log.info(
                    "Investment Harness finished. runId={}, status={}, decision={}, riskStatus={}, tradeStatus={}",
                    context.runId(),
                    runStatus,
                    decision.action(),
                    riskCheckResult.status(),
                    tradeResult.status()
            );

            return HarnessRunResult.of(
                    context.runId(),
                    runStatus,
                    startedAt,
                    finishedAt,
                    steps,
                    decision,
                    riskCheckResult,
                    tradeResult,
                    context.portfolioSnapshot(),
                    context.marketSnapshot()
            );
        } catch (Exception e) {
            LocalDateTime finishedAt = LocalDateTime.now();

            log.error("Investment Harness failed. runId={}", runId, e);

            List<HarnessStepResult> steps = List.of(
                    new HarnessStepResult(
                            HarnessStepType.RUN_FAILED,
                            HarnessStepStatus.FAILED,
                            e.getMessage()
                    )
            );

            return HarnessRunResult.failed(
                    runId,
                    startedAt,
                    finishedAt,
                    steps
            );
        }
    }
}
