package com.stock.agent.provider.rulebased.swing.v1;

import com.stock.agent.AgentNextAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.policy.SwingV1ActionPolicy;
import com.stock.agent.decision.swing.v1.quantity.policy.SwingV1OrderQuantityPolicy;
import com.stock.agent.decision.swing.v1.quantity.result.SwingV1OrderQuantityResult;
import com.stock.agent.decision.swing.v1.resolution.SwingV1DecisionResolver;
import com.stock.agent.decision.swing.v1.result.SwingV1ActionPolicyResult;
import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.harness.HarnessRunContext;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.valuation.PortfolioValuationService;
import com.stock.portfolio.valuation.PortfolioValuationSnapshot;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisResult;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisService;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SwingV1AgentNextActionProvider
        implements AgentNextActionProvider {
    private final SwingTechnicalAnalysisService analysisService;
    private final SwingV1ActionPolicy actionPolicy;
    private final PortfolioValuationService portfolioValuationService;
    private final SwingV1OrderQuantityPolicy orderQuantityPolicy;
    private final SwingV1DecisionResolver decisionResolver;
    private final Clock clock;

    public SwingV1AgentNextActionProvider(
            SwingTechnicalAnalysisService analysisService,
            SwingV1ActionPolicy actionPolicy,
            PortfolioValuationService portfolioValuationService,
            SwingV1OrderQuantityPolicy orderQuantityPolicy,
            SwingV1DecisionResolver decisionResolver,
            Clock clock
    ) {
        this.analysisService = Objects.requireNonNull(
                analysisService,
                "analysisService must not be null."
        );
        this.actionPolicy = Objects.requireNonNull(
                actionPolicy,
                "actionPolicy must not be null."
        );
        this.portfolioValuationService = Objects.requireNonNull(
                portfolioValuationService,
                "portfolioValuationService must not be null."
        );
        this.orderQuantityPolicy = Objects.requireNonNull(
                orderQuantityPolicy,
                "orderQuantityPolicy must not be null."
        );
        this.decisionResolver = Objects.requireNonNull(
                decisionResolver,
                "decisionResolver must not be null."
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null."
        );
    }

    @Override
    public AgentNextAction next(HarnessRunContext context) {
        String candidateSymbol = firstCandidateSymbol(context);
        Optional<HarnessToolExecutionResult> historyResult =
                findDailyPriceHistoryResult(context, candidateSymbol);
        if (historyResult.isEmpty()) {
            return AgentNextAction.requestTool(
                    HarnessToolRequest.dailyPriceHistory(candidateSymbol)
            );
        }

        List<String> requiredPriceSymbols = requiredPriceSymbols(
                context.portfolioSnapshot(),
                candidateSymbol
        );
        Optional<String> missingPriceSymbol = requiredPriceSymbols.stream()
                .filter(symbol ->
                        findCurrentPriceResult(context, symbol).isEmpty()
                )
                .findFirst();
        if (missingPriceSymbol.isPresent()) {
            return AgentNextAction.requestTool(
                    HarnessToolRequest.currentPrice(
                            missingPriceSymbol.get()
                    )
            );
        }

        DailyPriceHistory history = historyResult.get()
                .output()
                .dailyPriceHistory();
        SwingTechnicalAnalysisResult analysis = analysisService.analyze(
                context.strategyIdentity(),
                history
        );
        InvestmentDecision decision = decide(
                context,
                candidateSymbol,
                requiredPriceSymbols,
                analysis
        );
        return AgentNextAction.finalDecision(decision);
    }

    private InvestmentDecision decide(
            HarnessRunContext context,
            String candidateSymbol,
            List<String> requiredPriceSymbols,
            SwingTechnicalAnalysisResult analysis
    ) {
        HarnessToolExecutionResult candidatePriceResult =
                findCurrentPriceResult(context, candidateSymbol)
                        .orElseThrow(() -> new IllegalStateException(
                                "Current price tool result is required. "
                                        + "symbol="
                                        + candidateSymbol
                        ));
        CurrentPriceSnapshot candidatePrice = candidatePriceResult
                .output()
                .currentPriceSnapshot();
        List<CurrentPriceSnapshot> currentPrices = requiredPriceSymbols
                .stream()
                .map(symbol -> findCurrentPriceResult(context, symbol)
                        .orElseThrow(() -> new IllegalStateException(
                                "Current price tool result is required. "
                                        + "symbol="
                                        + symbol
                        )))
                .map(result -> result.output().currentPriceSnapshot())
                .toList();
        Instant evaluatedAt = clock.instant();
        PortfolioValuationSnapshot portfolioValuation =
                portfolioValuationService.evaluate(
                        context.portfolioSnapshot(),
                        currentPrices,
                        evaluatedAt
                );
        SwingV1ActionPolicyResult actionResult = actionPolicy.decide(
                analysis,
                context.portfolioSnapshot(),
                candidatePrice
        );
        SwingV1OrderQuantityResult quantityResult =
                orderQuantityPolicy.calculate(
                        actionResult,
                        analysis,
                        candidatePrice,
                        portfolioValuation
                );
        SwingV1DecisionEvidence evidence = new SwingV1DecisionEvidence(
                analysis,
                candidatePrice,
                candidatePriceResult.output().currentPriceSource(),
                portfolioValuation,
                actionResult,
                quantityResult
        );
        return decisionResolver.resolve(evidence);
    }

    private String firstCandidateSymbol(HarnessRunContext context) {
        Objects.requireNonNull(context, "context must not be null.");
        if (context.candidateSymbols().isEmpty()) {
            throw new IllegalStateException(
                    "SWING_V1 agent requires at least one candidate symbol."
            );
        }
        return context.candidateSymbols().getFirst();
    }

    private List<String> requiredPriceSymbols(
            PortfolioSnapshot portfolioSnapshot,
            String candidateSymbol
    ) {
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );
        List<PortfolioPosition> positions = Objects.requireNonNull(
                portfolioSnapshot.positions(),
                "portfolioSnapshot.positions must not be null."
        );

        Set<String> symbols = new LinkedHashSet<>();
        symbols.add(candidateSymbol);
        for (PortfolioPosition position : positions) {
            Objects.requireNonNull(position, "position must not be null.");
            if (position.symbol() == null || position.symbol().isBlank()) {
                throw new IllegalArgumentException(
                        "position.symbol must not be blank."
                );
            }
            symbols.add(position.symbol());
        }
        return List.copyOf(symbols);
    }

    private Optional<HarnessToolExecutionResult> findDailyPriceHistoryResult(
            HarnessRunContext context,
            String symbol
    ) {
        return context.toolResults().stream()
                .filter(result -> result.status()
                        == HarnessToolExecutionStatus.EXECUTED)
                .filter(result -> result.type()
                        == HarnessToolType.GET_DAILY_PRICE_HISTORY)
                .filter(result -> result.request() != null)
                .filter(result -> Objects.equals(
                        result.request().symbol(),
                        symbol
                ))
                .filter(result -> result.output() != null)
                .filter(result -> result.output().dailyPriceHistory() != null)
                .filter(result -> Objects.equals(
                        result.output().dailyPriceHistory().symbol(),
                        symbol
                ))
                .findFirst();
    }

    private Optional<HarnessToolExecutionResult> findCurrentPriceResult(
            HarnessRunContext context,
            String symbol
    ) {
        return context.toolResults().stream()
                .filter(result -> result.status()
                        == HarnessToolExecutionStatus.EXECUTED)
                .filter(result -> result.type()
                        == HarnessToolType.GET_CURRENT_PRICE)
                .filter(result -> result.request() != null)
                .filter(result -> Objects.equals(
                        result.request().symbol(),
                        symbol
                ))
                .filter(result -> result.output() != null)
                .filter(result -> result.output().currentPriceSnapshot()
                        != null)
                .filter(result -> Objects.equals(
                        result.output().currentPriceSnapshot().symbol(),
                        symbol
                ))
                .findFirst();
    }
}
