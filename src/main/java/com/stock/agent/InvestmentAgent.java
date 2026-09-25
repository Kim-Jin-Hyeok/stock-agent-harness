package com.stock.agent;

import com.stock.harness.HarnessRunContext;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisStatus;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class InvestmentAgent {
    private final MovingAverageAnalysisService movingAverageAnalysisService;

    public InvestmentAgent(
            MovingAverageAnalysisService movingAverageAnalysisService
    ) {
        this.movingAverageAnalysisService = Objects.requireNonNull(
                movingAverageAnalysisService,
                "movingAverageAnalysisService must not be null."
        );
    }

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
        MovingAverageAnalysisResult analysis =
                movingAverageAnalysisService.analyze(
                        context.strategyIdentity(),
                        dailyPriceHistory
                );
        if (analysis.status()
                == MovingAverageAnalysisStatus.INSUFFICIENT_DATA) {
            return insufficientDataDecision(analysis);
        }

        return analyzedDecision(context, candidateSymbol, analysis);
    }

    private InvestmentDecision analyzedDecision(
            HarnessRunContext context,
            String candidateSymbol,
            MovingAverageAnalysisResult analysis
    ) {
        MovingAverageIndicator indicator = analysis.indicator();

        HarnessToolExecutionResult currentPriceResult =
                findCurrentPriceResult(context, candidateSymbol)
                        .orElseThrow(() -> new IllegalStateException(
                                "Current price tool result is required. symbol="
                                        + candidateSymbol
                        ));
        CurrentPriceSnapshot currentPrice =
                currentPriceResult.output().currentPriceSnapshot();

        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Moving average analyzed. symbol="
                        + currentPrice.symbol()
                        + ", trend="
                        + analysis.trend()
                        + ", shortPeriod="
                        + indicator.shortMovingAverage().period()
                        + ", shortAveragePriceKrw="
                        + indicator.shortMovingAverage().averagePriceKrw()
                        + ", longPeriod="
                        + indicator.longMovingAverage().period()
                        + ", longAveragePriceKrw="
                        + indicator.longMovingAverage().averagePriceKrw()
                        + ", asOfTradingDate="
                        + indicator.asOfTradingDate()
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
        MovingAverageAnalysisResult analysis =
                movingAverageAnalysisService.analyze(
                        context.strategyIdentity(),
                        dailyPriceHistory
                );
        if (analysis.status()
                == MovingAverageAnalysisStatus.INSUFFICIENT_DATA) {
            return AgentNextAction.finalDecision(
                    insufficientDataDecision(analysis)
            );
        }

        if (findCurrentPriceResult(context, candidateSymbol).isEmpty()) {
            return AgentNextAction.requestTool(
                    HarnessToolRequest.currentPrice(candidateSymbol)
            );
        }

        return AgentNextAction.finalDecision(analyzedDecision(
                context,
                candidateSymbol,
                analysis
        ));
    }

    private InvestmentDecision insufficientDataDecision(
            MovingAverageAnalysisResult analysis
    ) {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Moving average data is insufficient. symbol="
                        + analysis.symbol()
                        + ", requiredBars="
                        + analysis.requiredBarCount()
                        + ", availableBars="
                        + analysis.availableBarCount()
        );
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
