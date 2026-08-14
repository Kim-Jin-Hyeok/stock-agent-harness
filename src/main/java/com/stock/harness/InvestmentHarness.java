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
    private final HarnessRunHistoryService harnessRunHistoryService;
    private final InvestmentAgent investmentAgent;
    private final HarnessProperties harnessProperties;

    public HarnessRunResult run() {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("Investment Harness started.");

        String runId = UUID.randomUUID().toString();

        try {
            HarnessRunContext context = createContext(runId);

            InvestmentDecision decision = investmentAgent.decide(context);

            RiskCheckResult riskCheckResult = riskGuard.validate(
                    decision,
                    context.portfolioSnapshot()
            );

            TradeResult tradeResult = tradeExecutor.execute(runId, decision, riskCheckResult);

            PortfolioSnapshot finalPortfolioSnapshot = portfolioService.getCurrentSnapshot();

            List<HarnessStepResult> steps = recordSteps(
                    context,
                    decision,
                    riskCheckResult,
                    tradeResult
            );

            HarnessRunStatus runStatus = determineRunStatus(steps);

            steps.forEach(step -> log.info(
                    "Harness step recorded. type={}, status={}, message={}",
                    step.type(),
                    step.status(),
                    step.message())
            );

            LocalDateTime finishedAt = LocalDateTime.now();

            log.info(
                    "Investment Harness finished. runId={}, status={}, decision={}, riskStatus={}, tradeStatus={}",
                    context.runId(),
                    runStatus,
                    decision.action(),
                    riskCheckResult.status(),
                    tradeResult.status()
            );

            HarnessRunResult result = HarnessRunResult.of(
                    context.runId(),
                    runStatus,
                    startedAt,
                    finishedAt,
                    steps,
                    decision,
                    riskCheckResult,
                    tradeResult,
                    finalPortfolioSnapshot,
                    context.marketSnapshot()
            );

            harnessRunHistoryService.record(result);

            return result;
        } catch (Exception e) {
            HarnessRunResult result = createFailedResult(
                    runId,
                    startedAt,
                    e
            );

            harnessRunHistoryService.record(result);

            return result;
        }
    }

    private HarnessRunContext createContext(String runId) {
        PortfolioSnapshot portfolioSnapshot = portfolioService.getCurrentSnapshot();
        MarketSnapshot marketSnapshot = marketService.getCurrentSnapshot();

        return new HarnessRunContext(
                runId,
                harnessProperties.maxSteps(),
                portfolioSnapshot,
                marketSnapshot
        );
    }

    private List<HarnessStepResult> recordSteps(
            HarnessRunContext context,
            InvestmentDecision decision,
            RiskCheckResult riskCheckResult,
            TradeResult tradeResult
    ) {
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

        boolean stepLimitExceeded = stepRecorder.size() > context.maxSteps();
        String stepLimitMessage = "Executable steps: "
                + stepRecorder.size()
                + ", max steps: "
                + context.maxSteps();

        if (stepLimitExceeded) {
            stepRecorder.failed(HarnessStepType.CHECK_STEP_LIMIT, stepLimitMessage);
        } else {
            stepRecorder.completed(HarnessStepType.CHECK_STEP_LIMIT, stepLimitMessage);
        }

        return stepRecorder.steps();
    }

    private HarnessRunStatus determineRunStatus(List<HarnessStepResult> steps) {
        boolean hasFailedStep = steps.stream()
                .anyMatch(step -> step.status() == HarnessStepStatus.FAILED);

        return hasFailedStep
                ? HarnessRunStatus.FAILED
                : HarnessRunStatus.COMPLETED;
    }

    private HarnessRunResult createFailedResult(
            String runId,
            LocalDateTime startedAt,
            Exception e
    ) {
        LocalDateTime finishedAt = LocalDateTime.now();

        log.error("Investment Harness failed. runId={}", runId, e);

        String failureMessage = e.getMessage() != null
                ? e.getMessage()
                : e.getClass().getSimpleName();

        List<HarnessStepResult> steps = List.of(
                new HarnessStepResult(
                        HarnessStepType.RUN_FAILED,
                        HarnessStepStatus.FAILED,
                        failureMessage
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
