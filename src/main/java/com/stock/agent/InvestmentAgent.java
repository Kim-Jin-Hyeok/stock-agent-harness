package com.stock.agent;

import com.stock.harness.HarnessRunContext;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class InvestmentAgent {

    public InvestmentDecision decide(HarnessRunContext context) {
        String candidateSymbol = firstCandidateSymbol(context);
        HarnessToolExecutionResult dailyPriceHistoryResult =
                findDailyPriceHistoryResult(context, candidateSymbol)
                        .orElseThrow(() -> new IllegalStateException(
                                "Daily price history tool result is required. symbol="
                                        + candidateSymbol
                        ));
        DailyPriceHistory dailyPriceHistory =
                dailyPriceHistoryResult.output().dailyPriceHistory();

        if (dailyPriceHistory.bars().isEmpty()) {
            return new InvestmentDecision(
                    InvestmentAction.HOLD,
                    null,
                    null,
                    null,
                    "Daily price history is empty. symbol="
                            + candidateSymbol
            );
        }

        HarnessToolExecutionResult currentPriceResult =
                findCurrentPriceResult(context, candidateSymbol)
                        .orElseThrow(() -> new IllegalStateException(
                                "Current price tool result is required. symbol="
                                        + candidateSymbol
                        ));
        CurrentPriceSnapshot currentPrice =
                currentPriceResult.output().currentPriceSnapshot();
        DailyPriceBar latestBar = dailyPriceHistory.bars().getLast();

        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Market data received. symbol="
                        + currentPrice.symbol()
                        + ", dailyBars="
                        + dailyPriceHistory.bars().size()
                        + ", latestTradingDate="
                        + latestBar.tradingDate()
                        + ", latestClosePriceKrw="
                        + latestBar.closePriceKrw()
                        + ", currentPriceKrw="
                        + currentPrice.priceKrw()
                        + ", source="
                        + currentPriceResult.output().currentPriceSource()
        );
    }

    public AgentNextAction next(HarnessRunContext context) {
        String candidateSymbol = firstCandidateSymbol(context);
        Optional<HarnessToolExecutionResult> dailyPriceHistoryResult =
                findDailyPriceHistoryResult(context, candidateSymbol);
        if (dailyPriceHistoryResult.isEmpty()) {
            return AgentNextAction.requestTool(
                    HarnessToolRequest.dailyPriceHistory(candidateSymbol)
            );
        }

        DailyPriceHistory dailyPriceHistory = dailyPriceHistoryResult.get()
                .output()
                .dailyPriceHistory();
        if (dailyPriceHistory.bars().isEmpty()) {
            return AgentNextAction.finalDecision(decide(context));
        }

        if (findCurrentPriceResult(context, candidateSymbol).isEmpty()) {
            return AgentNextAction.requestTool(
                    HarnessToolRequest.currentPrice(candidateSymbol)
            );
        }

        return AgentNextAction.finalDecision(decide(context));
    }

    private String firstCandidateSymbol(HarnessRunContext context) {
        Objects.requireNonNull(context, "context must not be null.");
        if (context.candidateSymbols().isEmpty()) {
            throw new IllegalStateException(
                    "Investment agent requires at least one candidate symbol."
            );
        }
        return context.candidateSymbols().getFirst();
    }

    private Optional<HarnessToolExecutionResult> findDailyPriceHistoryResult(
            HarnessRunContext context,
            String candidateSymbol
    ) {
        return context.toolResults().stream()
                .filter(result ->
                        result.status() == HarnessToolExecutionStatus.EXECUTED
                )
                .filter(result ->
                        result.type()
                                == HarnessToolType.GET_DAILY_PRICE_HISTORY
                )
                .filter(result -> result.request() != null)
                .filter(result -> Objects.equals(
                        result.request().symbol(),
                        candidateSymbol
                ))
                .filter(result -> result.output() != null)
                .filter(result -> result.output().dailyPriceHistory() != null)
                .filter(result -> Objects.equals(
                        result.output().dailyPriceHistory().symbol(),
                        candidateSymbol
                ))
                .findFirst();
    }

    private Optional<HarnessToolExecutionResult> findCurrentPriceResult(
            HarnessRunContext context,
            String candidateSymbol
    ) {
        return context.toolResults().stream()
                .filter(result ->
                        result.status() == HarnessToolExecutionStatus.EXECUTED
                )
                .filter(result -> result.type() == HarnessToolType.GET_CURRENT_PRICE)
                .filter(result -> result.request() != null)
                .filter(result -> Objects.equals(
                        result.request().symbol(),
                        candidateSymbol
                ))
                .filter(result -> result.output() != null)
                .filter(result -> result.output().currentPriceSnapshot() != null)
                .filter(result -> Objects.equals(
                        result.output().currentPriceSnapshot().symbol(),
                        candidateSymbol
                ))
                .findFirst();
    }
}
