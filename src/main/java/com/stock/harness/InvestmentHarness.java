package com.stock.harness;

import com.stock.agent.AgentNextAction;
import com.stock.agent.AgentNextActionType;
import com.stock.agent.InvestmentAgent;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.agent.validation.HarnessAgentActionValidationResult;
import com.stock.harness.agent.validation.HarnessAgentActionValidationStatus;
import com.stock.harness.agent.validation.HarnessAgentActionValidator;
import com.stock.harness.execution.HarnessAgentLoopResult;
import com.stock.harness.execution.limit.HarnessAgentStepBudget;
import com.stock.harness.execution.limit.HarnessProviderCallBudget;
import com.stock.harness.execution.limit.HarnessToolCallBudget;
import com.stock.harness.execution.retry.HarnessRetryWaiter;
import com.stock.harness.execution.retry.HarnessToolRetryPolicy;
import com.stock.harness.execution.tool.HarnessToolRequestTracker;
import com.stock.harness.tool.*;
import com.stock.harness.tool.validation.HarnessToolRequestValidationResult;
import com.stock.harness.tool.validation.HarnessToolRequestValidationStatus;
import com.stock.harness.tool.validation.HarnessToolRequestValidator;
import com.stock.harness.tool.validation.HarnessToolResultValidationResult;
import com.stock.harness.tool.validation.HarnessToolResultValidationStatus;
import com.stock.harness.tool.validation.HarnessToolResultValidator;
import com.stock.market.MarketService;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.observation.CurrentPriceObservationService;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskGuard;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.universe.StrategyStockUniverseRegistry;
import com.stock.trade.TradeExecutor;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final HarnessToolResultValidator harnessToolResultValidator;
    private final HarnessAgentActionValidator harnessAgentActionValidator;
    private final HarnessToolRequestValidator harnessToolRequestValidator;
    private final HarnessRetryWaiter harnessRetryWaiter;
    private final StrategyStockUniverseRegistry strategyStockUniverseRegistry;
    private final CurrentPriceObservationService currentPriceObservationService;

    public HarnessRunResult run(InvestmentStrategyIdentity strategyIdentity) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");
        LocalDateTime startedAt = LocalDateTime.now();
        log.info(
                "Investment Harness started. strategyId={}, strategyVersion={}, horizon={}",
                strategyIdentity.strategyId(),
                strategyIdentity.strategyVersion(),
                strategyIdentity.horizon()
        );

        String runId = UUID.randomUUID().toString();
        HarnessStepRecorder stepRecorder = new HarnessStepRecorder();
        List<HarnessToolExecutionResult> recordedToolResults = new ArrayList<>();
        List<String> candidateSymbols = List.of();

        try {
            candidateSymbols = strategyStockUniverseRegistry.getCandidateSymbols(
                    strategyIdentity
            );
            PortfolioSnapshot portfolioSnapshot = stepRecorder.record(
                    HarnessStepType.LOAD_PORTFOLIO,
                    () -> portfolioService.getCurrentSnapshot(strategyIdentity),
                    "Portfolio loading complete."
            );
            MarketSnapshot marketSnapshot = stepRecorder.record(
                    HarnessStepType.LOAD_MARKET,
                    marketService::getCurrentSnapshot,
                    "Market loading complete."
            );

            HarnessRunContext context = createContext(
                    runId,
                    strategyIdentity,
                    portfolioSnapshot,
                    marketSnapshot,
                    candidateSymbols
            );
            HarnessAgentStepBudget agentStepBudget = new HarnessAgentStepBudget(
                    context.limits().maxSteps()
            );
            HarnessToolCallBudget toolCallBudget = new HarnessToolCallBudget(
                    context.limits().maxToolCalls()
            );
            HarnessProviderCallBudget providerCallBudget = new HarnessProviderCallBudget(
                    context.limits().maxProviderCalls()
            );
            HarnessToolRetryPolicy toolRetryPolicy = new HarnessToolRetryPolicy(
                    context.limits().maxToolRetries()
            );
            HarnessToolRequestTracker toolRequestTracker = new HarnessToolRequestTracker();

            HarnessAgentLoopResult agentLoopResult = resolveInvestmentDecision(
                    context,
                    stepRecorder,
                    agentStepBudget,
                    toolCallBudget,
                    providerCallBudget,
                    toolRetryPolicy,
                    toolRequestTracker,
                    recordedToolResults
            );
            InvestmentDecision decision = agentLoopResult.decision();

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
                    () -> tradeExecutor.execute(
                            runId,
                            strategyIdentity,
                            decision,
                            riskCheckResult
                    ),
                    result -> result.status() == TradeStatus.REJECTED
                            ? HarnessStepStatus.FAILED
                            : HarnessStepStatus.COMPLETED,
                    TradeResult::reason
            );

            PortfolioSnapshot finalPortfolioSnapshot = stepRecorder.record(
                    HarnessStepType.LOAD_FINAL_PORTFOLIO,
                    () -> portfolioService.getCurrentSnapshot(strategyIdentity),
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
                    "Investment Harness finished. runId={}, strategyId={}, status={}, decision={}, riskStatus={}, tradeStatus={}",
                    context.runId(),
                    context.strategyIdentity().strategyId(),
                    runStatus,
                    decision.action(),
                    riskCheckResult.status(),
                    tradeResult.status()
            );

            HarnessRunResult result = HarnessRunResult.of(
                    context.runId(),
                    context.strategyIdentity(),
                    runStatus,
                    startedAt,
                    finishedAt,
                    candidateSymbols,
                    steps,
                    agentLoopResult.toolResults(),
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
                    strategyIdentity,
                    startedAt,
                    e,
                    candidateSymbols,
                    stepRecorder.steps(),
                    recordedToolResults
            );

            harnessRunHistoryService.record(result);

            return result;
        }
    }

    private HarnessRunContext createContext(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot,
            List<String> candidateSymbols
    ) {
        HarnessRunLimits limits = new HarnessRunLimits(
                harnessProperties.maxSteps(),
                harnessProperties.maxToolCalls(),
                harnessProperties.maxToolRetries(),
                harnessProperties.maxProviderCalls()
        );

        HarnessAllowedTools allowedTools = HarnessAllowedTools.readOnly();

        return new HarnessRunContext(
                runId,
                strategyIdentity,
                limits,
                allowedTools,
                portfolioSnapshot,
                marketSnapshot,
                candidateSymbols,
                List.of()
        );
    }

    private HarnessRunStatus determineRunStatus(List<HarnessStepResult> steps) {
        boolean hasTerminalFailedStep = steps.stream()
                .anyMatch(step -> step.status() == HarnessStepStatus.FAILED
                        && step.type() != HarnessStepType.EXECUTE_TOOL_REQUEST);

        return hasTerminalFailedStep
                ? HarnessRunStatus.FAILED
                : HarnessRunStatus.COMPLETED;
    }

    private HarnessRunResult createFailedResult(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            LocalDateTime startedAt,
            Exception e,
            List<String> candidateSymbols,
            List<HarnessStepResult> recordedSteps,
            List<HarnessToolExecutionResult> recordedToolResults
    ) {
        LocalDateTime finishedAt = LocalDateTime.now();

        log.error(
                "Investment Harness failed. runId={}, strategyId={}",
                runId,
                strategyIdentity.strategyId(),
                e
        );

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
                strategyIdentity,
                startedAt,
                finishedAt,
                candidateSymbols,
                steps,
                recordedToolResults
        );
    }

    private HarnessAgentLoopResult resolveInvestmentDecision(
            HarnessRunContext context,
            HarnessStepRecorder stepRecorder,
            HarnessAgentStepBudget agentStepBudget,
            HarnessToolCallBudget toolCallBudget,
            HarnessProviderCallBudget providerCallBudget,
            HarnessToolRetryPolicy toolRetryPolicy,
            HarnessToolRequestTracker toolRequestTracker,
            List<HarnessToolExecutionResult> recordedToolResults
    ) {
        HarnessRunContext currentContext = context;

        while (true) {
            AgentNextAction action = runInvestmentAgent(
                    currentContext,
                    stepRecorder,
                    agentStepBudget
            );

            HarnessAgentActionValidationResult actionValidationResult = stepRecorder.record(
                    HarnessStepType.VALIDATE_AGENT_ACTION,
                    () -> harnessAgentActionValidator.validate(action),
                    result -> result.status() == HarnessAgentActionValidationStatus.VALID
                            ? HarnessStepStatus.COMPLETED
                            : HarnessStepStatus.FAILED,
                    HarnessAgentActionValidationResult::reason
            );

            if (actionValidationResult.status() != HarnessAgentActionValidationStatus.VALID) {
                throw new IllegalStateException(
                        actionValidationResult.reason()
                        + " reasonCode="
                        + actionValidationResult.reasonCode()
                );
            }

            if (action.type() == AgentNextActionType.FINAL_DECISION) {
                return new HarnessAgentLoopResult(
                        action.investmentDecision(),
                        recordedToolResults
                );
            }

            HarnessToolRequestValidationResult requestValidationResult = stepRecorder.record(
                    HarnessStepType.VALIDATE_TOOL_REQUEST,
                    () -> harnessToolRequestValidator.validate(action.toolRequest()),
                    result -> result.status() == HarnessToolRequestValidationStatus.VALID
                            ? HarnessStepStatus.COMPLETED
                            : HarnessStepStatus.FAILED,
                    HarnessToolRequestValidationResult::reason
            );

            if (requestValidationResult.status() != HarnessToolRequestValidationStatus.VALID) {
                throw new IllegalStateException(
                        requestValidationResult.reason()
                        + " reasonCode="
                        + requestValidationResult.reasonCode()
                );
            }

            HarnessAllowedTools allowedTools = currentContext.allowedTools();
            HarnessToolAuthorizationResult authorizationResult = stepRecorder.record(
                    HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                    () -> harnessToolAuthorizer.authorize(
                            allowedTools,
                            action.toolRequest()
                    ),
                    result -> result.status() == HarnessToolAuthorizationStatus.ALLOWED
                            ? HarnessStepStatus.COMPLETED
                            : HarnessStepStatus.FAILED,
                    HarnessToolAuthorizationResult::reason
            );

            if (authorizationResult.status() != HarnessToolAuthorizationStatus.ALLOWED) {
                HarnessToolExecutionResult executionResult = HarnessToolExecutionResult.authorizationDenied(
                        action.toolRequest()
                );
                recordedToolResults.add(executionResult);

                throw new IllegalStateException(
                        executionResult.reason() + " type=" + executionResult.type()
                );
            }

            checkDuplicateToolRequest(
                    stepRecorder,
                    toolRequestTracker,
                    action.toolRequest(),
                    recordedToolResults
            );

            HarnessToolExecutionResult executionResult = executeToolRequest(
                    currentContext.strategyIdentity(),
                    action.toolRequest(),
                    stepRecorder,
                    toolCallBudget,
                    providerCallBudget,
                    toolRetryPolicy,
                    recordedToolResults
            );

            if (executionResult.status() != HarnessToolExecutionStatus.EXECUTED) {
                throw new IllegalStateException(
                        executionResult.reason() + " type=" + executionResult.type()
                );
            }

            HarnessToolResultValidationResult validationResult = stepRecorder.record(
                    HarnessStepType.VALIDATE_TOOL_RESULT,
                    () -> harnessToolResultValidator.validate(
                            action.toolRequest(),
                            executionResult
                    ),
                    result -> result.status() == HarnessToolResultValidationStatus.VALID
                            ? HarnessStepStatus.COMPLETED
                            : HarnessStepStatus.FAILED,
                    HarnessToolResultValidationResult::reason
            );

            if (validationResult.status() != HarnessToolResultValidationStatus.VALID) {
                throw new IllegalStateException(
                        validationResult.reason()
                        + " reasonCode="
                        + validationResult.reasonCode()
                );
            }

            recordValidatedToolObservation(
                    currentContext,
                    executionResult
            );
            currentContext = currentContext.withToolResult(executionResult);
        }
    }

    private void recordValidatedToolObservation(
            HarnessRunContext context,
            HarnessToolExecutionResult executionResult
    ) {
        if (executionResult.type() != HarnessToolType.GET_CURRENT_PRICE) {
            return;
        }

        HarnessToolOutput output = executionResult.output();
        currentPriceObservationService.record(
                context.runId(),
                context.strategyIdentity(),
                output.currentPriceSnapshot(),
                output.currentPriceSource()
        );
    }

    private HarnessToolExecutionResult executeToolRequest(
            InvestmentStrategyIdentity strategyIdentity,
            HarnessToolRequest request,
            HarnessStepRecorder stepRecorder,
            HarnessToolCallBudget toolCallBudget,
            HarnessProviderCallBudget providerCallBudget,
            HarnessToolRetryPolicy toolRetryPolicy,
            List<HarnessToolExecutionResult> recordedToolResults
    ) {
        int usedRetries = 0;

        while (true) {
            consumeToolCallBudget(stepRecorder, toolCallBudget);

            HarnessToolExecutionResult executionResult = stepRecorder.record(
                    HarnessStepType.EXECUTE_TOOL_REQUEST,
                    () -> harnessToolExecutor.execute(
                            strategyIdentity,
                            request,
                            providerCallBudget
                    ),
                    result -> result.status() == HarnessToolExecutionStatus.EXECUTED
                            ? HarnessStepStatus.COMPLETED
                            : HarnessStepStatus.FAILED,
                    HarnessToolExecutionResult::reason
            );
            recordedToolResults.add(executionResult);

            if (!toolRetryPolicy.shouldRetry(executionResult, usedRetries)) {
                return executionResult;
            }

            stepRecorder.record(
                    HarnessStepType.WAIT_TOOL_RETRY,
                    harnessRetryWaiter::waitBeforeRetry,
                    delay -> "Tool retry wait completed. delay=" + delay
            );
            usedRetries++;
        }
    }

    private void checkDuplicateToolRequest(
            HarnessStepRecorder stepRecorder,
            HarnessToolRequestTracker toolRequestTracker,
            HarnessToolRequest request,
            List<HarnessToolExecutionResult> recordedToolResults
    ) {
        if (!toolRequestTracker.tryRegister(request)) {
            HarnessToolExecutionResult executionResult =
                    HarnessToolExecutionResult.duplicateRequest(request);
            recordedToolResults.add(executionResult);

            String message = executionResult.reason() + " type=" + request.type();
            stepRecorder.failed(HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST, message);
            throw new IllegalStateException(message);
        }

        stepRecorder.completed(
                HarnessStepType.CHECK_DUPLICATE_TOOL_REQUEST,
                "Tool request is not duplicate. type=" + request.type()
        );
    }

    private void consumeToolCallBudget(
            HarnessStepRecorder stepRecorder,
            HarnessToolCallBudget toolCallBudget
    ) {
        if (!toolCallBudget.tryConsume()) {
            String message = "Tool call limit exceeded. used="
                    + toolCallBudget.usedCalls()
                    + ", max="
                    + toolCallBudget.maxCalls();

            stepRecorder.failed(HarnessStepType.CHECK_TOOL_CALL_LIMIT, message);
            throw new IllegalStateException(message);
        }

        stepRecorder.completed(
                HarnessStepType.CHECK_TOOL_CALL_LIMIT,
                "Tool call allowed. used="
                + toolCallBudget.usedCalls()
                + ", max="
                + toolCallBudget.maxCalls()
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
        if (action == null || action.type() == null) {
            return "Investment agent returned an invalid action.";
        }

        if (action.type() == AgentNextActionType.FINAL_DECISION) {
            return action.investmentDecision() == null
                    ? "Investment agent returned an invalid final decision action."
                    : action.investmentDecision().reason();
        }

        return action.toolRequest() == null
                ? "Investment agent returned an invalid tool request action."
                : "Requested tool. type=" + action.toolRequest().type();
    }
}
