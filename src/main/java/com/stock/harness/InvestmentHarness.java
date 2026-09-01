package com.stock.harness;

import com.stock.agent.InvestmentAgent;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.tool.HarnessAllowedTools;
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
import java.util.ArrayList;
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
        HarnessStepRecorder stepRecorder = new HarnessStepRecorder();

        try {
            PortfolioSnapshot portfolioSnapshot = stepRecorder.record(
                    HarnessStepType.LOAD_PORTFOLIO,
                    portfolioService::getCurrentSnapshot,
                    "Portfolio loading complete."
            );
            MarketSnapshot marketSnapshot = stepRecorder.record(
                    HarnessStepType.LOAD_MARKET,
                    marketService::getCurrentSnapshot,
                    "Market loading complete."
            );

            HarnessRunContext context = createContext(
                    runId,
                    portfolioSnapshot,
                    marketSnapshot
            );

            InvestmentDecision decision = stepRecorder.record(
                    HarnessStepType.RUN_INVESTMENT_AGENT,
                    () -> investmentAgent.decide(context),
                    InvestmentDecision::reason
            );

            RiskCheckResult riskCheckResult = stepRecorder.record(
                    HarnessStepType.VALIDATE_DECISION,
                    () -> riskGuard.validate(decision, context.portfolioSnapshot()),
                    result -> result.status() == RiskCheckStatus.APPROVED
                            ? HarnessStepStatus.COMPLETED
                            : HarnessStepStatus.FAILED,
                    RiskCheckResult::reason
            );

            TradeResult tradeResult = stepRecorder.record(
                    HarnessStepType.EXECUTE_TRADE,
                    () -> tradeExecutor.execute(runId, decision, riskCheckResult),
                    result -> result.status() == TradeStatus.REJECTED
                            ? HarnessStepStatus.FAILED
                            : HarnessStepStatus.COMPLETED,
                    TradeResult::reason
            );

            PortfolioSnapshot finalPortfolioSnapshot = stepRecorder.record(
                    HarnessStepType.LOAD_FINAL_PORTFOLIO,
                    portfolioService::getCurrentSnapshot,
                    "Final portfolio loading complete."
            );

            List<HarnessStepResult> steps = recordSteps(
                    stepRecorder,
                    context
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
                    e,
                    stepRecorder.steps()
            );

            harnessRunHistoryService.record(result);

            return result;
        }
    }

    private HarnessRunContext createContext(
            String runId,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot
    ) {
        HarnessRunLimits limits = new HarnessRunLimits(
                harnessProperties.maxSteps()
        );

        HarnessAllowedTools allowedTools = HarnessAllowedTools.readOnly();

        return new HarnessRunContext(
                runId,
                limits,
                allowedTools,
                portfolioSnapshot,
                marketSnapshot
        );
    }

    private List<HarnessStepResult> recordSteps(
            HarnessStepRecorder stepRecorder,
            HarnessRunContext context
    ) {
        recordStepLimitCheck(stepRecorder, context);

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
            Exception e,
            List<HarnessStepResult> recordedSteps
    ) {
        LocalDateTime finishedAt = LocalDateTime.now();

        log.error("Investment Harness failed. runId={}", runId, e);

        String failureMessage = e.getMessage() != null
                ? e.getMessage()
                : e.getClass().getSimpleName();

        LocalDateTime recordedAt = LocalDateTime.now();

        List<HarnessStepResult> steps = new ArrayList<>(recordedSteps);
        steps.add(new HarnessStepResult(
                HarnessStepType.RUN_FAILED,
                HarnessStepStatus.FAILED,
                failureMessage,
                recordedAt,
                recordedAt
        ));

        return HarnessRunResult.failed(
                runId,
                startedAt,
                finishedAt,
                steps
        );
    }

    private void recordStepLimitCheck(
            HarnessStepRecorder stepRecorder,
            HarnessRunContext context
    ) {
        int executableStepCount = stepRecorder.size();
        int finalStepCount = executableStepCount + 1;
        boolean stepLimitExceeded = finalStepCount > context.limits().maxSteps();

        String stepLimitMessage = "Executable steps: "
                + executableStepCount
                + ", final steps: "
                + finalStepCount
                + ", max steps: "
                + context.limits().maxSteps();

        if (stepLimitExceeded) {
            stepRecorder.failed(HarnessStepType.CHECK_STEP_LIMIT, stepLimitMessage);
        } else {
            stepRecorder.completed(HarnessStepType.CHECK_STEP_LIMIT, stepLimitMessage);
        }
    }
}
