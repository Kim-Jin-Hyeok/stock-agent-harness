package com.stock.harness;

import com.stock.agent.AgentNextAction;
import com.stock.agent.AgentNextActionType;
import com.stock.agent.InvestmentAgent;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.execution.limit.HarnessAgentStepBudget;
import com.stock.harness.tool.*;
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
    private final HarnessToolAuthorizer harnessToolAuthorizer;
    private final HarnessToolExecutor harnessToolExecutor;

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
            HarnessAgentStepBudget agentStepBudget = new HarnessAgentStepBudget(
                    context.limits().maxSteps()
            );

            AgentNextAction agentNextAction = runInvestmentAgent(
                    context,
                    stepRecorder,
                    agentStepBudget
            );

            InvestmentDecision decision = resolvedInvestmentDecision(
                    agentNextAction,
                    context,
                    stepRecorder,
                    agentStepBudget
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

            List<HarnessStepResult> steps = stepRecorder.steps();

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
                marketSnapshot,
                List.of()
        );
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

    private InvestmentDecision resolvedInvestmentDecision(
            AgentNextAction action,
            HarnessRunContext context,
            HarnessStepRecorder stepRecorder,
            HarnessAgentStepBudget agentStepBudget
    ) {
        if (action.type() == AgentNextActionType.FINAL_DECISION) {
            return action.investmentDecision();
        }

        HarnessToolAuthorizationResult authorizationResult = stepRecorder.record(
                HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                () -> harnessToolAuthorizer.authorize(
                        context.allowedTools(),
                        action.toolRequest()
                ),
                result -> result.status() == HarnessToolAuthorizationStatus.ALLOWED
                        ? HarnessStepStatus.COMPLETED
                        : HarnessStepStatus.FAILED,
                HarnessToolAuthorizationResult::reason
        );

        if (authorizationResult.status() != HarnessToolAuthorizationStatus.ALLOWED) {
            HarnessToolExecutionResult executionResult = HarnessToolExecutionResult.authorizationDenied(
                    authorizationResult.type()
            );

            throw new IllegalStateException(
                    executionResult.reason() + " type=" + executionResult.type()
            );
        }

        HarnessToolExecutionResult executionResult = stepRecorder.record(
                HarnessStepType.EXECUTE_TOOL_REQUEST,
                () -> harnessToolExecutor.execute(action.toolRequest()),
                result -> result.status() == HarnessToolExecutionStatus.EXECUTED
                        ? HarnessStepStatus.COMPLETED
                        : HarnessStepStatus.FAILED,
                HarnessToolExecutionResult::reason
        );

        if (executionResult.status() == HarnessToolExecutionStatus.EXECUTED) {
            HarnessRunContext updatedContext = context.withToolResult(executionResult);
            AgentNextAction nextAction = runInvestmentAgent(
                    updatedContext,
                    stepRecorder,
                    agentStepBudget
            );

            if (nextAction.type() == AgentNextActionType.FINAL_DECISION) {
                return nextAction.investmentDecision();
            }

            throw new IllegalStateException(
                    "Multiple tool requests are not supported yet. type="
                    + nextAction.toolRequest().type()
            );
        }

        throw new IllegalStateException(
                executionResult.reason() + " type=" + executionResult.type()
        );
    }

    private AgentNextAction runInvestmentAgent(
            HarnessRunContext context,
            HarnessStepRecorder stepRecorder,
            HarnessAgentStepBudget agentStepBudget
    ) {
        if (!agentStepBudget.tryConsume()) {
            String message = "Agent step limit exceeded. used="
                    + agentStepBudget.usedSteps()
                    + ", max="
                    + agentStepBudget.maxSteps();

            stepRecorder.failed(HarnessStepType.CHECK_STEP_LIMIT, message);
            throw new IllegalStateException(message);
        }

        stepRecorder.completed(
                HarnessStepType.CHECK_STEP_LIMIT,
                "Agent step allowed. used="
                + agentStepBudget.usedSteps()
                + ", max="
                + agentStepBudget.maxSteps()
        );

        return stepRecorder.record(
                HarnessStepType.RUN_INVESTMENT_AGENT,
                () -> investmentAgent.next(context),
                this::agentNextActionMessage
        );
    }

    private String agentNextActionMessage(AgentNextAction action) {
        if (action.type() == AgentNextActionType.FINAL_DECISION) {
            return action.investmentDecision().reason();
        }

        return "Requested tool. type=" + action.toolRequest().type();
    }
}
