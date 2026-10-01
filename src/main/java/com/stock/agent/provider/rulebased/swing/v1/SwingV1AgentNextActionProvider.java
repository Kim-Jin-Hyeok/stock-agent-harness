package com.stock.agent.provider.rulebased.swing.v1;

import com.stock.agent.AgentNextAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.SwingV1DecisionInput;
import com.stock.agent.decision.swing.v1.SwingV1DecisionService;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.harness.HarnessRunContext;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.calendar.MarketTradingDayPolicy;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SwingV1AgentNextActionProvider
        implements AgentNextActionProvider {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Seoul");

    private final SwingV1DecisionService decisionService;
    private final MarketTradingDayPolicy marketTradingDayPolicy;
    private final Clock clock;

    public SwingV1AgentNextActionProvider(
            SwingV1DecisionService decisionService,
            MarketTradingDayPolicy marketTradingDayPolicy,
            Clock clock
    ) {
        this.decisionService = Objects.requireNonNull(
                decisionService,
                "decisionService must not be null."
        );
        this.marketTradingDayPolicy = Objects.requireNonNull(
                marketTradingDayPolicy,
                "marketTradingDayPolicy must not be null."
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

        DailyPriceHistory history = historyResult.get()
                .output()
                .dailyPriceHistory();
        validateDailyPriceHistory(history);

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

        InvestmentDecision decision = delegateDecision(
                context,
                candidateSymbol,
                requiredPriceSymbols,
                history
        );
        return AgentNextAction.finalDecision(decision);
    }

    private void validateDailyPriceHistory(DailyPriceHistory history) {
        LocalDate evaluationDate = LocalDate.ofInstant(
                clock.instant(),
                MARKET_ZONE
        );
        if (!marketTradingDayPolicy.isTradingDay(evaluationDate)) {
            throw new IllegalStateException(
                    "SWING_V1 evaluation date must be a trading day. date="
                            + evaluationDate
            );
        }

        LocalDate expectedTradingDate = evaluationDate.minusDays(1);
        while (!marketTradingDayPolicy.isTradingDay(expectedTradingDate)) {
            expectedTradingDate = expectedTradingDate.minusDays(1);
        }
        LocalDate actualTradingDate = history.bars().isEmpty()
                ? null
                : history.bars().getLast().tradingDate();
        if (!expectedTradingDate.equals(actualTradingDate)) {
            throw new IllegalStateException(
                    "SWING_V1 daily price history must end on the previous "
                            + "trading day. symbol=" + history.symbol()
                            + ", expectedTradingDate=" + expectedTradingDate
                            + ", actualTradingDate=" + actualTradingDate
            );
        }
    }

    private InvestmentDecision delegateDecision(
            HarnessRunContext context,
            String candidateSymbol,
            List<String> requiredPriceSymbols,
            DailyPriceHistory history
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
        return decisionService.decide(new SwingV1DecisionInput(
                context.strategyIdentity(),
                history,
                context.portfolioSnapshot(),
                candidatePrice,
                candidatePriceResult.output().currentPriceSource(),
                currentPrices,
                clock.instant()
        ));
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
